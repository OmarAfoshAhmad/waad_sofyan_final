package com.waad.tba.modules.claim.ruleengine.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waad.tba.modules.claim.ruleengine.entity.ClaimCoverageRule;
import com.waad.tba.modules.claim.ruleengine.model.RuleGroup;
import com.waad.tba.modules.claim.ruleengine.model.RuleType;
import com.waad.tba.modules.claim.ruleengine.repository.ClaimCoverageRuleRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/claim-coverage-rules")
@RequiredArgsConstructor
@Tag(name = "Admin - Claim Coverage Rules", description = "Manage dynamic financial coverage rules")
@PreAuthorize("isAuthenticated()")
public class ClaimCoverageRuleAdminController {

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ClaimCoverageRuleRepository ruleRepository;
    private final ObjectMapper objectMapper;

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Get all claim coverage rules")
    public ResponseEntity<List<ClaimCoverageRuleDto>> getAll() {
        List<ClaimCoverageRuleDto> rows = ruleRepository.findAll().stream()
                .sorted(Comparator
                        .comparing((ClaimCoverageRule r) -> r.getRuleGroup(),
                                Comparator.nullsLast(Comparator.comparing(RuleGroup::getOrder)))
                        .thenComparing(ClaimCoverageRule::getPriority, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ClaimCoverageRule::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::toDto)
                .toList();
        return ResponseEntity.ok(rows);
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Create claim coverage rule")
    public ResponseEntity<ClaimCoverageRuleDto> create(@RequestBody UpsertClaimCoverageRuleRequest request) {
        ClaimCoverageRule entity = new ClaimCoverageRule();
        applyRequest(entity, request);
        ClaimCoverageRule saved = ruleRepository.save(entity);
        return ResponseEntity.ok(toDto(saved));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Update claim coverage rule")
    public ResponseEntity<ClaimCoverageRuleDto> update(
            @PathVariable("id") Long id,
            @RequestBody UpsertClaimCoverageRuleRequest request) {

        return ruleRepository.findById(id)
                .map(entity -> {
                    applyRequest(entity, request);
                    ClaimCoverageRule saved = ruleRepository.save(entity);
                    return ResponseEntity.ok(toDto(saved));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Delete claim coverage rule")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        if (!ruleRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        ruleRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private void applyRequest(ClaimCoverageRule entity, UpsertClaimCoverageRuleRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("Rule name is required");
        }
        if (request.type() == null) {
            throw new IllegalArgumentException("Rule type is required");
        }
        if (request.ruleGroup() == null) {
            throw new IllegalArgumentException("Rule group is required");
        }
        if (request.priority() == null) {
            throw new IllegalArgumentException("Rule priority is required");
        }

        entity.setName(request.name().trim());
        entity.setType(request.type());
        entity.setRuleGroup(request.ruleGroup());
        entity.setPriority(request.priority());
        entity.setEnabled(Boolean.TRUE.equals(request.enabled()));

        String dependenciesJson = writeJson(request.dependencyRules() == null ? List.of() : request.dependencyRules());
        String configurationJson = writeJson(request.configuration() == null ? Map.of() : request.configuration());

        entity.setDependencyRules(dependenciesJson);
        entity.setConfiguration(configurationJson);
    }

    private ClaimCoverageRuleDto toDto(ClaimCoverageRule entity) {
        return new ClaimCoverageRuleDto(
                entity.getId(),
                entity.getName(),
                entity.getType(),
                entity.getPriority(),
                entity.isEnabled(),
                entity.getRuleGroup(),
                parseDependencies(entity.getDependencyRules()),
                parseConfig(entity.getConfiguration()));
    }

    private List<String> parseDependencies(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST_TYPE);
        } catch (Exception ex) {
            log.warn("Invalid dependency_rules JSON: {}", json, ex);
            return List.of();
        }
    }

    private Map<String, Object> parseConfig(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(json, MAP_TYPE);
            return parsed == null ? Map.of() : parsed;
        } catch (Exception ex) {
            log.warn("Invalid configuration JSON: {}", json, ex);
            return Map.of();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? new LinkedHashMap<>() : value);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid JSON payload for rule", ex);
        }
    }

    public record ClaimCoverageRuleDto(
            Long id,
            String name,
            RuleType type,
            Integer priority,
            Boolean enabled,
            RuleGroup ruleGroup,
            List<String> dependencyRules,
            Map<String, Object> configuration) {
    }

    public record UpsertClaimCoverageRuleRequest(
            String name,
            RuleType type,
            Integer priority,
            Boolean enabled,
            RuleGroup ruleGroup,
            List<String> dependencyRules,
            Map<String, Object> configuration) {
    }
}
