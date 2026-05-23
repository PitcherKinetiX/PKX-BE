package com.pkx.domain.analysis.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pkx.common.exception.BusinessException;
import com.pkx.common.exception.ErrorCode;
import com.pkx.domain.analysis.client.AiAnalysisFeignClient;
import com.pkx.domain.analysis.dto.AiAnalyzeRequest;
import com.pkx.domain.analysis.dto.AiAnalyzeResponse;
import com.pkx.domain.analysis.entity.Analysis;
import com.pkx.domain.analysis.entity.AnalysisResult;
import com.pkx.domain.analysis.entity.FeatureDetail;
import com.pkx.domain.analysis.repository.AnalysisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiAnalysisService {

    private final AnalysisRepository analysisRepository;
    private final AiAnalysisFeignClient aiAnalysisFeignClient;
    private final ObjectMapper objectMapper;

    /**
     * Feign으로 FastAPI AI 서버에 분석 요청을 보내고 결과를 엔티티로 변환.
     */
    public AnalysisResult analyzeVideo(Long analysisId) {
        log.info("Starting AI analysis for analysisId: {}", analysisId);

        Analysis analysis = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Analysis not found"));

        // FastAPI에 분석 요청
        AiAnalyzeRequest request = AiAnalyzeRequest.builder()
                .fileId(analysis.getVideoStoragePath())
                .userId(analysis.getUser().getUserId())
                .analysisId(analysisId)
                .modelType("GENERAL")
                .build();

        AiAnalyzeResponse aiResponse;
        try {
            aiResponse = aiAnalysisFeignClient.analyze(request);
            log.info("AI analysis completed for analysisId: {}, grade: {}", analysisId, aiResponse.getScores().getGrade());
        } catch (Exception e) {
            log.error("AI service call failed for analysisId: {}", analysisId, e);
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "AI 분석 서버 호출 실패: " + e.getMessage());
        }

        // AI 응답 → 엔티티 변환
        return mapToAnalysisResult(analysis, aiResponse);
    }

    /**
     * AI 응답을 AnalysisResult + FeatureDetail 엔티티로 변환.
     */
    private AnalysisResult mapToAnalysisResult(Analysis analysis, AiAnalyzeResponse response) {
        AiAnalyzeResponse.Scores scores = response.getScores();

        // grade → RiskGrade 매핑
        AnalysisResult.RiskGrade riskGrade = mapGradeToRiskGrade(scores.getGrade());

        // criticalAreas에서 위험 구간 판정
        boolean criticalDetected = response.getVelocityAnalysis().stream()
                .anyMatch(v -> v.getDangerRatio() >= 1.0);

        String criticalDescription = null;
        if (criticalDetected) {
            criticalDescription = buildCriticalDescription(response);
        }

        // recommendations 생성
        String recommendationsJson = buildRecommendationsJson(response);

        // riskSummary 생성
        String riskSummary = buildRiskSummary(response);

        AnalysisResult result = AnalysisResult.builder()
                .analysis(analysis)
                .overallRiskScore((int) Math.round(scores.getFinalScore()))
                .consistencyScore((int) Math.round(scores.getUserConsistencyScore()))
                .medicalRiskScore((int) Math.round(scores.getMedicalSafetyScore()))
                .riskGrade(riskGrade)
                .modelType(AnalysisResult.ModelType.GENERAL)
                .modelAccuracy(BigDecimal.valueOf(scores.getFinalScore()))
                .riskSummary(riskSummary)
                .recommendations(recommendationsJson)
                .criticalZoneDetected(criticalDetected)
                .criticalZoneDescription(criticalDescription)
                .build();

        // 13개 FeatureDetail 엔티티 생성
        List<FeatureDetail> featureDetails = mapToFeatureDetails(result, response);
        result.setFeatures(featureDetails);

        return result;
    }

    /**
     * AI 응답의 13개 features + velocity 데이터를 FeatureDetail 엔티티 리스트로 변환.
     */
    private List<FeatureDetail> mapToFeatureDetails(AnalysisResult result, AiAnalyzeResponse response) {
        // velocity 데이터를 index로 매핑 (빠른 조회용)
        Map<Integer, AiAnalyzeResponse.VelocityDetail> velocityMap = response.getVelocityAnalysis().stream()
                .collect(Collectors.toMap(AiAnalyzeResponse.VelocityDetail::getIndex, v -> v));

        return response.getFeatures().stream().map(f -> {
            FeatureDetail.FeatureDetailBuilder builder = FeatureDetail.builder()
                    .result(result)
                    .featureIndex(f.getIndex())
                    .name(f.getName())
                    .type(f.getType())
                    .userError(f.getUserError())
                    .generalError(f.getGeneralError())
                    .level(f.getLevel());

            // velocity 타입이면 추가 필드 매핑
            AiAnalyzeResponse.VelocityDetail vel = velocityMap.get(f.getIndex());
            if (vel != null) {
                builder.peakValue(vel.getPeakValue())
                       .dangerRatio(vel.getDangerRatio())
                       .medicalScore(vel.getMedicalScore());
            }

            return builder.build();
        }).collect(Collectors.toList());
    }

    /**
     * AI grade (A+, B+ 등) → RiskGrade enum 매핑.
     */
    private AnalysisResult.RiskGrade mapGradeToRiskGrade(String grade) {
        return switch (grade) {
            case "A+", "A-" -> AnalysisResult.RiskGrade.GOOD;
            case "B+", "B-" -> AnalysisResult.RiskGrade.NORMAL;
            case "C+", "C-" -> AnalysisResult.RiskGrade.CAUTION;
            default -> AnalysisResult.RiskGrade.DANGER;  // D+, D-, F
        };
    }

    /**
     * 위험 구간 설명 텍스트 생성.
     */
    private String buildCriticalDescription(AiAnalyzeResponse response) {
        List<String> dangers = response.getVelocityAnalysis().stream()
                .filter(v -> v.getDangerRatio() >= 1.0)
                .map(v -> String.format("%s (peak=%.1f°/s, danger ratio=%.2f)",
                        v.getName(), v.getPeakValue(), v.getDangerRatio()))
                .collect(Collectors.toList());

        return "위험 임계값 초과 감지: " + String.join(", ", dangers)
                + ". Critical feature: " + response.getCriticalAreas().getMedCriticalFeature();
    }

    /**
     * AI 분석 결과 기반 추천 사항 생성.
     */
    private String buildRecommendationsJson(AiAnalyzeResponse response) {
        List<String> recommendations = new ArrayList<>();
        AiAnalyzeResponse.Scores scores = response.getScores();

        if (scores.getMedicalSafetyScore() < 60) {
            recommendations.add("의학적 안전 점수가 낮습니다. 투구량을 줄이고 전문의 상담을 권장합니다.");
        }
        if (scores.getUserConsistencyScore() < 70) {
            recommendations.add("투구 일관성이 낮습니다. 폼 교정 훈련이 필요합니다.");
        }
        if (scores.getTimingScore() < 80) {
            recommendations.add("운동 사슬(kinetic chain) 타이밍에 문제가 있습니다. 하체 → 골반 → 몸통 → 팔 순서를 점검하세요.");
        }

        for (AiAnalyzeResponse.FeatureDetail feature : response.getFeatures()) {
            if ("위험".equals(feature.getLevel())) {
                recommendations.add(feature.getName() + " 항목이 위험 수준입니다. 해당 관절의 가동 범위와 부하를 점검하세요.");
            } else if ("주의".equals(feature.getLevel())) {
                recommendations.add(feature.getName() + " 항목에 주의가 필요합니다. 모니터링을 강화하세요.");
            }
        }

        for (AiAnalyzeResponse.VelocityDetail vel : response.getVelocityAnalysis()) {
            if (vel.getDangerRatio() >= 1.0) {
                recommendations.add(String.format("%s 속도가 위험 임계값을 초과했습니다 (%.1f°/s). 부상 위험이 있으니 주의하세요.",
                        vel.getName(), vel.getPeakValue()));
            }
        }

        if (recommendations.isEmpty()) {
            recommendations.add("전체적으로 양호한 투구 폼입니다. 현재 훈련을 유지하세요.");
        }

        try {
            return objectMapper.writeValueAsString(recommendations);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize recommendations", e);
            return "[]";
        }
    }

    /**
     * AI 분석 결과 기반 위험 요약 텍스트 생성.
     */
    private String buildRiskSummary(AiAnalyzeResponse response) {
        AiAnalyzeResponse.Scores scores = response.getScores();
        String grade = scores.getGrade();

        long dangerCount = response.getFeatures().stream()
                .filter(f -> "위험".equals(f.getLevel()))
                .count();
        long cautionCount = response.getFeatures().stream()
                .filter(f -> "주의".equals(f.getLevel()))
                .count();

        StringBuilder summary = new StringBuilder();
        summary.append(String.format("종합 등급: %s (%.1f점). ", grade, scores.getFinalScore()));
        summary.append(String.format("일관성: %.1f, 유사도: %.1f, 의학적 안전: %.1f. ",
                scores.getUserConsistencyScore(),
                scores.getGeneralSimilarityScore(),
                scores.getMedicalSafetyScore()));

        if (dangerCount > 0) {
            summary.append(String.format("위험 항목 %d개 감지. ", dangerCount));
        }
        if (cautionCount > 0) {
            summary.append(String.format("주의 항목 %d개 감지. ", cautionCount));
        }

        summary.append("Critical feature: ").append(response.getCriticalAreas().getUserCriticalFeature()).append(".");

        return summary.toString();
    }
}
