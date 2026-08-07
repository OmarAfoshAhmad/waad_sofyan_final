package com.waad.tba.modules.settlement.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.waad.tba.modules.settlement.entity.ProviderPaymentAllocation;

public interface ProviderPaymentAllocationRepository
        extends JpaRepository<ProviderPaymentAllocation, Long> {
}
