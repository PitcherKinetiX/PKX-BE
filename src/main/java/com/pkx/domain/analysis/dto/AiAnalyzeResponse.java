package com.pkx.domain.analysis.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiAnalyzeResponse {

    private Scores scores;
    private List<FeatureDetail> features;
    private List<VelocityDetail> velocityAnalysis;
    private CriticalAreas criticalAreas;
    private GeneralModel generalModel;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Scores {
        private Double userConsistencyScore;
        private Double generalSimilarityScore;
        private Double medicalSafetyScore;
        private Double finalScore;
        private String grade;
        private Double timingScore;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeatureDetail {
        private Integer index;
        private String name;
        private String type;        // "angle" | "velocity"
        private Double userError;
        private Double generalError;
        private String level;       // "정상" | "양호" | "주의" | "위험"
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VelocityDetail {
        private Integer index;
        private String name;
        private Double peakValue;
        private Double dangerRatio;
        private Integer medicalScore;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CriticalAreas {
        private Integer userCriticalWindow;
        private String userCriticalFeature;
        private List<String> userCriticalTop3;
        private String medCriticalFeature;
        private Integer medCriticalWindow;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeneralModel {
        private String worstFeature;
        private Double latentShiftNorm;
    }
}
