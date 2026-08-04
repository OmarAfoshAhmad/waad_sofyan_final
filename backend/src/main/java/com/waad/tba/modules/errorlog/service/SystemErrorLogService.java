package com.waad.tba.modules.errorlog.service;

import com.waad.tba.modules.errorlog.dto.ErrorLogDtos.ErrorLogDetailDto;
import com.waad.tba.modules.errorlog.dto.ErrorLogDtos.ErrorLogRowDto;
import com.waad.tba.modules.errorlog.entity.ErrorLogSeverity;
import com.waad.tba.modules.errorlog.entity.ErrorLogSource;
import com.waad.tba.modules.errorlog.entity.SystemErrorLog;
import com.waad.tba.modules.errorlog.repository.SystemErrorLogRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Central store for backend + frontend error events surfaced to users.
 * Recording is best-effort and must never break the request that produced the error.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SystemErrorLogService {

    private final SystemErrorLogRepository repository;

    // Redact anything that looks like a secret before persisting technical text.
    private static final Pattern SENSITIVE = Pattern.compile(
            "(?i)(password|passwd|pwd|token|secret|authorization|bearer|api[_-]?key|jwt|cookie|set-cookie)"
                    + "\\s*[=:]\\s*\\S+");

    /**
     * Persist an error event. Never throws — a failure here must not mask the original error.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(SystemErrorLog event) {
        try {
            if (event.getOccurredAt() == null) {
                event.setOccurredAt(LocalDateTime.now());
            }
            if (event.getSeverity() == null) {
                event.setSeverity(ErrorLogSeverity.ERROR);
            }
            event.setUserMessage(truncate(event.getUserMessage(), 1000));
            event.setTechnicalMessage(truncate(redact(event.getTechnicalMessage()), 2000));
            event.setStackExcerpt(truncate(redact(event.getStackExcerpt()), 4000));
            event.setNotes(truncate(event.getNotes(), 2000));
            event.setPath(truncate(event.getPath(), 500));
            event.setFrontendRoute(truncate(event.getFrontendRoute(), 500));
            event.setBrowser(truncate(event.getBrowser(), 300));
            if (event.getStackHash() == null && event.getStackExcerpt() != null) {
                event.setStackHash(hash(event.getStackExcerpt()));
            }
            repository.save(event);
        } catch (Exception e) {
            log.warn("[MON-BKP-LOG-1] Failed to persist error event: {}", e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public Page<ErrorLogRowDto> list(ErrorLogSource source, ErrorLogSeverity severity, Boolean resolved,
                                     Integer statusCode, String path, String username,
                                     LocalDateTime from, LocalDateTime to, Pageable pageable) {
        Specification<SystemErrorLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (source != null) {
                predicates.add(cb.equal(root.get("source"), source));
            }
            if (severity != null) {
                predicates.add(cb.equal(root.get("severity"), severity));
            }
            if (resolved != null) {
                predicates.add(cb.equal(root.get("resolved"), resolved));
            }
            if (statusCode != null) {
                predicates.add(cb.equal(root.get("statusCode"), statusCode));
            }
            if (path != null && !path.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("path")), "%" + path.toLowerCase() + "%"));
            }
            if (username != null && !username.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("username")), "%" + username.toLowerCase() + "%"));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("occurredAt"), to));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return repository.findAll(spec, pageable).map(this::toRow);
    }

    @Transactional(readOnly = true)
    public ErrorLogDetailDto get(Long id) {
        SystemErrorLog e = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Error event not found"));
        return toDetail(e);
    }

    @Transactional
    public ErrorLogDetailDto resolve(Long id, boolean resolved, String notes, String username) {
        SystemErrorLog e = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Error event not found"));
        e.setResolved(resolved);
        if (resolved) {
            e.setResolvedBy(username);
            e.setResolvedAt(LocalDateTime.now());
        } else {
            e.setResolvedBy(null);
            e.setResolvedAt(null);
        }
        if (notes != null) {
            e.setNotes(truncate(notes, 2000));
        }
        return toDetail(repository.save(e));
    }

    @Transactional(readOnly = true)
    public long unresolvedCount() {
        return repository.countByResolvedFalse();
    }

    private ErrorLogRowDto toRow(SystemErrorLog e) {
        return new ErrorLogRowDto(
                e.getId(), e.getOccurredAt(), e.getSource(), e.getSeverity(), e.getEnvironment(),
                e.getCorrelationId(), e.getUsername(), e.getRole(), e.getHttpMethod(), e.getPath(),
                e.getStatusCode(), e.getErrorCode(), e.getUserMessage(), e.getFrontendRoute(),
                e.getResolved(), e.getResolvedBy(), e.getResolvedAt());
    }

    private ErrorLogDetailDto toDetail(SystemErrorLog e) {
        return new ErrorLogDetailDto(
                e.getId(), e.getOccurredAt(), e.getSource(), e.getSeverity(), e.getEnvironment(),
                e.getCorrelationId(), e.getUserId(), e.getUsername(), e.getRole(), e.getHttpMethod(),
                e.getPath(), e.getStatusCode(), e.getErrorCode(), e.getUserMessage(), e.getTechnicalMessage(),
                e.getExceptionClass(), e.getStackExcerpt(), e.getStackHash(), e.getFrontendRoute(),
                e.getBrowser(), e.getResolved(), e.getResolvedBy(), e.getResolvedAt(), e.getNotes());
    }

    static String redact(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        return SENSITIVE.matcher(value).replaceAll("$1=***");
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    private static String hash(String value) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8 && i < digest.length; i++) {
                sb.append(String.format("%02x", digest[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
