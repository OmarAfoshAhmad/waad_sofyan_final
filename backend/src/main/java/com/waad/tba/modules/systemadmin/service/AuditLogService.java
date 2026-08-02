package com.waad.tba.modules.systemadmin.service;

import com.waad.tba.modules.audit.enums.AuditAction;
import com.waad.tba.modules.audit.enums.AuditSource;
import com.waad.tba.modules.audit.enums.EntityType;
import com.waad.tba.modules.audit.service.AuditLogWriteRequest;
import com.waad.tba.modules.audit.service.MedicalAuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Compatibility facade for legacy system-admin audit calls.
 *
 * The canonical audit trail is now {@code medical_audit_logs}. Keeping this
 * facade avoids scattering old table writes across modules while existing
 * services are gradually renamed to the canonical audit API.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AuditLogService {

    private final MedicalAuditLogService medicalAuditLogService;

    @Transactional
    public void createAuditLog(String action, String entityType, Long entityId,
                               String details, Long userId, String username,
                               String ipAddress, String userAgent) {
        EntityType canonicalEntityType = toEntityType(entityType);
        AuditAction canonicalAction = toAction(action);

        Map<String, Object> afterState = new LinkedHashMap<>();
        afterState.put("legacyAction", action);
        afterState.put("legacyEntityType", entityType);
        afterState.put("details", details);
        afterState.put("username", username);
        afterState.put("ipAddress", ipAddress);
        afterState.put("userAgent", userAgent);

        medicalAuditLogService.record(AuditLogWriteRequest.builder()
                .entityType(canonicalEntityType)
                .entityId(entityId == null ? "N/A" : String.valueOf(entityId))
                .action(canonicalAction)
                .reason(details)
                .afterState(afterState)
                .source(AuditSource.USER)
                .version(1)
                .build());

        log.debug("Canonical audit log created via legacy facade: {} {} by {}", action, entityType, username);
    }

    private AuditAction toAction(String action) {
        String normalized = normalize(action);
        return switch (normalized) {
            case "VIEW", "VIEWED" -> AuditAction.VIEW;
            case "CREATE", "CREATED", "FEATURE_FLAG_CREATED" -> AuditAction.CREATED;
            case "UPDATE", "UPDATED", "FEATURE_FLAG_UPDATED", "UPDATE_REVIEWER_PROVIDER_ASSIGNMENTS",
                    "FEATURE_FLAG_TOGGLED" -> AuditAction.UPDATED;
            case "DELETE", "DELETED", "FEATURE_FLAG_DELETED" -> AuditAction.DELETED;
            case "RESTORE", "RESTORED" -> AuditAction.RESTORED;
            case "ACTIVATE", "ACTIVATED" -> AuditAction.ACTIVATED;
            case "SUSPEND", "SUSPENDED" -> AuditAction.SUSPENDED;
            case "TERMINATE", "TERMINATED" -> AuditAction.TERMINATED;
            case "APPROVE", "APPROVED" -> AuditAction.APPROVED;
            case "REJECT", "REJECTED" -> AuditAction.REJECTED;
            case "IMPORT", "IMPORTED" -> AuditAction.IMPORTED;
            case "EXPORT", "EXPORTED" -> AuditAction.EXPORTED;
            default -> AuditAction.MANUAL_OVERRIDE;
        };
    }

    private EntityType toEntityType(String entityType) {
        String normalized = normalize(entityType);
        return switch (normalized) {
            case "CLAIM" -> EntityType.CLAIM;
            case "CLAIM_LINE", "CLAIMLINE" -> EntityType.CLAIM_LINE;
            case "PREAUTHORIZATION", "PRE_AUTHORIZATION", "PREAUTH" -> EntityType.PREAUTHORIZATION;
            case "SETTLEMENT" -> EntityType.SETTLEMENT;
            case "MEMBER" -> EntityType.MEMBER;
            case "VISIT" -> EntityType.VISIT;
            case "PROVIDER" -> EntityType.PROVIDER;
            case "PROVIDERCONTRACT", "PROVIDER_CONTRACT" -> EntityType.PROVIDER_CONTRACT;
            case "MEDICALREVIEWERPROVIDER", "MEDICAL_REVIEWER_PROVIDER" -> EntityType.MEDICAL_REVIEWER_PROVIDER;
            case "FEATUREFLAG", "FEATURE_FLAG" -> EntityType.FEATURE_FLAG;
            case "EMPLOYER" -> EntityType.EMPLOYER;
            case "EMPLOYERCONTRACT", "EMPLOYER_CONTRACT" -> EntityType.EMPLOYER_CONTRACT;
            case "PRICELIST", "PRICE_LIST" -> EntityType.PRICE_LIST;
            case "MEDICALDICTIONARY", "MEDICAL_DICTIONARY" -> EntityType.MEDICAL_DICTIONARY;
            default -> EntityType.SYSTEM_SETTING;
        };
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.trim()
                .replace('-', '_')
                .replace(' ', '_')
                .toUpperCase(Locale.ROOT);
    }
}
