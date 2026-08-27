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

    @Mock
    private com.waad.tba.modules.employer.repository.EmployerRepository employerRepository;

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

        com.waad.tba.modules.employer.entity.Employer employer =
                com.waad.tba.modules.employer.entity.Employer.builder().id(77L).name("جهة اختبار").active(true).build();
        when(employerRepository.findById(77L)).thenReturn(Optional.of(employer));
        com.waad.tba.modules.rbac.dto.UserUpdateDto dto = com.waad.tba.modules.rbac.dto.UserUpdateDto.builder()
                .username("superadmin").email("admin@example.com").userType("DATA_ENTRY").employerId(77L).build();

        userService.update(5L, dto);

        verify(userRepository).save(any());
        verify(sessionManagementService).revokeAll("superadmin");
    }

    @Test
    void createDataEntryRequiresAndPersistsAnActiveEmployerScope() {
        com.waad.tba.modules.rbac.dto.UserCreateDto missingEmployer =
                com.waad.tba.modules.rbac.dto.UserCreateDto.builder()
                        .username("data-entry").email("data@example.com").password("Strong@123")
                        .fullName("مدخل بيانات").userType("DATA_ENTRY").build();
        when(userRepository.existsByUsernameIgnoreCase("data-entry")).thenReturn(false);
        when(userRepository.existsByEmailIgnoreCase("data@example.com")).thenReturn(false);
        when(userMapper.toEntity(missingEmployer)).thenReturn(User.builder().build());
        when(passwordEncoder.encode("Strong@123")).thenReturn("encoded");

        assertThrows(IllegalArgumentException.class, () -> userService.create(missingEmployer));
        verify(userRepository, never()).save(any());

        reset(userRepository, userMapper, passwordEncoder);
        com.waad.tba.modules.rbac.dto.UserCreateDto scoped =
                com.waad.tba.modules.rbac.dto.UserCreateDto.builder()
                        .username("data-entry").email("data@example.com").password("Strong@123")
                        .fullName("مدخل بيانات").userType("DATA_ENTRY").employerId(81L).build();
        User entity = User.builder().build();
        com.waad.tba.modules.employer.entity.Employer employer =
                com.waad.tba.modules.employer.entity.Employer.builder().id(81L).name("جهة نشطة").active(true).build();
        when(userRepository.existsByUsernameIgnoreCase("data-entry")).thenReturn(false);
        when(userRepository.existsByEmailIgnoreCase("data@example.com")).thenReturn(false);
        when(userMapper.toEntity(scoped)).thenReturn(entity);
        when(passwordEncoder.encode("Strong@123")).thenReturn("encoded");
        when(employerRepository.findById(81L)).thenReturn(Optional.of(employer));
        when(userRepository.save(entity)).thenReturn(entity);
        when(userMapper.toResponseDto(entity)).thenReturn(com.waad.tba.modules.rbac.dto.UserResponseDto.builder().build());

        userService.create(scoped);

        assertEquals("DATA_ENTRY", entity.getUserType());
        assertEquals(81L, entity.getEmployerId());
        assertNull(entity.getProviderId());
    }

    @Test
    void dataEntryRejectsInactiveEmployer() {
        User target = User.builder().build();
        com.waad.tba.modules.rbac.dto.UserCreateDto dto =
                com.waad.tba.modules.rbac.dto.UserCreateDto.builder()
                        .username("inactive-scope").email("inactive@example.com").password("Strong@123")
                        .fullName("مدخل بيانات").userType("DATA_ENTRY").employerId(91L).build();
        when(userRepository.existsByUsernameIgnoreCase(dto.getUsername())).thenReturn(false);
        when(userRepository.existsByEmailIgnoreCase(dto.getEmail())).thenReturn(false);
        when(userMapper.toEntity(dto)).thenReturn(target);
        when(passwordEncoder.encode(dto.getPassword())).thenReturn("encoded");
        when(employerRepository.findById(91L)).thenReturn(Optional.of(
                com.waad.tba.modules.employer.entity.Employer.builder().id(91L).name("جهة موقوفة").active(false).build()));

        assertThrows(IllegalArgumentException.class, () -> userService.create(dto));
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateRejectsDeactivatingTheLastActiveSuperAdmin() {
        User superAdmin = User.builder().id(5L).username("superadmin")
                .email("admin@example.com").userType("SUPER_ADMIN").active(true).build();
        when(userRepository.findById(5L)).thenReturn(Optional.of(superAdmin));
        when(userRepository.countByUserTypeAndActiveTrue("SUPER_ADMIN")).thenReturn(1L);

        com.waad.tba.modules.rbac.dto.UserUpdateDto dto = com.waad.tba.modules.rbac.dto.UserUpdateDto.builder()
                .username("superadmin").email("admin@example.com").userType("SUPER_ADMIN").active(false).build();

        assertThrows(IllegalArgumentException.class, () -> userService.update(5L, dto));

        verify(userRepository, never()).save(any());
        verify(sessionManagementService, never()).revokeAll(anyString());
    }

    @Test
    void deleteSoftDeletesNonSuperAdminAndRevokesSessions() {
        User user = User.builder().id(9L).username("provider-user")
                .email("provider@example.com").userType("PROVIDER_STAFF").providerId(44L).active(true).build();
        when(userRepository.findById(9L)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        userService.delete(9L);

        assertFalse(user.getActive());
        verify(userRepository).save(user);
        verify(userRepository, never()).deleteById(anyLong());
        verify(sessionManagementService).revokeAll("provider-user");
        verify(securityService).auditLog(
                eq(9L),
                eq(SecurityAuditEvent.AuditActionType.ACCOUNT_DELETED),
                contains("soft delete"), isNull(), isNull());
    }

    @Test
    void toggleStatusRevokesSessionsWhenStatusChanges() {
        User user = User.builder().id(10L).username("reviewer")
                .email("reviewer@example.com").userType("MEDICAL_REVIEWER").active(true).build();
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userMapper.toResponseDto(any())).thenReturn(com.waad.tba.modules.rbac.dto.UserResponseDto.builder().build());

        userService.toggleStatus(10L);

        assertFalse(user.getActive());
        verify(sessionManagementService).revokeAll("reviewer");
    }

    @Test
    void updateRevokesSessionsWhenEmployerFeaturePermissionChanges() {
        User employerUser = User.builder().id(11L).username("employer-admin")
                .email("employer@example.com")
                .userType("EMPLOYER_ADMIN")
                .employerId(100L)
                .active(true)
                .canViewMembers(true)
                .build();
        com.waad.tba.modules.employer.entity.Employer employer =
                com.waad.tba.modules.employer.entity.Employer.builder().id(100L).name("جهة اختبار").active(true).build();
        when(userRepository.findById(11L)).thenReturn(Optional.of(employerUser));
        when(employerRepository.findById(100L)).thenReturn(Optional.of(employer));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userMapper.toResponseDto(any())).thenReturn(com.waad.tba.modules.rbac.dto.UserResponseDto.builder().build());
        doAnswer(inv -> {
            User target = inv.getArgument(0);
            com.waad.tba.modules.rbac.dto.UserUpdateDto dto = inv.getArgument(1);
            target.setEmail(dto.getEmail());
            target.setCanViewMembers(dto.getCanViewMembers());
            return null;
        }).when(userMapper).updateEntityFromDto(any(), any());

        com.waad.tba.modules.rbac.dto.UserUpdateDto dto = com.waad.tba.modules.rbac.dto.UserUpdateDto.builder()
                .username("employer-admin")
                .email("employer@example.com")
                .userType("EMPLOYER_ADMIN")
                .employerId(100L)
                .active(true)
                .canViewMembers(false)
                .build();

        userService.update(11L, dto);

        verify(sessionManagementService).revokeAll("employer-admin");
    }
}
