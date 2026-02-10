package com.pkx.domain.user.service;

import com.pkx.common.exception.BusinessException;
import com.pkx.common.exception.ErrorCode;
import com.pkx.common.util.JwtUtil;
import com.pkx.domain.user.dto.LoginRequest;
import com.pkx.domain.user.dto.LoginResponse;
import com.pkx.domain.user.dto.RegisterRequest;
import com.pkx.domain.user.dto.UserResponse;
import com.pkx.domain.user.entity.User;
import com.pkx.domain.user.enums.UserRole;
import com.pkx.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

/**
 * Service for authentication operations including registration, login, and token refresh.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${jwt.expiration}")
    private Long jwtExpiration;

    @Value("${jwt.refresh-expiration}")
    private Long refreshExpiration;

    private static final String REFRESH_TOKEN_PREFIX = "refresh_token:";

    /**
     * Register a new user.
     */
    @Transactional
    public UserResponse register(RegisterRequest request) {
        log.info("Registering new user: {}", request.getEmail());

        // Validate password confirmation
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "Password and confirmation do not match");
        }

        // Email 중복 체크
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        // Create new user (현재 User 엔티티는 email/password/name/isActive만 가진다)
        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .name(request.getName())
                .isActive(true)
                .build();

        User savedUser = userRepository.save(user);

        log.info("User registered successfully: {}", savedUser.getEmail());

        return UserResponse.fromEntity(savedUser);
    }

    /**
     * Authenticate user and generate tokens.
     */
    @Transactional
    public LoginResponse login(LoginRequest request) {
        log.info("User login attempt: {}", request.getEmail());

        // Authenticate user
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        // Load user details
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();

        // Generate tokens
        String accessToken = jwtUtil.generateToken(userDetails);
        String refreshToken = jwtUtil.generateRefreshToken(userDetails);

        // Store refresh token in Redis
        String redisKey = REFRESH_TOKEN_PREFIX + request.getEmail();
        redisTemplate.opsForValue().set(redisKey, refreshToken, refreshExpiration, TimeUnit.MILLISECONDS);

        // Load user entity
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        log.info("User logged in successfully: {}", request.getEmail());

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtExpiration)
                .user(UserResponse.fromEntity(user))
                .build();
    }

    /**
     * Refresh access token using refresh token.
     */
    @Transactional(readOnly = true)
    public LoginResponse refreshToken(String refreshToken) {
        log.info("Refreshing access token");

        // Validate refresh token
        if (!jwtUtil.validateToken(refreshToken)) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_EXPIRED);
        }

        // Extract username from refresh token
        String username = jwtUtil.extractUsername(refreshToken);

        // Verify refresh token in Redis
        String redisKey = REFRESH_TOKEN_PREFIX + username;
        String storedToken = (String) redisTemplate.opsForValue().get(redisKey);

        if (storedToken == null || !storedToken.equals(refreshToken)) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID, "Refresh token not found or invalid");
        }

        // Load user details
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);

        // Generate new access token
        String newAccessToken = jwtUtil.generateToken(userDetails);

        // Load user entity
        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        log.info("Access token refreshed successfully for user: {}", username);

        return LoginResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtExpiration)
                .user(UserResponse.fromEntity(user))
                .build();
    }

    /**
     * Logout user by invalidating refresh token.
     */
    @Transactional
    public void logout(String username) {
        log.info("Logging out user: {}", username);

        // Remove refresh token from Redis
        String redisKey = REFRESH_TOKEN_PREFIX + username;
        redisTemplate.delete(redisKey);

        log.info("User logged out successfully: {}", username);
    }
}
