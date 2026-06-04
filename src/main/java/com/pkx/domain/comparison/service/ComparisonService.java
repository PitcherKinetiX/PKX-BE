package com.pkx.domain.comparison.service;

import com.pkx.common.exception.BusinessException;
import com.pkx.common.exception.ErrorCode;
import com.pkx.domain.analysis.entity.Analysis;
import com.pkx.domain.analysis.entity.AnalysisResult;
import com.pkx.domain.analysis.entity.FeatureDetail;
import com.pkx.domain.analysis.repository.AnalysisRepository;
import com.pkx.domain.comparison.dto.ComparisonRequest;
import com.pkx.domain.comparison.dto.ComparisonResponse;
import com.pkx.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for comparing two analyses and calculating improvements.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComparisonService {

    private final AnalysisRepository analysisRepository;

    /**
     * Compare two analyses and return improvement metrics.
     */
    @Transactional(readOnly = true)
    public ComparisonResponse compareAnalyses(ComparisonRequest request, User user) {
        log.info("Comparing analyses: baseline={}, current={} for user={}",
                request.getBaselineAnalysisId(), request.getCurrentAnalysisId(), user.getUserId());

        // Validate analyses exist and belong to user
        Analysis baseline = getAnalysisForUser(request.getBaselineAnalysisId(), user);
        Analysis current = getAnalysisForUser(request.getCurrentAnalysisId(), user);

        // Validate both analyses are completed
        validateAnalysisCompleted(baseline, "Baseline");
        validateAnalysisCompleted(current, "Current");

        // Build comparison response
        ComparisonResponse response = ComparisonResponse.builder()
                .baseline(buildAnalysisSummary(baseline))
                .current(buildAnalysisSummary(current))
                .improvementSummary(calculateImprovementSummary(baseline.getResult(), current.getResult()))
                .coreMetrics(compareCoreMetrics(baseline.getResult(), current.getResult()))
                .jointMetrics(compareJointMetrics(baseline.getResult(), current.getResult()))
                .insights(generateInsights(baseline.getResult(), current.getResult()))
                .build();

        log.info("Comparison completed successfully");
        return response;
    }

    /**
     * Get analysis and verify ownership.
     */
    private Analysis getAnalysisForUser(Long analysisId, User user) {
        Analysis analysis = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Analysis not found: " + analysisId));

        if (!analysis.getUser().getUserId().equals(user.getUserId())) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_PERMISSIONS,
                    "Access denied to analysis: " + analysisId);
        }

        return analysis;
    }

    /**
     * Validate analysis is completed.
     */
    private void validateAnalysisCompleted(Analysis analysis, String label) {
        if (analysis.getStatus() != Analysis.AnalysisStatus.COMPLETED || analysis.getResult() == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    label + " analysis is not completed yet");
        }
    }

    /**
     * Build analysis summary.
     */
    private ComparisonResponse.AnalysisSummary buildAnalysisSummary(Analysis analysis) {
        return ComparisonResponse.AnalysisSummary.builder()
                .analysisId(analysis.getAnalysisId())
                .filename(analysis.getVideoFilename())
                .analysisDate(analysis.getCompletedAt())
                .riskGrade(analysis.getResult().getRiskGrade().name())
                .overallRiskScore(analysis.getResult().getOverallRiskScore())
                .build();
    }

    /**
     * Calculate overall improvement summary.
     */
    private ComparisonResponse.ImprovementSummary calculateImprovementSummary(
            AnalysisResult baseline, AnalysisResult current) {

        int baselineScore = baseline.getOverallRiskScore();
        int currentScore = current.getOverallRiskScore();

        int change = currentScore - baselineScore;
        double changePercentage = (double) change / baselineScore * 100;

        String status;
        String message;

        if (Math.abs(change) < 3) {
            status = "NO_CHANGE";
            message = "Performance remained relatively stable between analyses.";
        } else if (change > 0) {
            status = "IMPROVED";
            message = String.format("Great progress! Overall risk score improved by %d points (%.1f%%).",
                    change, changePercentage);
        } else {
            status = "DECLINED";
            message = String.format("Performance declined by %d points (%.1f%%). Review recommendations to address concerns.",
                    Math.abs(change), Math.abs(changePercentage));
        }

        return ComparisonResponse.ImprovementSummary.builder()
                .status(status)
                .improvementPercentage(changePercentage)
                .message(message)
                .build();
    }

    /**
     * Compare core metrics.
     */
    private ComparisonResponse.MetricsComparison compareCoreMetrics(
            AnalysisResult baseline, AnalysisResult current) {

        return ComparisonResponse.MetricsComparison.builder()
                .overallRiskScore(calculateMetricChange(
                        baseline.getOverallRiskScore(),
                        current.getOverallRiskScore(),
                        true))
                .consistencyScore(calculateMetricChange(
                        baseline.getConsistencyScore(),
                        current.getConsistencyScore(),
                        true))
                .medicalRiskScore(calculateMetricChange(
                        baseline.getMedicalRiskScore(),
                        current.getMedicalRiskScore(),
                        true))
                .build();
    }

    /**
     * Calculate metric change with direction.
     */
    private ComparisonResponse.MetricChange calculateMetricChange(
            Integer baselineValue, Integer currentValue, boolean higherIsBetter) {

        int change = currentValue - baselineValue;
        double changePercentage = (double) change / baselineValue * 100;

        String direction;
        if (Math.abs(change) < 2) {
            direction = "NO_CHANGE";
        } else if (higherIsBetter) {
            direction = change > 0 ? "IMPROVED" : "DECLINED";
        } else {
            direction = change < 0 ? "IMPROVED" : "DECLINED";
        }

        return ComparisonResponse.MetricChange.builder()
                .baselineValue(baselineValue)
                .currentValue(currentValue)
                .change(change)
                .changePercentage(changePercentage)
                .changeDirection(direction)
                .build();
    }

    /**
     * 13개 생체역학 특징(features)을 비교. featureIndex로 매칭하고
     * userError를 0~100 안전 점수(높을수록 안전)로 변환해 비교한다.
     * (기존 deprecated JointMetrics는 현재 파이프라인이 채우지 않으므로 features 사용)
     */
    private Map<String, ComparisonResponse.JointMetricComparison> compareJointMetrics(
            AnalysisResult baseline, AnalysisResult current) {

        Map<Integer, FeatureDetail> baselineByIndex = baseline.getFeatures().stream()
                .collect(Collectors.toMap(FeatureDetail::getFeatureIndex, f -> f, (a, b) -> a, LinkedHashMap::new));

        Map<String, ComparisonResponse.JointMetricComparison> comparisons = new LinkedHashMap<>();
        for (FeatureDetail currentFeature : current.getFeatures()) {
            FeatureDetail baselineFeature = baselineByIndex.get(currentFeature.getFeatureIndex());
            if (baselineFeature == null) {
                continue;
            }
            comparisons.put(currentFeature.getName(),
                    createFeatureComparison(currentFeature.getName(), baselineFeature, currentFeature));
        }
        return comparisons;
    }

    /**
     * 특징 1개의 비교 결과 생성. 안전 점수가 높을수록(=오차가 작을수록) 개선.
     */
    private ComparisonResponse.JointMetricComparison createFeatureComparison(
            String name, FeatureDetail baseline, FeatureDetail current) {

        int baselineScore = toSafetyScore(baseline.getUserError());
        int currentScore = toSafetyScore(current.getUserError());

        int change = currentScore - baselineScore;
        double changePercentage = baselineScore != 0 ? (double) change / baselineScore * 100 : 0;

        String status;
        if (Math.abs(change) < 3) {
            status = "NO_CHANGE";
        } else {
            status = change > 0 ? "IMPROVED" : "DECLINED";  // 점수 상승 = 개선
        }

        return ComparisonResponse.JointMetricComparison.builder()
                .jointName(name)
                .baselineValue(baselineScore)
                .currentValue(currentScore)
                .change(change)
                .changePercentage(changePercentage)
                .status(status)
                .build();
    }

    /**
     * userError(0~1, 낮을수록 좋음) → 0~100 안전 점수(높을수록 안전).
     */
    private int toSafetyScore(Double userError) {
        double err = userError != null ? userError : 0.0;
        int score = (int) Math.round((1.0 - err) * 100);
        return Math.max(0, Math.min(100, score));
    }

    /**
     * Generate insights based on comparison.
     */
    private List<String> generateInsights(AnalysisResult baseline, AnalysisResult current) {
        List<String> insights = new ArrayList<>();

        // Risk grade comparison
        if (current.getRiskGrade() != baseline.getRiskGrade()) {
            String gradeChange = baseline.getRiskGrade().compareTo(current.getRiskGrade()) > 0
                    ? "improved from " + baseline.getRiskGrade() + " to " + current.getRiskGrade()
                    : "declined from " + baseline.getRiskGrade() + " to " + current.getRiskGrade();
            insights.add("Risk grade has " + gradeChange);
        }

        // Critical zone comparison
        if (baseline.getCriticalZoneDetected() && !current.getCriticalZoneDetected()) {
            insights.add("Excellent! Critical zone issues have been resolved");
        } else if (!baseline.getCriticalZoneDetected() && current.getCriticalZoneDetected()) {
            insights.add("Warning: New critical zone detected - immediate attention required");
        }

        // Feature-level improvements (13개 특징 중 개선된 개수)
        Map<Integer, FeatureDetail> baselineByIndex = baseline.getFeatures().stream()
                .collect(Collectors.toMap(FeatureDetail::getFeatureIndex, f -> f, (a, b) -> a));
        long improvedFeatures = current.getFeatures().stream()
                .filter(cf -> {
                    FeatureDetail bf = baselineByIndex.get(cf.getFeatureIndex());
                    return bf != null && cf.getUserError() < bf.getUserError() - 0.03;
                })
                .count();
        if (improvedFeatures > 0) {
            insights.add(improvedFeatures + " biomechanical features improved compared to the baseline");
        }

        // Overall improvement
        int overallChange = current.getOverallRiskScore() - baseline.getOverallRiskScore();
        if (overallChange > 15) {
            insights.add("Outstanding overall improvement - keep up the excellent work");
        } else if (overallChange < -15) {
            insights.add("Significant performance decline - recommend reviewing training approach");
        }

        // Consistency improvement
        int consistencyChange = current.getConsistencyScore() - baseline.getConsistencyScore();
        if (consistencyChange > 10) {
            insights.add("Delivery consistency has improved significantly");
        }

        // Add general insight if no specific ones
        if (insights.isEmpty()) {
            insights.add("Performance metrics show minor changes - continue monitoring progress");
        }

        return insights;
    }
}
