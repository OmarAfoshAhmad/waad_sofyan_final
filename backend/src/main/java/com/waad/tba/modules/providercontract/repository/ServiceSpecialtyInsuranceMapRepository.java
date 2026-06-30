package com.waad.tba.modules.providercontract.repository;

import com.waad.tba.modules.providercontract.entity.ServiceSpecialtyInsuranceMap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ServiceSpecialtyInsuranceMapRepository extends JpaRepository<ServiceSpecialtyInsuranceMap, Long> {

    @Query("SELECT m FROM ServiceSpecialtyInsuranceMap m " +
           "WHERE m.isActive = true " +
           "AND (m.provider.id = :providerId OR m.provider IS NULL) " +
           "ORDER BY m.priority ASC")
    List<ServiceSpecialtyInsuranceMap> findActiveByProviderOrGlobal(Long providerId);

}
