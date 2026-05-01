package com.waad.tba.security;

import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.modules.rbac.repository.UserRepository;
import com.waad.tba.modules.rbac.service.UserSecurityService;
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
import java.util.List;

/**
 * Custom UserDetailsService — Static Role-Based Authorization (Phase 5)
 *
 * Loads user details and assigns a single ROLE_ authority from user.userType.
 * No dynamic permissions are loaded.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final UserSecurityService securityService;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsernameOrEmail(username, username)
                .orElseThrow(() -> {
                    log.warn("LOGIN: User not found for: {}", username);
                    return new UsernameNotFoundException("User not found with username or email: " + username);
                });

        log.debug("LOGIN: Found user: {}, role: {}", user.getUsername(), user.getUserType());

        // Security checks
        securityService.checkAccountLocked(user);
        securityService.checkEmailVerified(user);

        Collection<? extends GrantedAuthority> authorities = getAuthorities(user);

        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                (user.getActive() == null || user.getActive()),
                true, true, true,
                authorities);
    }

    /**
     * Build authorities from user's static role (userType field).
     * Returns a single authority: ROLE_{userType}
     * 
     * Normalizes role to uppercase to prevent mismatches due to database case
     * sensitivity.
     */
    private Collection<? extends GrantedAuthority> getAuthorities(User user) {
        String role = user.getUserType() != null ? user.getUserType().trim().toUpperCase() : "DATA_ENTRY";
        SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + role);
        log.debug("LOGIN: User {} loaded with role authority: {}", user.getUsername(), authority.getAuthority());
        log.debug("LOGIN: Raw userType from DB: '{}', Normalized: 'ROLE_{}'", user.getUserType(), role);
        return List.of(authority);
    }
}
