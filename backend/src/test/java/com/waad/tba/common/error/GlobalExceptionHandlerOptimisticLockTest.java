package com.waad.tba.common.error;

import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Concurrent-edit conflicts on @Version-ed entities (e.g. ProviderContract)
 * must surface as a clear 409 + Arabic message, not a raw 500 exposing the
 * Hibernate exception to the client.
 */
class GlobalExceptionHandlerOptimisticLockTest {

    @Test
    void optimisticLockConflictMapsToHttp409WithArabicMessage() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler(Optional.empty());
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/provider-contracts/42");

        ObjectOptimisticLockingFailureException ex =
                new ObjectOptimisticLockingFailureException("ProviderContract", 42L);

        ResponseEntity<ApiError> response = handler.handleOptimisticLock(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).contains("تم تعديل هذا العنصر من قبل مستخدم آخر");
    }
}
