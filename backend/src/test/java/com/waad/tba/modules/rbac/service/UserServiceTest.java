package com.waad.tba.modules.rbac.service;

import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.modules.rbac.repository.UserRepository;
import com.waad.tba.modules.auth.service.SessionManagementService;
import com.waad.tba.security.audit.SecurityAuditEvent;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private com.waad.tba.modules.rbac.mapper.UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserSecurityService securityService;

    @Mock
    private SessionManagementService sessionManagementService;

    @InjectMocks
    private UserService userService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .username("testuser")
                .email("test@example.com")
                .active(true)
                .build();
    }

    @Test
    void testFindUserById_Success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userMapper.toResponseDto(testUser)).thenReturn(com.waad.tba.modules.rbac.dto.UserResponseDto.builder().username("testuser").build());

        var result = userService.findById(1L);

        assertNotNull(result);
        assertEquals("testuser", result.getUsername());
        verify(userRepository, times(1)).findById(1L);
    }

    @Test
    void testFindUserById_NotFound() {
        when(userRepository.findById(2L)).thenReturn(Optional.empty());

        assertThrows(com.waad.tba.common.exception.ResourceNotFoundException.class, () -> {
            userService.findById(2L);
        });
    }

    @Test
    void resetPasswordMustEncodePersistAuditAndRevokeSessions() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.encode("Strong@Pass123")).thenReturn("encoded-password");

        userService.resetPassword(1L, "Strong@Pass123");

        assertEquals("encoded-password", testUser.getPassword());
        assertNotNull(testUser.getPasswordChangedAt());
        verify(userRepository).save(testUser);
        verify(securityService).auditLog(
                eq(1L),
                eq(SecurityAuditEvent.AuditActionType.PASSWORD_RESET),
                anyString(), isNull(), isNull());
        verify(sessionManagementService).revokeAll("testuser");
    }

    @Test
    void resetPasswordMustRejectPasswordEqualToUsernameWithoutMutation() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        assertThrows(
                com.waad.tba.modules.rbac.exception.PasswordPolicyViolationException.class,
                () -> userService.resetPassword(1L, "TESTUSER"));

        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any());
        verify(sessionManagementService, never()).revokeAll(anyString());
    }

    // ═══════════════════════════════════════════════════════════════════
    // Settings & permissions closure round: update() previously had no
    // SUPER_ADMIN protection at all, unlike delete()/toggleStatus() — a
    // SUPER_ADMIN could demote the LAST active SUPER_ADMIN account to any
    // other role via a plain update, permanently locking out all
    // administrative access with no recovery path.
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void updateRejectsDemotingTheLastActiveSuperAdmin() {
        User superAdmin = User.builder().id(5L).username("superadmin")
                .email("admin@example.com").userType("SUPER_ADMIN").active(true).build();
        when(userRepository.findById(5L)).thenReturn(Optional.of(superAdmin));
        lenient().when(userRepository.existsByUsernameIgnoreCase(anyString())).thenReturn(false);
        lenient().when(userRepository.existsByEmailIgnoreCase(anyString())).thenReturn(false);
        when(userRepository.countByUserTypeAndActiveTrue("SUPER_ADMIN")).thenReturn(1L);

        com.waad.tba.modules.rbac.dto.UserUpdateDto dto = com.waad.tba.modules.rbac.dto.UserUpdateDto.builder()
                .username("superadmin").email("admin@example.com").userType("DATA_ENTRY").build();

        assertThrows(IllegalArgumentException.class, () -> userService.update(5L, dto));

        verify(userRepository, never()).save(any());
    }

    @Test
    void updateAllowsDemotingASuperAdminWhenAnotherOneRemainsActive() {
        User superAdmin = User.builder().id(5L).username("superadmin")
                .email("admin@example.com").userType("SUPER_ADMIN").active(true).build();
        when(userRepository.findById(5L)).thenReturn(Optional.of(superAdmin));
        lenient().when(userRepository.existsByUsernameIgnoreCase(anyString())).thenReturn(false);
        lenient().when(userRepository.existsByEmailIgnoreCase(anyString())).thenReturn(false);
        when(userRepository.countByUserTypeAndActiveTrue("SUPER_ADMIN")).thenReturn(2L);
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userMapper.toResponseDto(any())).thenReturn(com.waad.tba.modules.rbac.dto.UserResponseDto.builder().build());

        com.waad.tba.modules.rbac.dto.UserUpdateDto dto = com.waad.tba.modules.rbac.dto.UserUpdateDto.builder()
                .username("superadmin").email("admin@example.com").userType("DATA_ENTRY").build();

        userService.update(5L, dto);

        verify(userRepository).save(any());
    }
}
