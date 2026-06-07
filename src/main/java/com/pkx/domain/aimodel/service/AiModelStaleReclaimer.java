package com.pkx.domain.aimodel.service;

import com.pkx.domain.aimodel.entity.UserAiModel;
import com.pkx.domain.aimodel.repository.UserAiModelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 오래 TRAINING 상태에 멈춰 있는 개인화 모델을 FAILED로 회수하는 워커.
 *
 * 폴러(AiModelTrainingRunner)가 정상 동작하면 AI 실패를 곧바로 FAILED로 반영하지만,
 * BE 재시작 등으로 폴러 스레드가 유실되면 DB가 TRAINING 에 영구히 멈춘다(프론트는 계속 "진행중").
 * 이 워커가 그 무한 대기를 끊는 안전망이다. (분석의 reclaimStale 과 동일한 취지)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiModelStaleReclaimer {

    private final UserAiModelRepository userAiModelRepository;

    /** TRAINING 이 이 시간보다 오래되면 유실로 보고 FAILED 처리 (폴러 최대 대기 30분 + 여유) */
    private static final long STALE_THRESHOLD_MINUTES = 40;

    @Scheduled(fixedDelayString = "${aimodel.stale.poll-interval-ms:60000}")
    @Transactional
    public void reclaimStaleTraining() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(STALE_THRESHOLD_MINUTES);

        List<UserAiModel> training = userAiModelRepository.findByStatus(UserAiModel.ModelStatus.TRAINING);
        for (UserAiModel model : training) {
            LocalDateTime startedAt = model.getTrainingStartedAt();
            // 시작 시각이 없거나(레거시 행) 임계 시간 초과면 회수
            if (startedAt == null || startedAt.isBefore(threshold)) {
                log.warn("Reclaiming stale TRAINING model (modelId={}, userId={}, startedAt={})",
                        model.getModelId(), model.getUser().getUserId(), startedAt);
                model.markAsFailed("학습이 시간 내에 완료되지 않아 실패 처리되었습니다. 다시 시도해주세요.");
                model.setTrainingJobId(null);
                userAiModelRepository.save(model);
            }
        }
    }
}
