package com.pkx.domain.analysis.controller;

import com.pkx.common.dto.ApiResponse;
import com.pkx.common.dto.PageResponse;
import com.pkx.common.exception.BusinessException;
import com.pkx.common.exception.ErrorCode;
import com.pkx.domain.analysis.dto.*;
import com.pkx.domain.analysis.service.AnalysisService;
import com.pkx.domain.analysis.service.PdfReportService;
import com.pkx.domain.user.entity.User;
import com.pkx.domain.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST API controller for video analysis operations.
 * Handles video upload, analysis status, result retrieval, and deletion.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/analyses")
@RequiredArgsConstructor
@Validated
@Tag(name = "Analysis", description = "Video analysis APIs")
@SecurityRequirement(name = "bearer-jwt")
public class AnalysisController {

    private final AnalysisService analysisService;
    private final PdfReportService pdfReportService;
    private final UserService userService;

    /**
     * Upload video and start analysis.
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Upload video for analysis",
            description = "Upload a pitching video and start AI analysis. " +
                    "Supported formats: MP4, MPEG, MOV, AVI, MKV. Max size: 500MB."
    )
    public ApiResponse<UploadResponse> uploadVideo(
            @Parameter(description = "Video file to analyze", required = true)
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        log.info("Upload request received from user: {}", userDetails.getUsername());

        User user = getUserFromUserDetails(userDetails);
        UploadResponse response = analysisService.uploadAndAnalyze(file, user);

        return ApiResponse.success("Video uploaded successfully", response);
    }

    /**
     * Get analysis status.
     */
    @GetMapping("/{id}/status")
    @Operation(
            summary = "Get analysis status",
            description = "Check the current status of an analysis (UPLOADING, PROCESSING, COMPLETED, FAILED)"
    )
    public ApiResponse<AnalysisStatusResponse> getAnalysisStatus(
            @Parameter(description = "Analysis ID", required = true)
            @PathVariable("id") Long analysisId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User user = getUserFromUserDetails(userDetails);
        AnalysisStatusResponse response = analysisService.getAnalysisStatus(analysisId, user);

        return ApiResponse.success(response);
    }

    /**
     * Get detailed analysis with results.
     */
    @GetMapping("/{id}")
    @Operation(
            summary = "Get analysis details",
            description = "Retrieve detailed analysis results including risk scores, joint metrics, and recommendations"
    )
    public ApiResponse<AnalysisDetailResponse> getAnalysisDetail(
            @Parameter(description = "Analysis ID", required = true)
            @PathVariable("id") Long analysisId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User user = getUserFromUserDetails(userDetails);
        AnalysisDetailResponse response = analysisService.getAnalysisDetail(analysisId, user);

        return ApiResponse.success(response);
    }

    /**
     * Get paginated list of analyses.
     */
    @GetMapping
    @Operation(
            summary = "Get analysis list",
            description = "Retrieve paginated list of user's analyses"
    )
    public ApiResponse<PageResponse<AnalysisListResponse>> getAnalysisList(
            @Parameter(description = "Page number (0-indexed)", example = "0")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size", example = "20")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @Parameter(description = "Sort field", example = "createdAt")
            @RequestParam(defaultValue = "createdAt") String sort,
            @Parameter(description = "Sort direction", example = "DESC")
            @RequestParam(defaultValue = "DESC") Sort.Direction direction,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User user = getUserFromUserDetails(userDetails);

        // Some clients may send sort param like "createdAt,desc".
        // In that case, strip off any direction suffix and rely on the explicit 'direction' param.
        String sortProperty = sort.split(",")[0];

        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortProperty));
        Page<AnalysisListResponse> analysisPage = analysisService.getAnalysisList(user, pageable);

        return ApiResponse.success(PageResponse.of(analysisPage));
    }

    /**
     * Download PDF report.
     */
    @GetMapping("/{id}/report")
    @Operation(
            summary = "Download PDF report",
            description = "Generate and download a comprehensive PDF report of the analysis"
    )
    public ResponseEntity<byte[]> downloadReport(
            @Parameter(description = "Analysis ID", required = true)
            @PathVariable("id") Long analysisId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User user = getUserFromUserDetails(userDetails);
        byte[] pdfBytes = pdfReportService.generateReport(analysisId, user);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "analysis_report_" + analysisId + ".pdf");
        headers.setContentLength(pdfBytes.length);

        return ResponseEntity.ok()
                .headers(headers)
                .body(pdfBytes);
    }

    /**
     * Delete analysis.
     */
    @DeleteMapping("/{id}")
    @Operation(
            summary = "Delete analysis",
            description = "Delete an analysis and all associated data including video file"
    )
    public ApiResponse<Void> deleteAnalysis(
            @Parameter(description = "Analysis ID", required = true)
            @PathVariable("id") Long analysisId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User user = getUserFromUserDetails(userDetails);
        analysisService.deleteAnalysis(analysisId, user);

        return ApiResponse.success("Analysis deleted successfully", null);
    }

    /**
     * Helper method to get User entity from UserDetails.
     */
    private User getUserFromUserDetails(UserDetails userDetails) {
        String email = userDetails.getUsername();
        return userService.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
