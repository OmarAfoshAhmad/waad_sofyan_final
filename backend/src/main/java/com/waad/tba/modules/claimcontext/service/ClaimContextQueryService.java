package com.waad.tba.modules.claimcontext.service;

import com.waad.tba.modules.claimcontext.repository.ClaimContextDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Reading the claim contexts a screen may offer.
 *
 * <p>Exists because the controller was calling the repository directly, which
 * the engineering constitution prohibits (Controller → Repository). The list is
 * trivial today; the point is that the next thing this endpoint needs -- a
 * scope, a filter, a cache -- has somewhere to live that is not the controller.
 */
@Service
@RequiredArgsConstructor
public class ClaimContextQueryService {

    private final ClaimContextDefinitionRepository repository;

    @Transactional(readOnly = true)
    public List<ClaimContextView> listActive() {
        return repository.findByActiveTrueOrderByDisplayOrderAscCodeAsc().stream()
                .map(context -> new ClaimContextView(
                        context.getCode(),
                        context.getNameAr(),
                        context.getBaseEncounterType().name()))
                .toList();
    }

    public record ClaimContextView(String code, String nameAr, String baseEncounterType) {}
}
