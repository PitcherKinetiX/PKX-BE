package com.pkx.domain.comparison.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for comparing two analyses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to compare two analyses")
public class ComparisonRequest {

    @NotNull(message = "Baseline analysis ID is required")
    @Schema(description = "Baseline analysis ID (older/reference)", example = "100", required = true)
    private Long baselineAnalysisId;

    @NotNull(message = "Current analysis ID is required")
    @Schema(description = "Current analysis ID (newer/to compare)", example = "105", required = true)
    private Long currentAnalysisId;
}
