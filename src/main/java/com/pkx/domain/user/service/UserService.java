package com.pkx.domain.user.service;

import com.pkx.common.exception.BusinessException;
import com.pkx.common.exception.ErrorCode;
import com.pkx.domain.user.dto.ChangePasswordRequest;
import com.pkx.domain.user.dto.UpdateProfileRequest;
import com.pkx.domain.user.dto.UserProfileResponse;
import com.pkx.domain.user.entity.User;
import com.pkx.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
}
