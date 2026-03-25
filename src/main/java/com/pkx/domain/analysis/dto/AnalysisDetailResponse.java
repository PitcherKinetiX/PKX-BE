package com.pkx.domain.analysis.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

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

        @Schema(description = "Overall risk score (0-100)", example = "75")
        private Integer overallRiskScore;

        @Schema(description = "Consistency score (0-100)", example = "82")
        private Integer consistencyScore;

        @Schema(description = "Medical risk score (0-100)", example = "70")
        private Integer medicalRiskScore;

        @Schema(description = "Risk grade", example = "NORMAL")
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

        @Schema(description = "13개 생체역학 특징 상세 데이터")
        private List<FeatureDetailDto> features;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Individual biomechanical feature detail")
    public static class FeatureDetailDto {

        @Schema(description = "Feature index (0-12)", example = "0")
        private Integer index;

        @Schema(description = "Feature name", example = "L_elbow_angle")
        private String name;

        @Schema(description = "Feature type: angle or velocity", example = "angle")
        private String type;

        @Schema(description = "User error rate", example = "0.084")
        private Double userError;

        @Schema(description = "General model error rate", example = "0.046")
        private Double generalError;

        @Schema(description = "Risk level", example = "양호")
        private String level;

        @Schema(description = "Peak velocity value (velocity type only)", example = "580.2")
        private Double peakValue;

        @Schema(description = "Danger ratio (velocity type only)", example = "0.83")
        private Double dangerRatio;

        @Schema(description = "Medical safety score (velocity type only)", example = "100")
        private Integer medicalScore;
    }
}
