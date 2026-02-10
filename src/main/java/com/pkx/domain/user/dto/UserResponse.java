package com.pkx.domain.user.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.pkx.domain.user.entity.User;
import com.pkx.domain.user.enums.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for user information response.
 *
 * Frontend `UserInfo` type expects:
 * {
 *   userId: number;
 *   email: string;
 *   name: string;
 * }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserResponse {

    private Long userId;
    private String email;
    private String name;

    /**
     * Convert User entity to UserResponse DTO.
     */
    public static UserResponse fromEntity(User user) {
        if (user == null) {
            return null;
        }

        return UserResponse.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .name(user.getName())
                .build();
    }
}
