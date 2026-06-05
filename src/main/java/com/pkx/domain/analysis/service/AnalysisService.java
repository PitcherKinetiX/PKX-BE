package com.pkx.domain.analysis.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pkx.common.exception.BusinessException;
import com.pkx.common.exception.ErrorCode;
import com.pkx.domain.analysis.dto.*;
import com.pkx.domain.analysis.entity.Analysis;
import com.pkx.domain.analysis.entity.AnalysisResult;
import com.pkx.domain.analysis.entity.FeatureDetail;
import com.pkx.domain.analysis.repository.AnalysisRepository;
import com.pkx.domain.analysis.repository.AnalysisResultRepository;
import com.pkx.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Main service for managing analysis operations.
 * Orchestrates video upload, AI analysis, and result retrieval.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisService {

    private final AnalysisRepository analysisRepository;
    private final AnalysisResultRepository analysisResultRepository;
    private final VideoUploadService videoUploadService;
    private final AiAnalysisService aiAnalysisService;
    private final ObjectMapper objectMapper;

    /**
     * Upload video and start asynchronous analysis.
     */
    @Transactional
    public UploadResponse uploadAndAnalyze(MultipartFile file, User user) {
        log.info("Starting video upload and analysis for user: {}", user.getUserId());

        // Upload video to MinIO
        VideoUploadService.VideoUploadResult uploadResult = videoUploadService.uploadVideo(file, user.getUserId());

        // Create analysis record
        Analysis analysis = Analysis.builder()
                .user(user)
                .videoFilename(uploadResult.getFilename())
                .videoStoragePath(uploadResult.getStoragePath())
                .videoSizeBytes(uploadResult.getSizeBytes())
                .videoDurationSeconds(uploadResult.getDurationSeconds())
                .status(Analysis.AnalysisStatus.UPLOADING)
                .build();

        // 큐에 적재만 하고 즉시 응답 — 실제 AI 분석은 AnalysisQueueWorker가 하나씩 처리
        analysis.markAsQueued();
        analysis = analysisRepository.save(analysis);
        log.info("Created analysis record with ID: {} (QUEUED)", analysis.getAnalysisId());

        return UploadResponse.builder()
                .analysisId(analysis.getAnalysisId())
                .filename(analysis.getVideoFilename())
                .sizeBytes(analysis.getVideoSizeBytes())
                .durationSeconds(analysis.getVideoDurationSeconds())
                .status(analysis.getStatus().name())
                .uploadedAt(analysis.getCreatedAt())
                .message("Video uploaded successfully and analysis started")
                .build();
    }

    /**
     * Get analysis status.
     */
    @Transactional(readOnly = true)
    public AnalysisStatusResponse getAnalysisStatus(Long analysisId, User user) {
        Analysis analysis = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Analysis not found"));

        // Verify ownership
        if (!analysis.getUser().getUserId().equals(user.getUserId())) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_PERMISSIONS, "Access denied");
        }

        // Calculate progress based on status
        Integer progress = calculateProgress(analysis);

        String message = switch (analysis.getStatus()) {
            case UPLOADING -> "Video upload in progress";
            case QUEUED -> "Waiting in analysis queue";
            case PROCESSING -> "AI analysis in progress";
            case COMPLETED -> "Analysis completed successfully";
            case FAILED -> "Analysis failed";
        };

        return AnalysisStatusResponse.builder()
                .analysisId(analysis.getAnalysisId())
                .status(analysis.getStatus().name())
                .message(message)
                .progress(progress)
                .createdAt(analysis.getCreatedAt())
                .startedAt(analysis.getStartedAt())
                .completedAt(analysis.getCompletedAt())
                .errorMessage(analysis.getErrorMessage())
                .build();
    }

    /**
     * Get detailed analysis with results.
     */
    @Transactional(readOnly = true)
    public AnalysisDetailResponse getAnalysisDetail(Long analysisId, User user) {
        Analysis analysis = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Analysis not found"));

        // Verify ownership
        if (!analysis.getUser().getUserId().equals(user.getUserId())) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_PERMISSIONS, "Access denied");
        }

        AnalysisDetailResponse response = AnalysisDetailResponse.builder()
                .analysisId(analysis.getAnalysisId())
                .videoFilename(analysis.getVideoFilename())
                .videoSizeBytes(analysis.getVideoSizeBytes())
                .videoDurationSeconds(analysis.getVideoDurationSeconds())
                .status(analysis.getStatus().name())
                .createdAt(analysis.getCreatedAt())
                .completedAt(analysis.getCompletedAt())
                .build();

        // Add result details if completed
        if (analysis.getStatus() == Analysis.AnalysisStatus.COMPLETED && analysis.getResult() != null) {
            response.setResult(mapToResultDetail(analysis.getResult()));
        }

        return response;
    }

    /**
     * Get paginated list of analyses for user.
     */
    @Transactional(readOnly = true)
    public Page<AnalysisListResponse> getAnalysisList(User user, Pageable pageable) {
        Page<Analysis> analysisPage = analysisRepository.findByUser_UserId(user.getUserId(), pageable);

        return analysisPage.map(this::mapToListResponse);
    }

    /**
     * Delete analysis and associated data.
     */
    @Transactional
    public void deleteAnalysis(Long analysisId, User user) {
        Analysis analysis = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Analysis not found"));

        // Verify ownership
        if (!analysis.getUser().getUserId().equals(user.getUserId())) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_PERMISSIONS, "Access denied");
        }

        // TODO: Delete video file from MinIO storage

        // Delete analysis (cascade will delete result and metrics)
        analysisRepository.delete(analysis);

        log.info("Deleted analysis: {} for user: {}", analysisId, user.getUserId());
    }

    /**
     * Calculate progress percentage based on status.
     */
    private Integer calculateProgress(Analysis analysis) {
        return switch (analysis.getStatus()) {
            case UPLOADING -> 15;
            case QUEUED -> 30;
            case PROCESSING -> 60;
            case COMPLETED -> 100;
            case FAILED -> 0;
        };
    }

    /**
     * Map AnalysisResult entity to ResultDetail DTO.
     */
    private AnalysisDetailResponse.ResultDetail mapToResultDetail(AnalysisResult result) {
        List<String> recommendations = parseRecommendations(result.getRecommendations());

        // 13개 FeatureDetail 엔티티 → DTO 변환
        List<AnalysisDetailResponse.FeatureDetailDto> featureDtos = result.getFeatures().stream()
                .map(f -> AnalysisDetailResponse.FeatureDetailDto.builder()
                        .index(f.getFeatureIndex())
                        .name(f.getName())
                        .type(f.getType())
                        .userError(f.getUserError())
                        .generalError(f.getGeneralError())
                        .level(f.getLevel())
                        .peakValue(f.getPeakValue())
                        .dangerRatio(f.getDangerRatio())
                        .medicalScore(f.getMedicalScore())
                        .build())
                .collect(Collectors.toList());

        return AnalysisDetailResponse.ResultDetail.builder()
                .overallRiskScore(result.getOverallRiskScore())
                .consistencyScore(result.getConsistencyScore())
                .medicalRiskScore(result.getMedicalRiskScore())
                .riskGrade(result.getRiskGrade().name())
                .modelType(result.getModelType().name())
                .modelAccuracy(result.getModelAccuracy())
                .riskSummary(result.getRiskSummary())
                .recommendations(recommendations)
                .criticalZoneDetected(result.getCriticalZoneDetected())
                .criticalZoneDescription(result.getCriticalZoneDescription())
                .features(featureDtos)
                .build();
    }

    /**
     * Map Analysis entity to AnalysisListResponse DTO.
     */
    private AnalysisListResponse mapToListResponse(Analysis analysis) {
        AnalysisListResponse response = AnalysisListResponse.builder()
                .analysisId(analysis.getAnalysisId())
                .videoFilename(analysis.getVideoFilename())
                .videoDurationSeconds(analysis.getVideoDurationSeconds())
                .status(analysis.getStatus().name())
                .createdAt(analysis.getCreatedAt())
                .completedAt(analysis.getCompletedAt())
                .build();

        // Add result summary if completed
        if (analysis.getResult() != null) {
            response.setRiskGrade(analysis.getResult().getRiskGrade().name());
            response.setOverallRiskScore(analysis.getResult().getOverallRiskScore());
        }

        return response;
    }

    /**
     * Parse recommendations JSON string to list.
     */
    private List<String> parseRecommendations(String recommendationsJson) {
        if (recommendationsJson == null || recommendationsJson.isEmpty()) {
            return List.of();
        }

        try {
            return objectMapper.readValue(recommendationsJson, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse recommendations JSON", e);
            return List.of();
        }
    }
}
