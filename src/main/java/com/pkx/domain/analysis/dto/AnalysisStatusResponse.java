package com.pkx.domain.analysis.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for analysis status check.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Analysis status response")
public class AnalysisStatusResponse {

    @Schema(description = "Analysis ID", example = "123")
    private Long analysisId;

    @Schema(description = "Current status", example = "PROCESSING",
            allowableValues = {"UPLOADING", "PROCESSING", "COMPLETED", "FAILED"})
    private String status;

    @Schema(description = "Status message", example = "Analysis in progress")
    private String message;

    @Schema(description = "Progress percentage (0-100)", example = "45")
    private Integer progress;

    @Schema(description = "Created timestamp")
    private LocalDateTime createdAt;

    @Schema(description = "Started timestamp")
    private LocalDateTime startedAt;

    @Schema(description = "Completed timestamp")
    private LocalDateTime completedAt;

    @Schema(description = "Error message if status is FAILED")
    private String errorMessage;
}
