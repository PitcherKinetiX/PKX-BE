package com.pkx.domain.analysis.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pkx.common.exception.BusinessException;
import com.pkx.common.exception.ErrorCode;
import com.pkx.domain.analysis.entity.Analysis;
import com.pkx.domain.analysis.entity.AnalysisResult;
import com.pkx.domain.analysis.entity.JointMetrics;
import com.pkx.domain.analysis.repository.AnalysisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

/**
 * Mock AI Analysis Service for Phase 1.
 * Simulates AI analysis with realistic mock data.
 * Will be replaced with actual AI API integration in Phase 2.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiAnalysisService {

    private final AnalysisRepository analysisRepository;
    private final ObjectMapper objectMapper;

    private static final Random random = new Random();

    /**
     * Analyze video and return mock analysis result.
     * In Phase 2, this will call the actual AI analysis API.
     *
     * @param analysisId The analysis ID to process
     * @return AnalysisResult entity with mock data
     */
    public AnalysisResult analyzeVideo(Long analysisId) {
        log.info("Starting mock AI analysis for analysisId: {}", analysisId);

        Analysis analysis = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Analysis not found"));

        // Simulate AI processing time (1-3 seconds)
        simulateProcessingDelay();

        // Generate random scenario (GOOD, NORMAL, CAUTION, DANGER)
        MockScenario scenario = selectRandomScenario();

        log.info("Generated mock scenario: {} for analysisId: {}", scenario.name(), analysisId);

        // Create analysis result based on scenario
        AnalysisResult result = createMockResult(analysis, scenario);

        return result;
    }

    /**
     * Simulate AI processing delay.
     */
    private void simulateProcessingDelay() {
        try {
            Thread.sleep(1000 + random.nextInt(2000)); // 1-3 seconds
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Processing delay interrupted", e);
        }
    }

    /**
     * Select random mock scenario.
     */
    private MockScenario selectRandomScenario() {
        MockScenario[] scenarios = MockScenario.values();
        return scenarios[random.nextInt(scenarios.length)];
    }

    /**
     * Create mock analysis result based on scenario.
     */
    private AnalysisResult createMockResult(Analysis analysis, MockScenario scenario) {
        AnalysisResult result = AnalysisResult.builder()
                .analysis(analysis)
                .overallRiskScore(scenario.overallRiskScore + randomVariation(5))
                .consistencyScore(scenario.consistencyScore + randomVariation(5))
                .medicalRiskScore(scenario.medicalRiskScore + randomVariation(5))
                .riskGrade(scenario.riskGrade)
                .modelType(AnalysisResult.ModelType.GENERAL)
                .modelAccuracy(BigDecimal.valueOf(92.5 + random.nextDouble() * 5)) // 92.5-97.5%
                .riskSummary(scenario.riskSummary)
                .recommendations(createRecommendationsJson(scenario))
                .criticalZoneDetected(scenario.criticalZoneDetected)
                .criticalZoneDescription(scenario.criticalZoneDescription)
                .build();

        // Create joint metrics
        JointMetrics jointMetrics = createMockJointMetrics(result, scenario);
        result.setJointMetrics(jointMetrics);

        return result;
    }

    /**
     * Create mock joint metrics based on scenario.
     */
    private JointMetrics createMockJointMetrics(AnalysisResult result, MockScenario scenario) {
        return JointMetrics.builder()
                .result(result)
                .shoulderStress(scenario.shoulderStress + randomVariation(10))
                .elbowLoad(scenario.elbowLoad + randomVariation(10))
                .wristLoad(scenario.wristLoad + randomVariation(10))
                .spineAngle(scenario.spineAngle + randomVariation(10))
                .kneeStability(scenario.kneeStability + randomVariation(10))
                .hipRotation(scenario.hipRotation + randomVariation(10))
                .radarData(createRadarDataJson(scenario))
                .temporalErrorData(createTemporalErrorDataJson())
                .build();
    }

    /**
     * Create recommendations JSON array.
     */
    private String createRecommendationsJson(MockScenario scenario) {
        try {
            return objectMapper.writeValueAsString(scenario.recommendations);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize recommendations", e);
            return "[]";
        }
    }

    /**
     * Create radar chart data JSON.
     */
    private String createRadarDataJson(MockScenario scenario) {
        try {
            Map<String, Object> radarData = new LinkedHashMap<>();
            radarData.put("labels", Arrays.asList("Shoulder", "Elbow", "Wrist", "Spine", "Knee", "Hip"));
            radarData.put("values", Arrays.asList(
                    scenario.shoulderStress + randomVariation(5),
                    scenario.elbowLoad + randomVariation(5),
                    scenario.wristLoad + randomVariation(5),
                    scenario.spineAngle + randomVariation(5),
                    scenario.kneeStability + randomVariation(5),
                    scenario.hipRotation + randomVariation(5)
            ));
            radarData.put("benchmark", Arrays.asList(75, 75, 75, 75, 75, 75));

            return objectMapper.writeValueAsString(radarData);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize radar data", e);
            return "{}";
        }
    }

    /**
     * Create temporal error data JSON (time-series data).
     */
    private String createTemporalErrorDataJson() {
        try {
            List<Map<String, Object>> timeSeriesData = new ArrayList<>();

            // Generate 50 frames of mock temporal data
            for (int frame = 0; frame < 50; frame++) {
                Map<String, Object> frameData = new LinkedHashMap<>();
                frameData.put("frame", frame);
                frameData.put("timestamp", frame * 0.033); // 30 fps
                frameData.put("error", 5 + random.nextDouble() * 15); // 5-20 error value
                frameData.put("velocity", 20 + random.nextDouble() * 30); // 20-50 velocity
                timeSeriesData.add(frameData);
            }

            return objectMapper.writeValueAsString(timeSeriesData);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize temporal error data", e);
            return "[]";
        }
    }

    /**
     * Generate random variation within range.
     */
    private int randomVariation(int range) {
        return random.nextInt(range * 2 + 1) - range; // -range to +range
    }

    /**
     * Mock analysis scenarios with realistic data.
     */
    private enum MockScenario {
        GOOD(
                AnalysisResult.RiskGrade.GOOD,
                85, 90, 88,
                20, 18, 22, 15, 12, 20,
                false,
                null,
                "Excellent pitching mechanics! Your form shows minimal stress on joints with consistent delivery.",
                Arrays.asList(
                        "Continue with current training regimen",
                        "Maintain proper warm-up and cool-down routines",
                        "Focus on maintaining shoulder flexibility",
                        "Regular strength training for core stability"
                )
        ),

        NORMAL(
                AnalysisResult.RiskGrade.NORMAL,
                70, 75, 72,
                45, 42, 48, 40, 38, 45,
                false,
                null,
                "Good pitching form with minor areas for improvement. Overall mechanics are sound.",
                Arrays.asList(
                        "Work on elbow extension timing",
                        "Improve hip rotation during delivery",
                        "Strengthen rotator cuff muscles",
                        "Practice consistent release point",
                        "Monitor fatigue levels during training"
                )
        ),

        CAUTION(
                AnalysisResult.RiskGrade.CAUTION,
                50, 55, 48,
                68, 72, 65, 62, 58, 70,
                true,
                "Elevated stress detected during late cocking phase. Elbow valgus angle approaching caution threshold.",
                "Moderate risk detected in pitching mechanics. Several areas require attention to prevent injury.",
                Arrays.asList(
                        "Reduce throwing volume by 20-30%",
                        "Consult with pitching coach on arm slot adjustment",
                        "Strengthen forearm and wrist stabilizers",
                        "Improve lower body drive mechanics",
                        "Consider biomechanical assessment",
                        "Focus on recovery between sessions",
                        "Monitor for any pain or discomfort"
                )
        ),

        DANGER(
                AnalysisResult.RiskGrade.DANGER,
                30, 35, 28,
                88, 92, 85, 82, 78, 90,
                true,
                "Critical stress levels detected during arm acceleration. Elbow valgus torque exceeds safe threshold. Immediate attention required.",
                "HIGH RISK: Significant mechanical issues detected that may lead to injury if not addressed immediately.",
                Arrays.asList(
                        "STOP throwing immediately - rest required",
                        "Schedule appointment with sports medicine physician",
                        "Complete biomechanical evaluation recommended",
                        "Work with certified physical therapist",
                        "Major mechanical adjustments needed before resuming",
                        "Develop comprehensive rehabilitation plan",
                        "Do not resume throwing without medical clearance",
                        "Consider MRI if experiencing any pain"
                )
        );

        final AnalysisResult.RiskGrade riskGrade;
        final int overallRiskScore;
        final int consistencyScore;
        final int medicalRiskScore;
        final int shoulderStress;
        final int elbowLoad;
        final int wristLoad;
        final int spineAngle;
        final int kneeStability;
        final int hipRotation;
        final boolean criticalZoneDetected;
        final String criticalZoneDescription;
        final String riskSummary;
        final List<String> recommendations;

        MockScenario(AnalysisResult.RiskGrade riskGrade,
                     int overallRiskScore, int consistencyScore, int medicalRiskScore,
                     int shoulderStress, int elbowLoad, int wristLoad,
                     int spineAngle, int kneeStability, int hipRotation,
                     boolean criticalZoneDetected, String criticalZoneDescription,
                     String riskSummary, List<String> recommendations) {
            this.riskGrade = riskGrade;
            this.overallRiskScore = overallRiskScore;
            this.consistencyScore = consistencyScore;
            this.medicalRiskScore = medicalRiskScore;
            this.shoulderStress = shoulderStress;
            this.elbowLoad = elbowLoad;
            this.wristLoad = wristLoad;
            this.spineAngle = spineAngle;
            this.kneeStability = kneeStability;
            this.hipRotation = hipRotation;
            this.criticalZoneDetected = criticalZoneDetected;
            this.criticalZoneDescription = criticalZoneDescription;
            this.riskSummary = riskSummary;
            this.recommendations = recommendations;
        }
    }
}
