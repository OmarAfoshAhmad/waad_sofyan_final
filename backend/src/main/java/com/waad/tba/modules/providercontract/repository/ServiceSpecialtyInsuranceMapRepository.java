package com.waad.tba.modules.providercontract.repository;

import com.waad.tba.modules.providercontract.entity.ServiceSpecialtyInsuranceMap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ServiceSpecialtyInsuranceMapRepository extends JpaRepository<ServiceSpecialtyInsuranceMap, Long> {

    @Query("SELECT m FROM ServiceSpecialtyInsuranceMap m " +
           "WHERE m.isActive = true " +
           "AND (m.provider.id = :providerId OR m.provider IS NULL) " +
           "ORDER BY m.priority ASC")
    List<ServiceSpecialtyInsuranceMap> findActiveByProviderOrGlobal(Long providerId);

    /**
     * Only for permanent deletion of the contract itself. A contract-scoped
     * mapping row describes how that contract's services classify; it has no
     * meaning once the contract is erased. Global rows carry no contract and are
     * untouched by this.
     */
    @Modifying
    @Query("DELETE FROM ServiceSpecialtyInsuranceMap m WHERE m.contract.id = :contractId")
    int hardDeleteByContractId(@Param("contractId") Long contractId);

}
