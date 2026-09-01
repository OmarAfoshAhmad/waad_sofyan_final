package com.waad.tba.modules.providercontract.repository;

import com.waad.tba.modules.providercontract.entity.ProviderContractTerm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Optional;

public interface ProviderContractTermRepository extends JpaRepository<ProviderContractTerm, Long> {
    @Query("SELECT t FROM ProviderContractTerm t WHERE t.contract.id = :contractId " +
           "AND t.effectiveFrom <= :date AND (t.effectiveTo IS NULL OR :date < t.effectiveTo)")
    Optional<ProviderContractTerm> findEffective(@Param("contractId") Long contractId, @Param("date") LocalDate date);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM ProviderContractTerm t WHERE t.contract.id = :contractId AND t.effectiveTo IS NULL")
    Optional<ProviderContractTerm> findOpenForUpdate(@Param("contractId") Long contractId);

    /**
     * Only for permanent deletion of the contract itself. These rows are the
     * contract's own dated terms and carry no meaning without it; every other
     * path amends them rather than removing them.
     */
    @Modifying
    @Query("DELETE FROM ProviderContractTerm t WHERE t.contract.id = :contractId")
    int hardDeleteByContractId(@Param("contractId") Long contractId);
}
