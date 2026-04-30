package com.waad.tba.modules.claim.draft.service;

import com.waad.tba.common.exception.ResourceNotFoundException;
import com.fasterxml.jackson.databind.JsonNode;
import com.waad.tba.modules.claim.draft.dto.ClaimDraftResponse;
import com.waad.tba.modules.claim.draft.dto.ClaimDraftUpsertRequest;
import com.waad.tba.modules.claim.draft.entity.ClaimDraft;
import com.waad.tba.modules.claim.draft.repository.ClaimDraftRepository;
import com.waad.tba.modules.claim.repository.ClaimBatchRepository;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.security.AuthorizationService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ClaimDraftService {

    private final ClaimDraftRepository claimDraftRepository;
    private final ClaimBatchRepository claimBatchRepository;
    private final AuthorizationService authorizationService;

    @Transactional(readOnly = true)
    public Optional<ClaimDraftResponse> getDraft(Long batchId) {
        User currentUser = requireCurrentUser();
        return claimDraftRepository.findByUserIdAndBatchId(currentUser.getId(), batchId)
                .map(d -> toResponse(d, false));
    }

    @Transactional
    public ClaimDraftResponse upsertDraft(ClaimDraftUpsertRequest request) {
        User currentUser = requireCurrentUser();

        claimBatchRepository.findById(request.getBatchId())
                .orElseThrow(() -> new ResourceNotFoundException("ClaimBatch", "id", request.getBatchId()));

        ClaimDraft draft = claimDraftRepository.findByUserIdAndBatchId(currentUser.getId(), request.getBatchId())
                .orElseGet(() -> ClaimDraft.builder()
                        .userId(currentUser.getId())
                        .batchId(request.getBatchId())
                        .status("DRAFT")
                        .version(1L)
                        .build());

        boolean conflictResolved = false;
        if (draft.getId() != null && request.getVersion() != null && !request.getVersion().equals(draft.getVersion())) {
            conflictResolved = true;
        }

        draft.setDataJson(request.getData());
        draft.setStatus("DRAFT");
        if (draft.getId() == null) {
            draft.setVersion(1L);
        } else {
            draft.setVersion(draft.getVersion() + 1);
        }

        ClaimDraft saved = claimDraftRepository.save(draft);
        return toResponse(saved, conflictResolved);
    }

    @Transactional
    public void deleteDraft(Long batchId) {
        User currentUser = requireCurrentUser();
        claimDraftRepository.deleteByUserIdAndBatchId(currentUser.getId(), batchId);
    }

    private User requireCurrentUser() {
        User currentUser = authorizationService.getCurrentUser();
        if (currentUser == null) {
            throw new AccessDeniedException("Authentication required");
        }
        return currentUser;
    }

    private ClaimDraftResponse toResponse(ClaimDraft draft, boolean conflictResolved) {
        return ClaimDraftResponse.builder()
                .id(draft.getId())
                .userId(draft.getUserId())
                .batchId(draft.getBatchId())
                .status(draft.getStatus())
                .data(draft.getDataJson())
                .version(draft.getVersion())
                .updatedAt(draft.getUpdatedAt())
                .conflictResolved(conflictResolved)
                .build();
    }
}
