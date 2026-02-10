package com.pkx.domain.comparison.service;

import com.pkx.common.exception.BusinessException;
import com.pkx.common.exception.ErrorCode;
import com.pkx.domain.analysis.entity.Analysis;
import com.pkx.domain.analysis.entity.AnalysisResult;
import com.pkx.domain.analysis.entity.JointMetrics;
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
                .jointMetrics(compareJointMetrics(baseline.getResult().getJointMetrics(), current.getResult().getJointMetrics()))
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
     * Compare joint metrics.
     */
    private Map<String, ComparisonResponse.JointMetricComparison> compareJointMetrics(
            JointMetrics baseline, JointMetrics current) {

        Map<String, ComparisonResponse.JointMetricComparison> jointComparisons = new LinkedHashMap<>();

        jointComparisons.put("shoulderStress", createJointComparison(
                "Shoulder Stress",
                baseline.getShoulderStress(),
                current.getShoulderStress()));

        jointComparisons.put("elbowLoad", createJointComparison(
                "Elbow Load",
                baseline.getElbowLoad(),
                current.getElbowLoad()));

        jointComparisons.put("wristLoad", createJointComparison(
                "Wrist Load",
                baseline.getWristLoad(),
                current.getWristLoad()));

        jointComparisons.put("spineAngle", createJointComparison(
                "Spine Angle",
                baseline.getSpineAngle(),
                current.getSpineAngle()));

        jointComparisons.put("kneeStability", createJointComparison(
                "Knee Stability",
                baseline.getKneeStability(),
                current.getKneeStability()));

        jointComparisons.put("hipRotation", createJointComparison(
                "Hip Rotation",
                baseline.getHipRotation(),
                current.getHipRotation()));

        return jointComparisons;
    }

    /**
     * Create joint metric comparison.
     */
    private ComparisonResponse.JointMetricComparison createJointComparison(
            String jointName, Integer baselineValue, Integer currentValue) {

        int change = currentValue - baselineValue;
        double changePercentage = baselineValue != 0 ? (double) change / baselineValue * 100 : 0;

        // For joint stress metrics, lower is better
        String status;
        if (Math.abs(change) < 3) {
            status = "NO_CHANGE";
        } else {
            status = change < 0 ? "IMPROVED" : "DECLINED";
        }

        return ComparisonResponse.JointMetricComparison.builder()
                .jointName(jointName)
                .baselineValue(baselineValue)
                .currentValue(currentValue)
                .change(change)
                .changePercentage(changePercentage)
                .status(status)
                .build();
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

        // Joint stress improvements
        JointMetrics baselineMetrics = baseline.getJointMetrics();
        JointMetrics currentMetrics = current.getJointMetrics();

        int shoulderImprovement = baselineMetrics.getShoulderStress() - currentMetrics.getShoulderStress();
        if (shoulderImprovement > 10) {
            insights.add("Significant improvement in shoulder stress reduction");
        }

        int elbowImprovement = baselineMetrics.getElbowLoad() - currentMetrics.getElbowLoad();
        if (elbowImprovement > 10) {
            insights.add("Notable reduction in elbow load - great progress");
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
