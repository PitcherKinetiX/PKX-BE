package com.pkx.domain.aimodel.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for model training initiation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Model training response")
public class TrainResponse {

    @Schema(description = "Training job ID", example = "train_123456")
    private String trainingJobId;

    @Schema(description = "Training status", example = "TRAINING")
    private String status;

    @Schema(description = "Number of samples used for training", example = "15")
    private Integer sampleCount;

    @Schema(description = "Estimated completion time")
    private LocalDateTime estimatedCompletionTime;

    @Schema(description = "Message", example = "Model training started successfully")
    private String message;
}
