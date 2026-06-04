package com.pkx.domain.user.service;

import com.pkx.common.exception.BusinessException;
import com.pkx.common.exception.ErrorCode;
import com.pkx.domain.analysis.entity.Analysis;
import com.pkx.domain.analysis.repository.AnalysisRepository;
import com.pkx.domain.analysis.service.VideoUploadService;
import com.pkx.domain.user.dto.ChangePasswordRequest;
import com.pkx.domain.user.dto.UpdateProfileRequest;
import com.pkx.domain.user.dto.UserProfileResponse;
import com.pkx.domain.user.entity.User;
import com.pkx.domain.user.repository.RefreshTokenRepository;
import com.pkx.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for user profile management operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AnalysisRepository analysisRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final VideoUploadService videoUploadService;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String REFRESH_TOKEN_PREFIX = "refresh_token:";

    /**
     * Find user by email.
     */
    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    /**
     * Get user profile information.
     */
    @Transactional(readOnly = true)
    public UserProfileResponse getUserProfile(User user) {
        return UserProfileResponse.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .name(user.getName())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .lastLoginAt(user.getLastLoginAt())
                .isActive(user.getIsActive())
                .build();
    }

    /**
     * Update user profile.
     */
    @Transactional
    public UserProfileResponse updateUserProfile(UpdateProfileRequest request, User user) {
        log.info("Updating profile for user: {}", user.getUserId());

        // Update user
        User updatedUser = userRepository.findById(user.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        updatedUser.setName(request.getName());
        updatedUser = userRepository.save(updatedUser);

        log.info("Profile updated successfully for user: {}", user.getUserId());

        return getUserProfile(updatedUser);
    }

    /**
     * Change user password.
     */
    @Transactional
    public void changePassword(ChangePasswordRequest request, User user) {
        log.info("Changing password for user: {}", user.getUserId());

        // Get user from database
        User dbUser = userRepository.findById(user.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // Verify current password
        if (!passwordEncoder.matches(request.getCurrentPassword(), dbUser.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "Current password is incorrect");
        }

        // Validate new password confirmation
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "New password and confirmation do not match");
        }

        // Validate new password is different from current
        if (request.getCurrentPassword().equals(request.getNewPassword())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "New password must be different from current password");
        }

        // Update password
        dbUser.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(dbUser);

        log.info("Password changed successfully for user: {}", user.getUserId());
    }

    /**
     * Permanently delete (withdraw) the user account and all associated data.
     * Deletes the user's analyses (cascading to results/metrics) and their GCS video files,
     * removes refresh tokens, then deletes the user.
     */
    @Transactional
    public void deleteAccount(User user, String rawPassword) {
        log.info("Account deletion requested for user: {}", user.getUserId());

        User dbUser = userRepository.findById(user.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // Verify password before deletion
        if (!passwordEncoder.matches(rawPassword, dbUser.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "Password is incorrect");
        }

        // Delete the user's analyses (DB cascade handles results/metrics) and GCS video files
        List<Analysis> analyses = analysisRepository.findByUser(dbUser);
        for (Analysis analysis : analyses) {
            videoUploadService.deleteFile(analysis.getVideoStoragePath());
        }
        analysisRepository.deleteAll(analyses);

        // Remove persisted refresh tokens and the cached one in Redis
        refreshTokenRepository.deleteByUser(dbUser);
        redisTemplate.delete(REFRESH_TOKEN_PREFIX + dbUser.getEmail());

        // Finally remove the user
        userRepository.delete(dbUser);

        log.info("Account deleted successfully for user: {} (analyses removed: {})",
                dbUser.getUserId(), analyses.size());
    }
}
