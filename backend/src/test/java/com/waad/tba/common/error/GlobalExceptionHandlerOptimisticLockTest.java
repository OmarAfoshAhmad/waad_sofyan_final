package com.waad.tba.common.error;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;

import com.waad.tba.modules.rbac.exception.AccountLockedException;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Concurrent-edit conflicts on @Version-ed entities (e.g. ProviderContract)
 * must surface as a clear 409 + Arabic message, not a raw 500 exposing the
 * Hibernate exception to the client.
 */
class GlobalExceptionHandlerOptimisticLockTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(
            new com.waad.tba.modules.benefitpolicy.service.LedgerConstraintTranslator());

    @Test
    void optimisticLockConflictMapsToHttp409WithArabicMessage() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/provider-contracts/42");

        ObjectOptimisticLockingFailureException ex =
                new ObjectOptimisticLockingFailureException("ProviderContract", 42L);

        ResponseEntity<ApiError> response = handler.handleOptimisticLock(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).contains("تم تعديل هذا العنصر من قبل مستخدم آخر");
    }

    @Test
    void malformedJsonMapsToSafeHttp400WithoutParserDetails() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/claims");

        ResponseEntity<ApiError> response = handler.handleMalformedJson(
                new HttpMessageNotReadableException(
                        "JSON parse error: com.fasterxml.jackson.BadThing",
                        new RuntimeException("parser detail"),
                        new MockHttpInputMessage(new byte[] {})),
                request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR.name());
        assertThat(response.getBody().getMessage()).isEqualTo("Malformed request body.");
        assertThat(response.getBody().getMessageAr()).isEqualTo("صيغة بيانات الطلب غير صحيحة.");
        assertThat(response.getBody().getDetails()).isNull();
    }

    @Test
    void genericErrorDoesNotExposeExceptionClassOrMessage() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/claims");

        ResponseEntity<ApiError> response = handler.handleGeneric(
                new RuntimeException("org.postgresql.util.PSQLException: secret internal detail at com.waad.tba.Service"),
                request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(ErrorCode.INTERNAL_ERROR.name());
        assertThat(response.getBody().getMessage()).isEqualTo("An unexpected error occurred.");
        assertThat(response.getBody().getMessageAr()).isNull();
        assertThat(response.getBody().toString())
                .doesNotContain("PSQLException")
                .doesNotContain("postgresql")
                .doesNotContain("secret internal detail")
                .doesNotContain("com.waad.tba.Service")
                .doesNotContain("RuntimeException")
                .doesNotContain("stackTrace");
        assertThat(response.getBody().getDetails()).isInstanceOfSatisfying(Map.class, rawDetails -> {
            Map<?, ?> details = rawDetails;
            assertThat(details.containsKey("reference")).isTrue();
            assertThat(details.containsKey("exception")).isFalse();
            assertThat(details.containsKey("reason")).isFalse();
        });
    }

    @Test
    void loginFailureResponsesAreUniformToAvoidAccountEnumeration() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/auth/session/login");

        ResponseEntity<ApiError> badCredentials = handler.handleBadCredentials(
                new BadCredentialsException("bad password"),
                request);
        ResponseEntity<ApiError> disabled = handler.handleDisabled(
                new DisabledException("disabled account"),
                request);
        ResponseEntity<ApiError> internalAuth = handler.handleInternalAuth(
                new InternalAuthenticationServiceException("user lookup detail"),
                request);
        ResponseEntity<ApiError> locked = handler.handleAccountLocked(
                new AccountLockedException("known-user@example.com", LocalDateTime.of(2026, 9, 12, 4, 30)),
                request);

        assertUniformLoginFailure(badCredentials);
        assertUniformLoginFailure(disabled);
        assertUniformLoginFailure(internalAuth);
        assertUniformLoginFailure(locked);

        assertThat(locked.getBody()).isNotNull();
        assertThat(locked.getBody().toString())
                .doesNotContain("ACCOUNT_LOCKED")
                .doesNotContain("lockedUntil")
                .doesNotContain("known-user@example.com")
                .doesNotContain("04:30");
    }

    private static void assertUniformLoginFailure(ResponseEntity<ApiError> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS.name());
        assertThat(response.getBody().getMessage()).isEqualTo("Invalid username or password");
        assertThat(response.getBody().getMessageAr()).isEqualTo("اسم المستخدم أو كلمة المرور غير صحيحة");
        assertThat(response.getBody().getDetails()).isNull();
    }
}
