package com.pkx.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for user profile information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "User profile information")
public class UserProfileResponse {

    @Schema(description = "User ID", example = "1")
    private Long userId;

    @Schema(description = "Email address", example = "pitcher@example.com")
    private String email;

    @Schema(description = "User's full name", example = "John Smith")
    private String name;

    @Schema(description = "Account creation date")
    private LocalDateTime createdAt;

    @Schema(description = "Last profile update date")
    private LocalDateTime updatedAt;

    @Schema(description = "Last login date")
    private LocalDateTime lastLoginAt;

    @Schema(description = "Account active status", example = "true")
    private Boolean isActive;
}
