package com.pkx.security;

import com.pkx.common.exception.BusinessException;
import com.pkx.common.exception.ErrorCode;
import com.pkx.domain.user.entity.User;
import com.pkx.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;

/**
 * Custom implementation of UserDetailsService for loading user-specific data.
 * Used by Spring Security for authentication and authorization.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        log.debug("Loading user by username: {}", username);

        // 현재 시스템에서는 email을 username으로 사용한다.
        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> {
                    log.error("User not found with email: {}", username);
                    return new UsernameNotFoundException("User not found with email: " + username);
                });

        return buildUserDetails(user);
    }

    /**
     * Load user by user ID.
     */
    @Transactional(readOnly = true)
    public UserDetails loadUserById(Long id) {
        log.debug("Loading user by id: {}", id);

        User user = userRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("User not found with id: {}", id);
                    return new BusinessException(ErrorCode.USER_NOT_FOUND);
                });

        return buildUserDetails(user);
    }

    /**
     * Build Spring Security UserDetails from User entity.
     */
    private UserDetails buildUserDetails(User user) {
        if (!user.getIsActive()) {
            throw new BusinessException(ErrorCode.USER_DISABLED);
        }

        return org.springframework.security.core.userdetails.User
                // Spring Security의 username 자리에 email을 사용
                .withUsername(user.getEmail())
                .password(user.getPassword())
                .authorities(getAuthorities(user))
                .accountExpired(false)
                .accountLocked(false)
                .credentialsExpired(false)
                .disabled(!user.getIsActive())
                .build();
    }

    /**
     * Get user authorities (roles).
     */
    private Collection<? extends GrantedAuthority> getAuthorities(User user) {
        // 현재 User 엔티티에는 역할 필드가 없으므로 기본 ROLE_USER를 부여한다.
        String role = "ROLE_USER";
        return Collections.singletonList(new SimpleGrantedAuthority(role));
    }
}
