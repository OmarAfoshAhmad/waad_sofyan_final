package com.waad.tba.modules.medicaldictionary.repository;

import com.waad.tba.modules.medicaldictionary.entity.PriceListClassificationSession;
import com.waad.tba.modules.medicaldictionary.enums.PriceListSessionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceListClassificationSessionRepository extends JpaRepository<PriceListClassificationSession, Long> {
    Page<PriceListClassificationSession> findByStatus(PriceListSessionStatus status, Pageable pageable);

    java.util.Optional<PriceListClassificationSession> findFirstBySourceFingerprintOrderByUpdatedAtDesc(String sourceFingerprint);
}
