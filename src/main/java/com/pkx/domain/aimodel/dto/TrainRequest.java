package com.pkx.domain.aimodel.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO for training user-specific AI model.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to train user-specific AI model")
public class TrainRequest {

    @NotEmpty(message = "Analysis IDs are required for training")
    @Schema(description = "List of analysis IDs to use for training", example = "[1, 2, 3, 5, 8]", required = true)
    private List<Long> analysisIds;

    @Schema(description = "Optional training notes", example = "Training with recent pitching sessions")
    private String notes;
}
