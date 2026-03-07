package com.waad.tba.modules.systemadmin.service;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.modules.rbac.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Implementation of UserPasswordService
 * Handles self-service password change for authenticated users
 * 
 * Security:
 * - Verifies current password before allowing change
 * - Prevents reuse of same password
 * - Uses BCrypt for password encoding
 * - Logs password change events (without sensitive data)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserPasswordServiceImpl implements UserPasswordService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void changePassword(String username, String currentPassword, String newPassword) {
        log.info("Password change requested for user: {}", username);
        
        // Find user
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    log.warn("Password change failed - user not found: {}", username);
                    return new BusinessRuleException("المستخدم غير موجود");
                });

        // Verify current password
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            log.warn("Password change failed - incorrect current password for user: {}", username);
            throw new BusinessRuleException("كلمة المرور الحالية غير صحيحة");
        }

        // Prevent reuse of same password
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            log.warn("Password change failed - new password same as current for user: {}", username);
            throw new BusinessRuleException("كلمة المرور الجديدة يجب أن تكون مختلفة عن الحالية");
        }

        // Update password
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setPasswordChangedAt(LocalDateTime.now());
        userRepository.save(user);
        
        log.info("Password changed successfully for user: {}", username);
    }

    @Override
    @Transactional
    public void updateProfile(String username, String fullName, String email, String phone) {
        log.info("Profile update requested for user: {}", username);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessRuleException("المستخدم غير موجود"));

        if (fullName != null && !fullName.isBlank()) {
            user.setFullName(fullName.strip());
        }

        if (email != null && !email.isBlank()) {
            String normalizedEmail = email.strip().toLowerCase();
            // Ensure the new email is not taken by another account
            userRepository.findByEmail(normalizedEmail).ifPresent(other -> {
                if (!other.getId().equals(user.getId())) {
                    throw new BusinessRuleException("البريد الإلكتروني مستخدم بالفعل من قِبَل حساب آخر");
                }
            });
            user.setEmail(normalizedEmail);
        }

        if (phone != null) {
            user.setPhone(phone.isBlank() ? null : phone.strip());
        }

        userRepository.save(user);
        log.info("Profile updated successfully for user: {}", username);
    }
}
