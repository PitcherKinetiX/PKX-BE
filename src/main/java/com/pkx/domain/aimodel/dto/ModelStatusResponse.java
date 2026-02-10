package com.pkx.domain.aimodel.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response DTO for AI model status.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "AI model status response")
public class ModelStatusResponse {

    @Schema(description = "Whether user has a custom trained model", example = "true")
    private Boolean hasCustomModel;

    @Schema(description = "Current model type being used", example = "USER_SPECIFIC")
    private String currentModelType;

    @Schema(description = "Model training status", example = "READY")
    private String trainingStatus; // NOT_STARTED, TRAINING, READY, FAILED

    @Schema(description = "Model accuracy if trained", example = "95.2")
    private BigDecimal modelAccuracy;

    @Schema(description = "Number of training samples used", example = "15")
    private Integer trainingSampleCount;

    @Schema(description = "Date when model was last trained")
    private LocalDateTime lastTrainedAt;

    @Schema(description = "Next training availability date")
    private LocalDateTime nextTrainingAvailable;

    @Schema(description = "Can train new model", example = "true")
    private Boolean canTrain;

    @Schema(description = "Reason if cannot train", example = "Minimum 10 analyses required")
    private String cannotTrainReason;

    @Schema(description = "Training progress percentage (0-100) if currently training", example = "45")
    private Integer trainingProgress;
}
