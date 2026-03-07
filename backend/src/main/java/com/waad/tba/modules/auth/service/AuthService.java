package com.waad.tba.modules.auth.service;

import java.time.LocalDateTime;
import java.util.List;
import java.security.SecureRandom;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.common.exception.ResourceNotFoundException;
import com.waad.tba.common.service.SystemSettingsService;
import com.waad.tba.core.email.EmailService;
import com.waad.tba.modules.auth.dto.LoginRequest;
import com.waad.tba.modules.auth.dto.LoginResponse;
import com.waad.tba.modules.auth.dto.RegisterRequest;
import com.waad.tba.modules.rbac.entity.PasswordResetToken;
import com.waad.tba.modules.rbac.repository.PasswordResetTokenRepository;
import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.modules.rbac.repository.UserRepository;
import com.waad.tba.security.JwtTokenProvider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

        private final UserRepository userRepository;
        private final PasswordEncoder passwordEncoder;
        private final JwtTokenProvider jwtTokenProvider;
        private final AuthenticationManager authenticationManager;
        private final EmailService emailService;
        private final PasswordResetTokenRepository passwordResetTokenRepository;
        private final ProviderRepository providerRepository;
        private final SystemSettingsService systemSettingsService;
        private static final SecureRandom SECURE_RANDOM = new SecureRandom();

        // ═══════════════════════════════════════════════════════════════════════════
        // ROLE NAMES - must match SystemRole enum exactly
        // ═══════════════════════════════════════════════════════════════════════════
        private static final String ROLE_PROVIDER = "PROVIDER_STAFF";
        private static final String ROLE_EMPLOYER_ADMIN = "EMPLOYER_ADMIN";
        private static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";

        @Transactional
        public LoginResponse login(LoginRequest request) {
                String identifier = request.getIdentifier();
                log.info("Login attempt for identifier: {}", identifier);

                // Find user by username or email (identifier can be either)
                User user = userRepository.findByUsernameOrEmail(identifier, identifier)
                                .orElseThrow(() -> {
                                        log.error("User not found with identifier: {}", identifier);
                                        return new BadCredentialsException("Invalid email or password");
                                });

                if (!user.getActive()) {
                        log.error("Inactive user attempted login: {}", user.getEmail());
                        throw new RuntimeException("Account is not active");
                }

                // ═══════════════════════════════════════════════════════════════════════════
                // PROVIDER SECURITY HARDENING (2026-01-16):
                // Validate Provider binding BEFORE authentication
                // Prevent login if PROVIDER user has no valid providerId
                // ═══════════════════════════════════════════════════════════════════════════
                validateRoleBindingsBeforeLogin(user);

                // Authenticate
                Authentication authentication = authenticationManager.authenticate(
                                new UsernamePasswordAuthenticationToken(user.getUsername(), request.getPassword()));

                if (!authentication.isAuthenticated()) {
                        log.error("Authentication failed for user: {}", user.getEmail());
                        throw new BadCredentialsException("Invalid email or password");
                }

                // Extract role from userType
                String userRole = user.getUserType() != null ? user.getUserType() : "DATA_ENTRY";
                List<String> roles = List.of(userRole);

                // Permissions are now handled by backend @PreAuthorize — no dynamic permissions
                // list needed
                List<String> permissions = List.of();

                // Generate JWT token
                String token = jwtTokenProvider.generateToken(user);

                // Fetch provider name if applicable
                String providerName = null;
                if (user.getProviderId() != null) {
                        providerName = providerRepository.findById(user.getProviderId())
                                        .map(Provider::getName)
                                        .orElse(null);
                }

                log.info("Login successful for user: {}", user.getUsername());

                return LoginResponse.builder()
                                .token(token)
                                .user(LoginResponse.UserInfo.builder()
                                                .id(user.getId())
                                                .username(user.getUsername())
                                                .fullName(user.getFullName())
                                                .email(user.getEmail())
                                                .roles(roles)
                                                .permissions(permissions)
                                                .employerId(user.getEmployerId())
                                                .providerId(user.getProviderId())
                                                .providerName(providerName)
                                                .build())
                                .build();
        }

        /**
         * ═══════════════════════════════════════════════════════════════════════════
         * ROLE BINDING VALIDATION (2026-01-16)
         * ═══════════════════════════════════════════════════════════════════════════
         * 
         * Validates that users with specific roles have the required bindings:
         * - PROVIDER users MUST have a valid providerId
         * - EMPLOYER_ADMIN users SHOULD have an employerId (warning only)
         * 
         * This validation runs BEFORE password check to prevent login for
         * improperly configured accounts.
         * 
         * @param user User attempting to login
         * @throws BusinessRuleException if PROVIDER has no providerId
         */
        public void validateRoleBindingsBeforeLogin(User user) {
                String userRole = user.getUserType();
                if (userRole == null || userRole.isBlank()) {
                        return;
                }

                boolean isProvider = ROLE_PROVIDER.equals(userRole);

                boolean isEmployerAdmin = ROLE_EMPLOYER_ADMIN.equals(userRole);

                // PROVIDER MUST have providerId
                if (isProvider) {
                        if (user.getProviderId() == null) {
                                log.error("❌ LOGIN_BLOCKED: PROVIDER user {} has no providerId binding",
                                                user.getUsername());
                                throw new BusinessRuleException(
                                                "حساب مقدم الخدمة غير مكتمل الإعداد. لم يتم ربط المستخدم بمقدم خدمة. " +
                                                                "يرجى التواصل مع مدير النظام. / " +
                                                                "Provider account setup incomplete. User is not linked to a provider. "
                                                                +
                                                                "Please contact system administrator. [Error: PROVIDER_NOT_LINKED]");
                        }

                        // Also validate that the provider exists and is active
                        Provider provider = providerRepository.findById(user.getProviderId()).orElse(null);
                        if (provider == null) {
                                log.error("❌ LOGIN_BLOCKED: PROVIDER user {} linked to non-existent providerId={}",
                                                user.getUsername(), user.getProviderId());
                                throw new BusinessRuleException(
                                                "مقدم الخدمة المرتبط بالحساب غير موجود في النظام. " +
                                                                "يرجى التواصل مع مدير النظام. / " +
                                                                "The linked provider does not exist in the system. " +
                                                                "Please contact system administrator. [Error: PROVIDER_NOT_FOUND]");
                        }

                        if (!provider.getActive()) {
                                log.error("❌ LOGIN_BLOCKED: PROVIDER user {} linked to inactive provider {} (id={})",
                                                user.getUsername(), provider.getName(), user.getProviderId());
                                throw new BusinessRuleException(
                                                "مقدم الخدمة المرتبط بالحساب غير نشط. " +
                                                                "يرجى التواصل مع مدير النظام. / " +
                                                                "The linked provider is not active. " +
                                                                "Please contact system administrator. [Error: PROVIDER_INACTIVE]");
                        }

                        log.info("✅ PROVIDER binding validated: user={}, providerId={}, providerName={}",
                                        user.getUsername(), user.getProviderId(), provider.getName());
                }

                // EMPLOYER_ADMIN SHOULD have employerId (warning only, don't block)
                if (isEmployerAdmin && user.getEmployerId() == null) {
                        log.warn("⚠️ LOGIN_WARNING: EMPLOYER_ADMIN user {} has no employerId binding",
                                        user.getUsername());
                }
        }

        @Transactional
        public LoginResponse register(RegisterRequest request) {
                log.info("Registration attempt for username: {}", request.getUsername());

                if (userRepository.existsByUsername(request.getUsername())) {
                        throw new IllegalArgumentException("Username already exists");
                }

                if (userRepository.existsByEmail(request.getEmail())) {
                        throw new IllegalArgumentException("Email already exists");
                }

                User user = User.builder()
                                .username(request.getUsername())
                                .password(passwordEncoder.encode(request.getPassword()))
                                .fullName(request.getFullName())
                                .email(request.getEmail())
                                .phone(request.getPhone())
                                .active(true)
                                .build();

                user = userRepository.save(user);
                log.info("User registered successfully: {}", user.getUsername());

                // Auto-login after registration
                LoginRequest loginRequest = LoginRequest.builder()
                                .identifier(request.getUsername())
                                .password(request.getPassword())
                                .build();

                return login(loginRequest);
        }

        /**
         * Get current authenticated user info
         * UPDATED (2026-02-08): Added SUPER_ADMIN dynamic permissions
         * UPDATED (2026-02-12): Added null safety checks
         */
        @Transactional(readOnly = true)
        public LoginResponse.UserInfo getCurrentUser(String token) {
                // SECURITY FIX: Validate token input
                if (token == null || token.isBlank()) {
                        throw new IllegalArgumentException("Token cannot be null or empty");
                }

                String username = jwtTokenProvider.getUsernameFromToken(token);

                // SECURITY FIX: Validate username extraction
                if (username == null || username.isBlank()) {
                        throw new RuntimeException("Invalid token: Unable to extract username");
                }

                User user = userRepository.findByUsername(username)
                                .orElseThrow(() -> new RuntimeException("User not found"));

                String userRole = user.getUserType() != null ? user.getUserType() : "DATA_ENTRY";
                List<String> roles = List.of(userRole);

                // Permissions handled by backend @PreAuthorize — no dynamic permissions list
                // needed
                List<String> permissions = List.of();

                return LoginResponse.UserInfo.builder()
                                .id(user.getId())
                                .username(user.getUsername())
                                .fullName(user.getFullName())
                                .email(user.getEmail())
                                .roles(roles)
                                .permissions(permissions)
                                .employerId(user.getEmployerId())
                                .providerId(user.getProviderId())
                                .build();
        }

        /**
         * Get user info by username (for session-based auth)
         * Phase A - Session Auth Support
         * 
         * PROVIDER PORTAL (2026-01-14):
         * Now includes providerName for PROVIDER users to display in forms.
         * 
         * UPDATED (2026-02-08): SUPER_ADMIN dynamic permissions
         */
        @Transactional(readOnly = true)
        public LoginResponse.UserInfo getUserInfo(String username) {
                User user = userRepository.findByUsername(username)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "User not found with username: " + username));

                String userRole = user.getUserType() != null ? user.getUserType() : "DATA_ENTRY";
                List<String> roles = List.of(userRole);

                // Permissions handled by backend @PreAuthorize — no dynamic permissions list
                // needed
                List<String> permissions = List.of();

                // Fetch provider name if user is a PROVIDER
                String providerName = null;
                if (user.getProviderId() != null) {
                        providerName = providerRepository.findById(user.getProviderId())
                                        .map(Provider::getName)
                                        .orElse(null);
                }

                return LoginResponse.UserInfo.builder()
                                .id(user.getId())
                                .username(user.getUsername())
                                .fullName(user.getFullName())
                                .email(user.getEmail())
                                .roles(roles)
                                .permissions(permissions)
                                .employerId(user.getEmployerId())
                                .providerId(user.getProviderId())
                                .providerName(providerName)
                                .build();
        }

        @Transactional
        public void sendResetOtp(String email) {
                log.info("Password reset OTP request for email: {}", email);

                // 1) Check if user exists by email
                User user = userRepository.findByEmail(email)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "User not found with email: " + email));

                // 2) Generate OTP from centralized settings
                int otpLength = Math.max(4, Math.min(10, systemSettingsService.getPasswordResetOtpLength()));
                String otp = generateNumericOtp(otpLength);

                // 3) Compute expiry from centralized settings
                int otpExpiryMinutes = Math.max(1,
                                Math.min(60, systemSettingsService.getPasswordResetOtpExpiryMinutes()));
                LocalDateTime expiry = LocalDateTime.now().plusMinutes(otpExpiryMinutes);

                // 4) Remove any existing token for this email
                passwordResetTokenRepository.deleteByEmail(email);

                // 5) Save new token
                PasswordResetToken token = PasswordResetToken.builder()
                                .email(email)
                                .otp(otp)
                                .expiryTime(expiry)
                                .build();
                passwordResetTokenRepository.save(token);

                // 6) Send email using EmailService
                String subject = "TBA-WAAD Password Reset OTP";
                String body = String.format(
                                """
                                                Dear %s,

                                                You have requested to reset your password.

                                                Your OTP code is: %s

                                                This code will expire in %d minutes.

                                                If you did not request this, please ignore this email.

                                                Best regards,
                                                TBA-WAAD System""",
                                user.getFullName(), otp, otpExpiryMinutes);

                emailService.sendOtpTemplate(email, user.getFullName(), otp);
                log.info("Password reset OTP sent successfully to: {}", email);
        }

        private String generateNumericOtp(int length) {
                StringBuilder sb = new StringBuilder(length);
                for (int i = 0; i < length; i++) {
                        sb.append(SECURE_RANDOM.nextInt(10));
                }
                return sb.toString();
        }

        @Transactional
        public void resetPassword(String email, String otp, String newPassword) {
                log.info("Password reset attempt for email: {}", email);

                // 1) Load token by email
                PasswordResetToken token = passwordResetTokenRepository.findByEmail(email)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "No reset request found for this email"));

                // 2) Validate OTP
                if (!token.getOtp().equals(otp)) {
                        throw new IllegalArgumentException("Invalid OTP");
                }

                // 3) Validate expiry
                if (token.getExpiryTime().isBefore(LocalDateTime.now())) {
                        throw new IllegalArgumentException("OTP has expired");
                }

                // 4) Load user by email
                User user = userRepository.findByEmail(email)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "User not found with email: " + email));

                // 5) Encode password and save user
                user.setPassword(passwordEncoder.encode(newPassword));
                userRepository.save(user);

                // 6) Delete token record for this email
                passwordResetTokenRepository.deleteByEmail(email);

                log.info("Password reset successfully for user: {}", user.getUsername());
        }

        /**
         * ═══════════════════════════════════════════════════════════════════════════
         * REFRESH JWT TOKEN (2026-02-07)
         * ═══════════════════════════════════════════════════════════════════════════
         * 
         * Generates a NEW JWT token with FRESH roles and permissions from database.
         * 
         * USE CASE:
         * - Admin assigns new role/permission to user
         * - User calls /api/v1/auth/refresh-token to get updated token
         * - No need to logout/login to see new permissions
         * 
         * CRITICAL:
         * - Always fetches CURRENT user data from database (not cached)
         * - Validates all role bindings before generating token
         * - Ensures PROVIDER users have valid providerId
         * 
         * @param username Username of authenticated user
         * @return New LoginResponse with fresh JWT token
         */
        @Transactional(readOnly = true)
        public LoginResponse refreshUserToken(String username) {
                log.info("🔄 Refreshing token for user: {}", username);

                // Fetch FRESH user data from database with all roles and permissions
                User user = userRepository.findByUsername(username)
                                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));

                if (!user.getActive()) {
                        log.error("Inactive user attempted token refresh: {}", user.getUsername());
                        throw new RuntimeException("Account is not active");
                }

                // Validate role bindings (same as login)
                validateRoleBindingsBeforeLogin(user);

                // Extract role from userType
                String userRole = user.getUserType() != null ? user.getUserType() : "DATA_ENTRY";
                List<String> roles = List.of(userRole);

                // Permissions handled by backend @PreAuthorize — no dynamic permissions list
                // needed
                List<String> permissions = List.of();

                // Generate NEW JWT token with fresh permissions
                String token = jwtTokenProvider.generateToken(user);

                // Fetch provider name if applicable
                String providerName = null;
                if (user.getProviderId() != null) {
                        providerName = providerRepository.findById(user.getProviderId())
                                        .map(Provider::getName)
                                        .orElse(null);
                }

                log.info("✅ Token refreshed successfully for user: {} (roles: {}, permissions: {})",
                                user.getUsername(), roles.size(), permissions.size());

                return LoginResponse.builder()
                                .token(token)
                                .user(LoginResponse.UserInfo.builder()
                                                .id(user.getId())
                                                .username(user.getUsername())
                                                .fullName(user.getFullName())
                                                .email(user.getEmail())
                                                .roles(roles)
                                                .permissions(permissions)
                                                .employerId(user.getEmployerId())
                                                .providerId(user.getProviderId())
                                                .providerName(providerName)
                                                .build())
                                .build();
        }
}
