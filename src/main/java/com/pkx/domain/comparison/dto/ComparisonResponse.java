package com.pkx.domain.comparison.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Response DTO for analysis comparison results.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Analysis comparison results")
public class ComparisonResponse {

    @Schema(description = "Baseline analysis summary")
    private AnalysisSummary baseline;

    @Schema(description = "Current analysis summary")
    private AnalysisSummary current;

    @Schema(description = "Overall improvement summary")
    private ImprovementSummary improvementSummary;

    @Schema(description = "Core metrics comparison")
    private MetricsComparison coreMetrics;

    @Schema(description = "Joint metrics comparison")
    private Map<String, JointMetricComparison> jointMetrics;

    @Schema(description = "Key insights from comparison")
    private List<String> insights;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Analysis summary information")
    public static class AnalysisSummary {

        @Schema(description = "Analysis ID", example = "100")
        private Long analysisId;

        @Schema(description = "Video filename", example = "pitching_1.mp4")
        private String filename;

        @Schema(description = "Analysis date")
        private LocalDateTime analysisDate;

        @Schema(description = "Risk grade", example = "NORMAL")
        private String riskGrade;

        @Schema(description = "Overall risk score", example = "75")
        private Integer overallRiskScore;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Overall improvement summary")
    public static class ImprovementSummary {

        @Schema(description = "Overall improvement status", example = "IMPROVED")
        private String status; // IMPROVED, DECLINED, NO_CHANGE

        @Schema(description = "Improvement percentage", example = "12.5")
        private Double improvementPercentage;

        @Schema(description = "Summary message")
        private String message;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Core metrics comparison")
    public static class MetricsComparison {

        @Schema(description = "Overall risk score change")
        private MetricChange overallRiskScore;

        @Schema(description = "Consistency score change")
        private MetricChange consistencyScore;

        @Schema(description = "Medical risk score change")
        private MetricChange medicalRiskScore;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Individual metric change details")
    public static class MetricChange {

        @Schema(description = "Baseline value", example = "70")
        private Integer baselineValue;

        @Schema(description = "Current value", example = "82")
        private Integer currentValue;

        @Schema(description = "Absolute change", example = "12")
        private Integer change;

        @Schema(description = "Percentage change", example = "17.1")
        private Double changePercentage;

        @Schema(description = "Change direction", example = "IMPROVED")
        private String changeDirection; // IMPROVED, DECLINED, NO_CHANGE
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Joint metric comparison")
    public static class JointMetricComparison {

        @Schema(description = "Joint name", example = "Shoulder Stress")
        private String jointName;

        @Schema(description = "Baseline value", example = "45")
        private Integer baselineValue;

        @Schema(description = "Current value", example = "38")
        private Integer currentValue;

        @Schema(description = "Change (negative is improvement for stress)", example = "-7")
        private Integer change;

        @Schema(description = "Percentage change", example = "-15.6")
        private Double changePercentage;

        @Schema(description = "Improved or declined", example = "IMPROVED")
        private String status;
    }
}
