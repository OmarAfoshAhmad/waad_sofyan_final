package com.waad.tba.modules.claim.service;

import com.waad.tba.modules.claim.entity.ClaimRejectionReason;
import com.waad.tba.modules.claim.repository.ClaimRejectionReasonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ClaimRejectionReasonService {

    private final ClaimRejectionReasonRepository repository;

    @Transactional(readOnly = true)
    public List<ClaimRejectionReason> getAll() {
        return repository.findByActiveTrueOrderByReasonTextAsc();
    }

    @Transactional
    public ClaimRejectionReason findOrCreate(String reasonText) {
        String trimmed = reasonText.trim();
        return repository.findByReasonText(trimmed)
                .orElseGet(() -> repository.save(
                        ClaimRejectionReason.builder()
                                .reasonText(trimmed)
                                .active(true)
                                .build()));
    }

    @Transactional
    public ClaimRejectionReason update(Long id, String reasonText) {
        ClaimRejectionReason reason = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Rejection reason not found: " + id));
        reason.setReasonText(reasonText.trim());
        return repository.save(reason);
    }

    @Transactional
    public void delete(Long id) {
        ClaimRejectionReason reason = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Rejection reason not found: " + id));
        reason.setActive(false);
        repository.save(reason);
    }
}
