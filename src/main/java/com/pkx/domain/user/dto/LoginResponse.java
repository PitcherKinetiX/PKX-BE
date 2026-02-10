package com.pkx.domain.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for login response containing tokens and user info.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    /**
     * JWT access token for authentication.
     */
    private String accessToken;

    /**
     * JWT refresh token for obtaining new access tokens.
     */
    private String refreshToken;

    /**
     * Token type (always "Bearer").
     */
    @Builder.Default
    private String tokenType = "Bearer";

    /**
     * Access token expiration time in milliseconds.
     */
    private Long expiresIn;

    /**
     * User information.
     */
    private UserResponse user;
}
