package com.pkx.domain.user.controller;

import com.pkx.common.dto.ApiResponse;
import com.pkx.domain.user.dto.ChangePasswordRequest;
import com.pkx.domain.user.dto.UpdateProfileRequest;
import com.pkx.domain.user.dto.UserProfileResponse;
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
 * REST API controller for user profile management.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "User", description = "User profile management APIs")
@SecurityRequirement(name = "bearer-jwt")
public class UserController {

    private final UserService userService;

    /**
     * Get current user profile.
     */
    @GetMapping("/me")
    @Operation(
            summary = "Get current user profile",
            description = "Retrieve the authenticated user's profile information"
    )
    public ApiResponse<UserProfileResponse> getCurrentUserProfile(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        log.info("Profile request from user: {}", userDetails.getUsername());

        User user = getUserFromUserDetails(userDetails);
        UserProfileResponse response = userService.getUserProfile(user);

        return ApiResponse.success(response);
    }

    /**
     * Update current user profile.
     */
    @PutMapping("/me")
    @Operation(
            summary = "Update current user profile",
            description = "Update the authenticated user's profile information"
    )
    public ApiResponse<UserProfileResponse> updateCurrentUserProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        log.info("Profile update request from user: {}", userDetails.getUsername());

        User user = getUserFromUserDetails(userDetails);
        UserProfileResponse response = userService.updateUserProfile(request, user);

        return ApiResponse.success("Profile updated successfully", response);
    }

    /**
     * Change current user password.
     */
    @PutMapping("/me/password")
    @Operation(
            summary = "Change password",
            description = "Change the authenticated user's password"
    )
    public ApiResponse<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        log.info("Password change request from user: {}", userDetails.getUsername());

        User user = getUserFromUserDetails(userDetails);
        userService.changePassword(request, user);

        return ApiResponse.success("Password changed successfully", null);
    }

    /**
     * Helper method to get User entity from UserDetails.
     */
    private User getUserFromUserDetails(UserDetails userDetails) {
        // TODO: Implement proper user lookup from UserService
        return User.builder()
                .userId(1L)
                .email(userDetails.getUsername())
                .name("Mock User")
                .build();
    }
}
