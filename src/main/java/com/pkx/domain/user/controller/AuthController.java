package com.pkx.domain.user.controller;

import com.pkx.common.dto.ApiResponse;
import com.pkx.domain.user.dto.LoginRequest;
import com.pkx.domain.user.dto.LoginResponse;
import com.pkx.domain.user.dto.RegisterRequest;
import com.pkx.domain.user.dto.UserResponse;
import com.pkx.domain.user.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for authentication endpoints.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication management APIs")
public class AuthController {

    private final AuthService authService;

    /**
     * Register a new user.
     */
    @PostMapping("/register")
    @Operation(summary = "Register a new user")
    public ApiResponse<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.info("POST /api/v1/auth/register - email: {}", request.getEmail());
        UserResponse response = authService.register(request);
        return ApiResponse.success("User registered successfully", response);
    }

    /**
     * Login and obtain access token.
     */
    @PostMapping("/login")
    @Operation(summary = "Login and obtain access token")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("POST /api/v1/auth/login - email: {}", request.getEmail());
        LoginResponse response = authService.login(request);
        return ApiResponse.success("Login successful", response);
    }

    /**
     * Refresh access token using refresh token.
     */
    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token")
    public ApiResponse<LoginResponse> refresh(@RequestParam String refreshToken) {
        log.info("POST /api/v1/auth/refresh");
        LoginResponse response = authService.refreshToken(refreshToken);
        return ApiResponse.success("Token refreshed successfully", response);
    }

    /**
     * Logout current user.
     */
    @PostMapping("/logout")
    @Operation(summary = "Logout current user")
    public ApiResponse<Void> logout() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        log.info("POST /api/v1/auth/logout - username: {}", username);
        authService.logout(username);

        // Void 응답이므로 data 자리에 null을 전달
        return ApiResponse.success("Logout successful", null);
    }
}
