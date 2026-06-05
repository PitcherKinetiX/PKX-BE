package com.pkx.domain.analysis.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pkx.common.exception.BusinessException;
import com.pkx.common.exception.ErrorCode;
import com.pkx.domain.analysis.client.AiAnalysisFeignClient;
import com.pkx.domain.analysis.dto.AiAnalyzeRequest;
import com.pkx.domain.analysis.dto.AiAnalyzeResponse;
import com.pkx.domain.analysis.dto.AiAnalyzeStatusResponse;
import com.pkx.domain.aimodel.entity.UserAiModel;
import com.pkx.domain.aimodel.repository.UserAiModelRepository;
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
    private final VideoUploadService videoUploadService;
    private final UserAiModelRepository userAiModelRepository;

    // 비동기 폴링 설정
    private static final long POLL_INTERVAL_MS = 5_000L;          // 5초 간격
    private static final long MAX_WAIT_MS = 10 * 60_000L;         // 최대 10분 대기
    private static final int MAX_CONSECUTIVE_POLL_ERRORS = 5;     // 상태조회 연속 실패 허용 횟수

    /**
     * Feign으로 FastAPI AI 서버에 분석 요청을 보내고 결과를 엔티티로 변환.
     */
    public AnalysisResult analyzeVideo(Long analysisId) {
        log.info("Starting AI analysis for analysisId: {}", analysisId);

        Analysis analysis = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Analysis not found"));

        // GCS Signed URL 생성 (외부 AI 서버가 직접 다운로드하도록)
        String videoUrl = videoUploadService.generateSignedUrl(analysis.getVideoStoragePath());

        // 개인화 모델이 READY면 해당 모델로 분석 (USER_SPECIFIC), 아니면 일반 모델로 폴백
        String modelType = "GENERAL";
        String userModelUrl = null;
        String userStatsUrl = null;
        UserAiModel userModel = userAiModelRepository.findByUser(analysis.getUser()).orElse(null);
        if (userModel != null && userModel.getStatus() == UserAiModel.ModelStatus.READY
                && userModel.getModelStoragePath() != null && userModel.getStatsStoragePath() != null) {
            userModelUrl = videoUploadService.generateSignedUrl(userModel.getModelStoragePath());
            userStatsUrl = videoUploadService.generateSignedUrl(userModel.getStatsStoragePath());
            modelType = "USER_SPECIFIC";
        }

        // FastAPI에 분석 요청
        AiAnalyzeRequest request = AiAnalyzeRequest.builder()
                .fileId(analysis.getVideoStoragePath())
                .userId(analysis.getUser().getUserId())
                .analysisId(analysisId)
                .modelType(modelType)
                .videoUrl(videoUrl)
                .userModelUrl(userModelUrl)
                .userStatsUrl(userStatsUrl)
                .build();

        // 1) 비동기 분석 시작 — jobId 즉시 수령 (짧은 호출 → Cloudflare 524 회피)
        String jobId;
        try {
            jobId = aiAnalysisFeignClient.startAnalyze(request).getJobId();
            log.info("AI analysis started for analysisId: {}, jobId: {}", analysisId, jobId);
        } catch (Exception e) {
            log.error("AI service start failed for analysisId: {}", analysisId, e);
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "AI 분석 시작 실패: " + e.getMessage());
        }

        // 2) DONE 될 때까지 짧은 폴링 (각 호출이 짧아 Cloudflare 100초 제한에 안 걸림)
        AiAnalyzeResponse aiResponse = pollUntilDone(analysisId, jobId);

        // 3) AI 응답 → 엔티티 변환
        return mapToAnalysisResult(analysis, aiResponse, modelType);
    }

    /**
     * AI 서버 잡이 DONE/FAILED 될 때까지 일정 간격으로 상태를 폴링.
     */
    private AiAnalyzeResponse pollUntilDone(Long analysisId, String jobId) {
        long deadline = System.currentTimeMillis() + MAX_WAIT_MS;
        int consecutiveErrors = 0;

        while (System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "AI 분석 대기 중 인터럽트됨");
            }

            AiAnalyzeStatusResponse status;
            try {
                status = aiAnalysisFeignClient.getStatus(jobId);
                consecutiveErrors = 0;
            } catch (Exception e) {
                // 일시적 네트워크 오류 / 잡 유실(404) 등 — 일정 횟수까지 재시도
                consecutiveErrors++;
                log.warn("AI status poll failed for analysisId: {}, jobId: {} ({}/{}): {}",
                        analysisId, jobId, consecutiveErrors, MAX_CONSECUTIVE_POLL_ERRORS, e.getMessage());
                if (consecutiveErrors >= MAX_CONSECUTIVE_POLL_ERRORS) {
                    throw new BusinessException(ErrorCode.AI_SERVICE_ERROR,
                            "AI 상태 조회 연속 실패 (jobId=" + jobId + "): " + e.getMessage());
                }
                continue;
            }

            switch (status.getStatus()) {
                case "DONE" -> {
                    if (status.getResult() == null) {
                        throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "AI 분석 완료됐으나 결과가 비어있음");
                    }
                    log.info("AI analysis completed for analysisId: {}, jobId: {}, grade: {}",
                            analysisId, jobId, status.getResult().getScores().getGrade());
                    return status.getResult();
                }
                case "FAILED" -> throw new BusinessException(ErrorCode.AI_SERVICE_ERROR,
                        "AI 분석 실패: " + status.getError());
                default -> { /* PENDING | RUNNING → 계속 폴링 */ }
            }
        }

        throw new BusinessException(ErrorCode.AI_SERVICE_ERROR,
                "AI 분석 시간 초과 (jobId=" + jobId + ", " + (MAX_WAIT_MS / 1000) + "s)");
    }

    /**
     * AI 응답을 AnalysisResult + FeatureDetail 엔티티로 변환.
     */
    private AnalysisResult mapToAnalysisResult(Analysis analysis, AiAnalyzeResponse response, String modelType) {
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
                .modelType("USER_SPECIFIC".equals(modelType)
                        ? AnalysisResult.ModelType.USER_SPECIFIC
                        : AnalysisResult.ModelType.GENERAL)
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
