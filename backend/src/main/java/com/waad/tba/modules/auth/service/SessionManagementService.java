package com.waad.tba.modules.auth.service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * Central session inventory and revocation backed by Spring Session JDBC.
 */
@Service
@RequiredArgsConstructor
public class SessionManagementService {

    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    public List<SessionInfo> list(String username, String currentSessionId) {
        return sessions(username).values().stream()
                .filter(session -> !session.isExpired())
                .map(session -> new SessionInfo(
                        session.getId(),
                        session.getCreationTime(),
                        session.getLastAccessedTime(),
                        session.getLastAccessedTime().plus(session.getMaxInactiveInterval()),
                        session.getId().equals(currentSessionId)))
                .sorted(Comparator.comparing(SessionInfo::lastAccessedAt).reversed())
                .toList();
    }

    public void revokeOwnedSession(String username, String sessionId) {
        Session owned = sessions(username).get(sessionId);
        if (owned == null) {
            // Do not reveal whether another user's session id exists.
            throw new AccessDeniedException("Session does not belong to the current user");
        }
        sessionRepository.deleteById(sessionId);
    }

    public int revokeAllExcept(String username, String excludedSessionId) {
        int revoked = 0;
        for (Session session : sessions(username).values()) {
            if (!session.getId().equals(excludedSessionId)) {
                sessionRepository.deleteById(session.getId());
                revoked++;
            }
        }
        return revoked;
    }

    public int revokeAll(String username) {
        return revokeAllExcept(username, null);
    }

    private Map<String, ? extends Session> sessions(String username) {
        return sessionRepository.findByPrincipalName(username);
    }

    public record SessionInfo(
            String id,
            Instant createdAt,
            Instant lastAccessedAt,
            Instant expiresAt,
            boolean current) {
    }
}
