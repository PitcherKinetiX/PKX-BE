package com.pkx.domain.aimodel.controller;

import com.pkx.common.dto.ApiResponse;
import com.pkx.common.exception.BusinessException;
import com.pkx.common.exception.ErrorCode;
import com.pkx.domain.aimodel.dto.ModelStatusResponse;
import com.pkx.domain.aimodel.dto.TrainResponse;
import com.pkx.domain.aimodel.service.AiModelService;
import com.pkx.domain.user.entity.User;
import com.pkx.domain.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * REST API controller for AI model management operations.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ai-models")
@RequiredArgsConstructor
@Tag(name = "AI Model", description = "User-specific AI model management APIs")
@SecurityRequirement(name = "bearer-jwt")
public class AiModelController {

    private final AiModelService aiModelService;
    private final UserService userService;

    /**
     * Get AI model status.
     */
    @GetMapping("/status")
    @Operation(
            summary = "Get AI model status",
            description = "Get current status of user's custom AI model including training status and availability"
    )
    public ApiResponse<ModelStatusResponse> getModelStatus(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        log.info("Model status request from user: {}", userDetails.getUsername());

        User user = getUserFromUserDetails(userDetails);
        ModelStatusResponse response = aiModelService.getModelStatus(user);

        return ApiResponse.success(response);
    }

    /**
     * Train user-specific AI model.
     */
    @PostMapping(value = "/train", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Train user-specific AI model",
            description = "Upload at least 10 pitching videos to train a personalized AI model. " +
                    "Training runs on the AI server (GPU) and may take several minutes."
    )
    public ApiResponse<TrainResponse> trainModel(
            @Parameter(description = "Training videos (minimum 10)", required = true)
            @RequestPart("files") List<MultipartFile> files,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        log.info("Model training request from user: {} with {} videos",
                userDetails.getUsername(), files == null ? 0 : files.size());

        User user = getUserFromUserDetails(userDetails);
        TrainResponse response = aiModelService.trainModel(files, user);

        return ApiResponse.success("Model training initiated", response);
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
