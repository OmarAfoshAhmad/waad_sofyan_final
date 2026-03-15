package com.waad.tba.modules.claim.repository;

import com.waad.tba.modules.claim.entity.ClaimRejectionReason;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClaimRejectionReasonRepository extends JpaRepository<ClaimRejectionReason, Long> {
    List<ClaimRejectionReason> findByActiveTrueOrderByReasonTextAsc();

    Optional<ClaimRejectionReason> findByReasonText(String reasonText);
}
