package com.waad.tba.modules.audit.service;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CorrelationIdProvider {

    private static final String CORRELATION_ID = "correlationId";
    private static final String TRACE_ID = "traceId";

    public String getOrCreate() {
        String correlationId = MDC.get(CORRELATION_ID);
        if (correlationId != null && !correlationId.isBlank()) {
            return correlationId;
        }

        String traceId = MDC.get(TRACE_ID);
        if (traceId != null && !traceId.isBlank()) {
            return traceId;
        }

        return UUID.randomUUID().toString();
    }
}
