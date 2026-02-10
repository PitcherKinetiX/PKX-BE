package com.pkx.domain.comparison.controller;

import com.pkx.common.dto.ApiResponse;
import com.pkx.common.exception.BusinessException;
import com.pkx.common.exception.ErrorCode;
import com.pkx.domain.comparison.dto.ComparisonRequest;
import com.pkx.domain.comparison.dto.ComparisonResponse;
import com.pkx.domain.comparison.service.ComparisonService;
import com.pkx.domain.user.entity.User;
import com.pkx.domain.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * REST API controller for analysis comparison operations.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/comparisons")
@RequiredArgsConstructor
@Tag(name = "Comparison", description = "Analysis comparison APIs")
@SecurityRequirement(name = "bearer-jwt")
public class ComparisonController {

    private final ComparisonService comparisonService;
    private final UserService userService;

    /**
     * Compare two analyses.
     */
    @PostMapping
    @Operation(
            summary = "Compare two analyses",
            description = "Compare a baseline analysis with a current analysis to track improvements and changes"
    )
    public ApiResponse<ComparisonResponse> compareAnalyses(
            @Valid @RequestBody ComparisonRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        log.info("Comparison request: baseline={}, current={} from user={}",
                request.getBaselineAnalysisId(), request.getCurrentAnalysisId(), userDetails.getUsername());

        User user = getUserFromUserDetails(userDetails);
        ComparisonResponse response = comparisonService.compareAnalyses(request, user);

        return ApiResponse.success("Comparison completed successfully", response);
    }

    /**
     * Get comparison (alternative GET endpoint for bookmarked comparisons).
     */
    @GetMapping
    @Operation(
            summary = "Get comparison",
            description = "Get comparison between two analyses using query parameters"
    )
    public ApiResponse<ComparisonResponse> getComparison(
            @RequestParam Long baselineAnalysisId,
            @RequestParam Long currentAnalysisId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        ComparisonRequest request = ComparisonRequest.builder()
                .baselineAnalysisId(baselineAnalysisId)
                .currentAnalysisId(currentAnalysisId)
                .build();

        User user = getUserFromUserDetails(userDetails);
        ComparisonResponse response = comparisonService.compareAnalyses(request, user);

        return ApiResponse.success(response);
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
