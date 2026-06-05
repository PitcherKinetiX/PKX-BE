package com.pkx.domain.aimodel.service;

import com.pkx.common.exception.BusinessException;
import com.pkx.common.exception.ErrorCode;
import com.pkx.domain.aimodel.client.AiTrainFeignClient;
import com.pkx.domain.aimodel.dto.AiTrainStatusResponse;
import com.pkx.domain.aimodel.entity.UserAiModel;
import com.pkx.domain.aimodel.repository.UserAiModelRepository;
import com.pkx.domain.analysis.service.VideoUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * AI 학습 잡의 비동기 폴링 + 완료 처리 담당.
 * (AiModelService 자기호출 시 @Async 프록시가 안 먹으므로 별도 빈으로 분리)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiModelTrainingRunner {

    private final UserAiModelRepository userAiModelRepository;
    private final VideoUploadService videoUploadService;
    private final AiTrainFeignClient aiTrainFeignClient;

    private static final long POLL_INTERVAL_MS = 5_000L;
    private static final long MAX_WAIT_MS = 30 * 60_000L;        // 학습은 길어질 수 있어 30분
    private static final int MAX_CONSECUTIVE_POLL_ERRORS = 5;

    /**
     * 전체 재학습: DONE 시 READY로 전환하고 학습 영상 원본을 GCS에서 삭제.
     */
    @Async
    public void runTraining(Long userId, String jobId, List<String> trainingVideoPaths,
                            String modelPath, String statsPath, int sampleCount) {
        try {
            AiTrainStatusResponse done = pollUntilDone(userId, jobId);
            finalizeReady(userId, modelPath, statsPath, done.getAccuracy(), sampleCount, false);
            log.info("Training finalized for userId: {} (jobId={})", userId, jobId);
        } catch (Exception e) {
            log.error("Training failed for userId: {} (jobId={}): {}", userId, jobId, e.getMessage());
            finalizeFailed(userId, e.getMessage());
        } finally {
            // 학습 전용 영상 원본은 보관하지 않음 (성공/실패 무관 정리)
            for (String path : trainingVideoPaths) {
                videoUploadService.deleteFile(path);
            }
        }
    }

    /**
     * 증분 업데이트: DONE 시 모델 경로/정확도 갱신, 학습 데이터 수 +1. (분석 영상은 삭제하지 않음)
     */
    @Async
    public void runIncrementalUpdate(Long userId, String jobId, String modelPath, String statsPath) {
        try {
            AiTrainStatusResponse done = pollUntilDone(userId, jobId);
            finalizeReady(userId, modelPath, statsPath, done.getAccuracy(), null, true);
            log.info("Incremental update finalized for userId: {} (jobId={})", userId, jobId);
        } catch (Exception e) {
            log.error("Incremental update failed for userId: {} (jobId={}): {}", userId, jobId, e.getMessage());
            finalizeFailed(userId, e.getMessage());
        }
    }

    private AiTrainStatusResponse pollUntilDone(Long userId, String jobId) {
        long deadline = System.currentTimeMillis() + MAX_WAIT_MS;
        int consecutiveErrors = 0;

        while (System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "AI 학습 대기 중 인터럽트됨");
            }

            AiTrainStatusResponse status;
            try {
                status = aiTrainFeignClient.getStatus(jobId);
                consecutiveErrors = 0;
            } catch (Exception e) {
                consecutiveErrors++;
                log.warn("AI train status poll failed (userId={}, jobId={}) ({}/{}): {}",
                        userId, jobId, consecutiveErrors, MAX_CONSECUTIVE_POLL_ERRORS, e.getMessage());
                if (consecutiveErrors >= MAX_CONSECUTIVE_POLL_ERRORS) {
                    throw new BusinessException(ErrorCode.AI_SERVICE_ERROR,
                            "AI 학습 상태 조회 연속 실패 (jobId=" + jobId + ")");
                }
                continue;
            }

            switch (status.getStatus()) {
                case "DONE" -> {
                    return status;
                }
                case "FAILED" -> throw new BusinessException(ErrorCode.AI_SERVICE_ERROR,
                        "AI 학습 실패: " + status.getError());
                default -> {
                    if (status.getProgress() != null) {
                        updateProgress(userId, status.getProgress());
                    }
                }
            }
        }

        throw new BusinessException(ErrorCode.AI_SERVICE_ERROR,
                "AI 학습 시간 초과 (jobId=" + jobId + ", " + (MAX_WAIT_MS / 1000) + "s)");
    }

    private void updateProgress(Long userId, int progress) {
        userAiModelRepository.findByUser_UserId(userId).ifPresent(model -> {
            model.setTrainingProgress(progress);
            userAiModelRepository.save(model);
        });
    }

    private void finalizeReady(Long userId, String modelPath, String statsPath,
                               Double accuracy, Integer sampleCount, boolean incremental) {
        UserAiModel model = userAiModelRepository.findByUser_UserId(userId).orElse(null);
        if (model == null) {
            log.warn("UserAiModel not found on finalize (userId={})", userId);
            return;
        }

        BigDecimal acc = accuracy != null ? BigDecimal.valueOf(accuracy) : model.getModelAccuracy();
        model.markAsReady(acc);
        model.setModelStoragePath(modelPath);
        model.setStatsStoragePath(statsPath);
        model.setTrainingJobId(null);

        if (incremental) {
            int current = model.getTrainingDataCount() == null ? 0 : model.getTrainingDataCount();
            model.setTrainingDataCount(current + 1);
        } else if (sampleCount != null) {
            model.setTrainingDataCount(sampleCount);
        }

        userAiModelRepository.save(model);
    }

    private void finalizeFailed(Long userId, String error) {
        userAiModelRepository.findByUser_UserId(userId).ifPresent(model -> {
            model.markAsFailed(error);
            model.setTrainingJobId(null);
            userAiModelRepository.save(model);
        });
    }
}
