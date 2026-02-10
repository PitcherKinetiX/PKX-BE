package com.pkx.domain.analysis.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Summary response DTO for analysis list items.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Analysis list item summary")
public class AnalysisListResponse {

    @Schema(description = "Analysis ID", example = "123")
    private Long analysisId;

    @Schema(description = "Video filename", example = "pitching_video.mp4")
    private String videoFilename;

    @Schema(description = "Video duration in seconds", example = "15.5")
    private BigDecimal videoDurationSeconds;

    @Schema(description = "Analysis status", example = "COMPLETED")
    private String status;

    @Schema(description = "Risk grade if completed", example = "NORMAL")
    private String riskGrade;

    @Schema(description = "Overall risk score if completed", example = "75")
    private Integer overallRiskScore;

    @Schema(description = "Created timestamp")
    private LocalDateTime createdAt;

    @Schema(description = "Completed timestamp")
    private LocalDateTime completedAt;
}
