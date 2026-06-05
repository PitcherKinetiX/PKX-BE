package com.pkx.domain.aimodel.service;

import com.pkx.common.exception.BusinessException;
import com.pkx.common.exception.ErrorCode;
import com.pkx.domain.aimodel.client.AiTrainFeignClient;
import com.pkx.domain.aimodel.dto.AiTrainRequest;
import com.pkx.domain.aimodel.dto.ModelStatusResponse;
import com.pkx.domain.aimodel.dto.TrainResponse;
import com.pkx.domain.aimodel.entity.UserAiModel;
import com.pkx.domain.aimodel.repository.UserAiModelRepository;
import com.pkx.domain.analysis.service.VideoUploadService;
import com.pkx.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 사용자별 개인화 AI 모델 학습/상태 관리 서비스.
 *
 * 모델 파일(pth/pkl)은 GCS에 저장하고 DB(UserAiModel)에는 경로만 보관한다.
 * 실제 학습은 AI 서버(/api/train)가 GPU에서 수행하며, 백엔드는 Signed URL로
 * 입력(영상·기존 모델)을 전달하고 출력(새 모델)을 업로드받는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiModelService {

    private final UserAiModelRepository userAiModelRepository;
    private final VideoUploadService videoUploadService;
    private final AiTrainFeignClient aiTrainFeignClient;
    private final AiModelTrainingRunner trainingRunner;

    private static final int MINIMUM_TRAINING_SAMPLES = 10;
    private static final String MODEL_CONTENT_TYPE = "application/octet-stream";
    private static final long SIGNED_URL_TRAINING_MINUTES = 60;  // 학습은 GPU 큐 대기+처리로 길 수 있음

    /**
     * AI 모델 상태 조회.
     */
    @Transactional(readOnly = true)
    public ModelStatusResponse getModelStatus(User user) {
        UserAiModel model = userAiModelRepository.findByUser(user).orElse(null);

        if (model == null) {
            return ModelStatusResponse.builder()
                    .hasCustomModel(false)
                    .currentModelType("GENERAL")
                    .trainingStatus(UserAiModel.ModelStatus.NOT_TRAINED.name())
                    .trainingSampleCount(0)
                    .trainingProgress(0)
                    .canTrain(true)
                    .build();
        }

        boolean isReady = model.getStatus() == UserAiModel.ModelStatus.READY;
        boolean isTraining = model.getStatus() == UserAiModel.ModelStatus.TRAINING;

        return ModelStatusResponse.builder()
                .hasCustomModel(isReady)
                .currentModelType(isReady ? "USER_SPECIFIC" : "GENERAL")
                .trainingStatus(model.getStatus().name())
                .modelAccuracy(model.getModelAccuracy())
                .trainingSampleCount(model.getTrainingDataCount())
                .lastTrainedAt(model.getLastTrainedAt())
                .canTrain(!isTraining)
                .cannotTrainReason(isTraining ? "모델 학습/업데이트가 진행 중입니다." : null)
                .trainingProgress(model.getTrainingProgress())
                .build();
    }

    /**
     * 사용자가 업로드한 영상 10개+ 로 개인화 모델을 학습(전체 재학습).
     * 일반 모델을 베이스로 fine-tune 한다.
     */
    @Transactional
    public TrainResponse trainModel(List<MultipartFile> files, User user) {
        if (files == null || files.size() < MINIMUM_TRAINING_SAMPLES) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    String.format("학습에는 최소 %d개의 영상이 필요합니다.", MINIMUM_TRAINING_SAMPLES));
        }

        UserAiModel model = userAiModelRepository.findByUser(user)
                .orElseGet(() -> UserAiModel.builder().user(user).build());

        if (model.getStatus() == UserAiModel.ModelStatus.TRAINING) {
            throw new BusinessException(ErrorCode.MODEL_TRAINING_IN_PROGRESS);
        }

        // 1) 학습 영상 GCS 업로드 (training/ 접두사) → Signed GET URL
        List<String> videoPaths = new ArrayList<>();
        for (MultipartFile file : files) {
            videoPaths.add(videoUploadService.uploadVideo(file, user.getUserId(), "training").getStoragePath());
        }
        List<String> videoUrls = videoPaths.stream()
                .map(p -> videoUploadService.generateSignedUrl(p, SIGNED_URL_TRAINING_MINUTES))
                .collect(Collectors.toList());

        // 2) 산출물 업로드(PUT)용 Signed URL
        String modelPath = modelStoragePath(user.getUserId());
        String statsPath = statsStoragePath(user.getUserId());
        String modelUploadUrl = videoUploadService.generateUploadSignedUrl(modelPath, MODEL_CONTENT_TYPE, SIGNED_URL_TRAINING_MINUTES);
        String statsUploadUrl = videoUploadService.generateUploadSignedUrl(statsPath, MODEL_CONTENT_TYPE, SIGNED_URL_TRAINING_MINUTES);

        // 3) AI 서버 학습 시작 (전체 재학습 → baseModelUrl=null)
        AiTrainRequest request = AiTrainRequest.builder()
                .userId(user.getUserId())
                .videoUrls(videoUrls)
                .baseModelUrl(null)
                .modelUploadUrl(modelUploadUrl)
                .statsUploadUrl(statsUploadUrl)
                .incremental(false)
                .build();

        String jobId;
        try {
            jobId = aiTrainFeignClient.startTrain(request).getJobId();
        } catch (Exception e) {
            log.error("AI train start failed for userId: {}", user.getUserId(), e);
            // 업로드한 학습 영상 정리
            videoPaths.forEach(videoUploadService::deleteFile);
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "AI 학습 시작 실패: " + e.getMessage());
        }

        model.markAsTraining();
        model.setTrainingJobId(jobId);
        model.setTrainingDataCount(files.size());
        userAiModelRepository.save(model);

        // 4) 비동기 폴링 + 완료 처리
        trainingRunner.runTraining(user.getUserId(), jobId, videoPaths, modelPath, statsPath, files.size());

        log.info("Model training started for userId: {}, jobId: {}, videos: {}",
                user.getUserId(), jobId, files.size());

        return TrainResponse.builder()
                .trainingJobId(jobId)
                .status("TRAINING")
                .sampleCount(files.size())
                .estimatedCompletionTime(LocalDateTime.now().plusMinutes(10))
                .message("개인화 모델 학습을 시작했습니다.")
                .build();
    }

    /**
     * 분석이 끝난 영상으로 개인화 모델을 백그라운드 증분 업데이트.
     * 모델이 READY일 때만 수행하고, 진행 중이면 조용히 스킵한다(다음 분석에서 반영).
     */
    @Transactional
    public void updateModelIncrementally(User user, String videoStoragePath) {
        UserAiModel model = userAiModelRepository.findByUser(user).orElse(null);
        if (model == null || model.getStatus() != UserAiModel.ModelStatus.READY
                || model.getModelStoragePath() == null || model.getStatsStoragePath() == null) {
            return;
        }

        String modelPath = model.getModelStoragePath();
        String statsPath = model.getStatsStoragePath();

        AiTrainRequest request = AiTrainRequest.builder()
                .userId(user.getUserId())
                .videoUrls(List.of(videoUploadService.generateSignedUrl(videoStoragePath, SIGNED_URL_TRAINING_MINUTES)))
                .baseModelUrl(videoUploadService.generateSignedUrl(modelPath, SIGNED_URL_TRAINING_MINUTES))
                .modelUploadUrl(videoUploadService.generateUploadSignedUrl(modelPath, MODEL_CONTENT_TYPE, SIGNED_URL_TRAINING_MINUTES))
                .statsUploadUrl(videoUploadService.generateUploadSignedUrl(statsPath, MODEL_CONTENT_TYPE, SIGNED_URL_TRAINING_MINUTES))
                .incremental(true)
                .build();

        String jobId;
        try {
            jobId = aiTrainFeignClient.startTrain(request).getJobId();
        } catch (Exception e) {
            // 분석은 이미 완료됨 — 증분 업데이트 실패는 조용히 무시
            log.warn("Incremental model update start failed for userId: {}: {}", user.getUserId(), e.getMessage());
            return;
        }

        model.markAsTraining();
        model.setTrainingJobId(jobId);
        userAiModelRepository.save(model);

        trainingRunner.runIncrementalUpdate(user.getUserId(), jobId, modelPath, statsPath);
        log.info("Incremental model update started for userId: {}, jobId: {}", user.getUserId(), jobId);
    }

    /**
     * 사용자가 READY 상태의 개인화 모델을 보유하고 있는지.
     */
    @Transactional(readOnly = true)
    public boolean hasReadyModel(User user) {
        return userAiModelRepository.findByUser(user)
                .map(m -> m.getStatus() == UserAiModel.ModelStatus.READY)
                .orElse(false);
    }

    private String modelStoragePath(Long userId) {
        return String.format("models/%d/user_specific_ae.pth", userId);
    }

    private String statsStoragePath(Long userId) {
        return String.format("models/%d/user_stats.pkl", userId);
    }
}
