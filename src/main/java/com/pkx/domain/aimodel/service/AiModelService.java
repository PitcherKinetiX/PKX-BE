package com.pkx.domain.aimodel.service;

import com.pkx.common.exception.BusinessException;
import com.pkx.common.exception.ErrorCode;
import com.pkx.domain.aimodel.dto.ModelStatusResponse;
import com.pkx.domain.aimodel.dto.TrainRequest;
import com.pkx.domain.aimodel.dto.TrainResponse;
import com.pkx.domain.analysis.entity.Analysis;
import com.pkx.domain.analysis.repository.AnalysisRepository;
import com.pkx.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing user-specific AI models.
 * Phase 1: Mock implementation with simulated training.
 * Phase 2: Will integrate with actual AI model training API.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiModelService {

    private final AnalysisRepository analysisRepository;

    // In-memory storage for mock training status (Phase 1)
    // In Phase 2, this will be replaced with database storage
    private final ConcurrentHashMap<Long, ModelTrainingStatus> trainingStatusMap = new ConcurrentHashMap<>();

    private static final int MINIMUM_TRAINING_SAMPLES = 10;
    private static final int TRAINING_COOLDOWN_DAYS = 7;

    /**
     * Get AI model status for user.
     */
    @Transactional(readOnly = true)
    public ModelStatusResponse getModelStatus(User user) {
        log.info("Getting model status for user: {}", user.getUserId());

        ModelTrainingStatus status = trainingStatusMap.get(user.getUserId());

        // Count completed analyses
        long completedAnalysesCount = analysisRepository.findByUser_UserId(user.getUserId(),
                org.springframework.data.domain.Pageable.unpaged())
                .getTotalElements();

        boolean canTrain = completedAnalysesCount >= MINIMUM_TRAINING_SAMPLES;
        String cannotTrainReason = null;

        if (!canTrain) {
            cannotTrainReason = String.format("Minimum %d completed analyses required (currently have %d)",
                    MINIMUM_TRAINING_SAMPLES, completedAnalysesCount);
        } else if (status != null && status.getTrainingStatus().equals("TRAINING")) {
            canTrain = false;
            cannotTrainReason = "Model training is already in progress";
        } else if (status != null && status.getLastTrainedAt() != null) {
            LocalDateTime nextAvailable = status.getLastTrainedAt().plusDays(TRAINING_COOLDOWN_DAYS);
            if (LocalDateTime.now().isBefore(nextAvailable)) {
                canTrain = false;
                cannotTrainReason = String.format("Next training available on %s", nextAvailable.toLocalDate());
            }
        }

        if (status == null) {
            return ModelStatusResponse.builder()
                    .hasCustomModel(false)
                    .currentModelType("GENERAL")
                    .trainingStatus("NOT_STARTED")
                    .canTrain(canTrain)
                    .cannotTrainReason(cannotTrainReason)
                    .trainingSampleCount(0)
                    .build();
        }

        return ModelStatusResponse.builder()
                .hasCustomModel(status.getTrainingStatus().equals("READY"))
                .currentModelType(status.getTrainingStatus().equals("READY") ? "USER_SPECIFIC" : "GENERAL")
                .trainingStatus(status.getTrainingStatus())
                .modelAccuracy(status.getModelAccuracy())
                .trainingSampleCount(status.getTrainingSampleCount())
                .lastTrainedAt(status.getLastTrainedAt())
                .nextTrainingAvailable(status.getLastTrainedAt() != null ?
                        status.getLastTrainedAt().plusDays(TRAINING_COOLDOWN_DAYS) : null)
                .canTrain(canTrain)
                .cannotTrainReason(cannotTrainReason)
                .trainingProgress(status.getTrainingProgress())
                .build();
    }

    /**
     * Train user-specific AI model.
     */
    @Transactional
    public TrainResponse trainModel(TrainRequest request, User user) {
        log.info("Training model for user: {} with {} analyses",
                user.getUserId(), request.getAnalysisIds().size());

        // Validate minimum samples
        if (request.getAnalysisIds().size() < MINIMUM_TRAINING_SAMPLES) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    String.format("Minimum %d analyses required for training", MINIMUM_TRAINING_SAMPLES));
        }

        // Validate all analyses belong to user and are completed
        List<Analysis> analyses = analysisRepository.findAllById(request.getAnalysisIds());

        if (analyses.size() != request.getAnalysisIds().size()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                    "Some analyses not found");
        }

        for (Analysis analysis : analyses) {
            if (!analysis.getUser().getUserId().equals(user.getUserId())) {
                throw new BusinessException(ErrorCode.INSUFFICIENT_PERMISSIONS,
                        "Access denied to analysis: " + analysis.getAnalysisId());
            }

            if (analysis.getStatus() != Analysis.AnalysisStatus.COMPLETED) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                        "Analysis " + analysis.getAnalysisId() + " is not completed");
            }
        }

        // Check if training is already in progress
        ModelTrainingStatus currentStatus = trainingStatusMap.get(user.getUserId());
        if (currentStatus != null && currentStatus.getTrainingStatus().equals("TRAINING")) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "Model training is already in progress");
        }

        // Generate training job ID
        String jobId = "train_" + UUID.randomUUID().toString().substring(0, 8);

        // Create training status
        ModelTrainingStatus status = ModelTrainingStatus.builder()
                .userId(user.getUserId())
                .jobId(jobId)
                .trainingStatus("TRAINING")
                .trainingSampleCount(analyses.size())
                .trainingProgress(0)
                .startedAt(LocalDateTime.now())
                .build();

        trainingStatusMap.put(user.getUserId(), status);

        // Start async training (mock in Phase 1)
        trainModelAsync(user.getUserId(), jobId, analyses.size());

        LocalDateTime estimatedCompletion = LocalDateTime.now().plusMinutes(5);

        return TrainResponse.builder()
                .trainingJobId(jobId)
                .status("TRAINING")
                .sampleCount(analyses.size())
                .estimatedCompletionTime(estimatedCompletion)
                .message("Model training started successfully. This will take approximately 5 minutes.")
                .build();
    }

    /**
     * Mock async model training.
     * In Phase 2, this will call the actual AI training API.
     */
    @Async
    protected void trainModelAsync(Long userId, String jobId, int sampleCount) {
        log.info("Starting async model training for user: {}, jobId: {}", userId, jobId);

        try {
            ModelTrainingStatus status = trainingStatusMap.get(userId);
            if (status == null || !status.getJobId().equals(jobId)) {
                log.warn("Training status not found or job ID mismatch");
                return;
            }

            // Simulate training progress
            for (int progress = 10; progress <= 100; progress += 10) {
                Thread.sleep(30000); // 30 seconds per 10% progress = 5 minutes total

                status.setTrainingProgress(progress);
                trainingStatusMap.put(userId, status);

                log.info("Training progress for user {}: {}%", userId, progress);
            }

            // Mark as completed
            status.setTrainingStatus("READY");
            status.setTrainingProgress(100);
            status.setLastTrainedAt(LocalDateTime.now());
            status.setModelAccuracy(BigDecimal.valueOf(93.5 + Math.random() * 4)); // 93.5-97.5%
            trainingStatusMap.put(userId, status);

            log.info("Model training completed successfully for user: {}", userId);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Training interrupted for user: {}", userId, e);

            ModelTrainingStatus status = trainingStatusMap.get(userId);
            if (status != null) {
                status.setTrainingStatus("FAILED");
                trainingStatusMap.put(userId, status);
            }
        } catch (Exception e) {
            log.error("Training failed for user: {}", userId, e);

            ModelTrainingStatus status = trainingStatusMap.get(userId);
            if (status != null) {
                status.setTrainingStatus("FAILED");
                trainingStatusMap.put(userId, status);
            }
        }
    }

    /**
     * Internal class to track training status.
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class ModelTrainingStatus {
        private Long userId;
        private String jobId;
        private String trainingStatus;
        private Integer trainingSampleCount;
        private Integer trainingProgress;
        private BigDecimal modelAccuracy;
        private LocalDateTime startedAt;
        private LocalDateTime lastTrainedAt;
    }
}
