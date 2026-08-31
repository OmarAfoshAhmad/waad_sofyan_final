package com.waad.tba.modules.claimcontext.service;

import com.waad.tba.modules.claimcontext.repository.ClaimContextSourceAliasRepository;
import com.waad.tba.modules.medicaldictionary.service.MedicalDictionaryNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClaimContextSourceResolver {
    private final ClaimContextSourceAliasRepository aliasRepository;
    private final MedicalDictionaryNormalizer normalizer;

    @Transactional(readOnly = true)
    public Optional<Resolution> resolve(String sourceClassification, Long providerId) {
        String normalized = normalizer.normalize(sourceClassification);
        if (normalized.isBlank()) return Optional.empty();
        return aliasRepository.resolveCandidates(normalized, providerId).stream().findFirst()
                .map(alias -> new Resolution(alias.getClaimContext().getCode(),
                        alias.getMedicalCategoryCode(), alias.isRequiresReview()));
    }

    @Transactional(readOnly = true)
    public Map<String, Resolution> resolveAll(Collection<String> sourceClassifications, Long providerId) {
        Set<String> normalized = sourceClassifications.stream()
                .map(normalizer::normalize)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toSet());
        if (normalized.isEmpty()) return Map.of();

        Map<String, Resolution> result = new LinkedHashMap<>();
        for (var alias : aliasRepository.resolveCandidatesBulk(normalized, providerId)) {
            result.putIfAbsent(alias.getNormalizedAlias(), new Resolution(
                    alias.getClaimContext().getCode(), alias.getMedicalCategoryCode(), alias.isRequiresReview()));
        }
        return result;
    }

    public record Resolution(String claimContextCode, String medicalCategoryCode, boolean requiresReview) {}
}
