package com.pkx.domain.analysis.dto;

import com.pkx.domain.analysis.entity.AnalysisResult;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Detailed response DTO for completed analysis with full results.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Detailed analysis response with full results")
public class AnalysisDetailResponse {

    @Schema(description = "Analysis ID", example = "123")
    private Long analysisId;

    @Schema(description = "Video filename", example = "pitching_video.mp4")
    private String videoFilename;

    @Schema(description = "Video size in bytes", example = "52428800")
    private Long videoSizeBytes;

    @Schema(description = "Video duration in seconds", example = "15.5")
    private BigDecimal videoDurationSeconds;

    @Schema(description = "Analysis status", example = "COMPLETED")
    private String status;

    @Schema(description = "Created timestamp")
    private LocalDateTime createdAt;

    @Schema(description = "Completed timestamp")
    private LocalDateTime completedAt;

    @Schema(description = "Analysis result (null if not completed)")
    private ResultDetail result;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Analysis result details")
    public static class ResultDetail {

        @Schema(description = "Overall risk score (0-100, higher is better)", example = "75")
        private Integer overallRiskScore;

        @Schema(description = "Consistency score (0-100)", example = "82")
        private Integer consistencyScore;

        @Schema(description = "Medical risk score (0-100, higher is better)", example = "70")
        private Integer medicalRiskScore;

        @Schema(description = "Risk grade", example = "NORMAL",
                allowableValues = {"GOOD", "NORMAL", "CAUTION", "DANGER"})
        private String riskGrade;

        @Schema(description = "Model type used", example = "GENERAL")
        private String modelType;

        @Schema(description = "Model accuracy percentage", example = "94.5")
        private BigDecimal modelAccuracy;

        @Schema(description = "Risk summary text")
        private String riskSummary;

        @Schema(description = "List of recommendations")
        private List<String> recommendations;

        @Schema(description = "Whether critical zone was detected", example = "false")
        private Boolean criticalZoneDetected;

        @Schema(description = "Critical zone description if detected")
        private String criticalZoneDescription;

        @Schema(description = "Joint metrics")
        private JointMetricsDetail jointMetrics;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Joint metrics details")
    public static class JointMetricsDetail {

        @Schema(description = "Shoulder stress score (0-100, lower is better)", example = "45")
        private Integer shoulderStress;

        @Schema(description = "Elbow load score (0-100, lower is better)", example = "42")
        private Integer elbowLoad;

        @Schema(description = "Wrist load score (0-100, lower is better)", example = "48")
        private Integer wristLoad;

        @Schema(description = "Spine angle score (0-100, lower is better)", example = "40")
        private Integer spineAngle;

        @Schema(description = "Knee stability score (0-100, lower is better)", example = "38")
        private Integer kneeStability;

        @Schema(description = "Hip rotation score (0-100, lower is better)", example = "45")
        private Integer hipRotation;

        @Schema(description = "Radar chart data (JSON string)")
        private String radarData;

        @Schema(description = "Temporal error data (JSON string)")
        private String temporalErrorData;
    }
}
