package com.waad.tba.modules.settlement.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.waad.tba.modules.settlement.entity.ProviderAccountReconciliationAudit;

public interface ProviderAccountReconciliationAuditRepository
        extends JpaRepository<ProviderAccountReconciliationAudit, Long> {

    List<ProviderAccountReconciliationAudit> findByProviderIdOrderByCreatedAtDesc(Long providerId);
}
