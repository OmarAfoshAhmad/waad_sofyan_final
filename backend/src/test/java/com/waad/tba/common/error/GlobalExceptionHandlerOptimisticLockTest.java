package com.waad.tba.common.error;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

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
                new RuntimeException("org.postgresql.util.PSQLException: secret internal detail"),
                request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getDetails()).isInstanceOfSatisfying(Map.class, rawDetails -> {
            Map<?, ?> details = rawDetails;
            assertThat(details.containsKey("reference")).isTrue();
            assertThat(details.containsKey("exception")).isFalse();
            assertThat(details.containsKey("reason")).isFalse();
        });
    }
}
