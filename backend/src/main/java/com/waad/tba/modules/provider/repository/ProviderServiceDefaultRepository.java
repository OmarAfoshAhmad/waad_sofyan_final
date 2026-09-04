package com.waad.tba.modules.provider.repository;

import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.provider.entity.ProviderServiceDefault;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProviderServiceDefaultRepository extends JpaRepository<ProviderServiceDefault, Long> {

    List<ProviderServiceDefault> findByProviderTypeAndActiveTrueAndAutoApplyTrueOrderBySortOrder(
            Provider.ProviderType providerType);

    List<ProviderServiceDefault> findByActiveTrueOrderByProviderTypeAscSortOrderAsc();

    /** All rows (active and inactive) for one service -- for reconciling defaults on update. */
    List<ProviderServiceDefault> findByServiceCode(String serviceCode);

    /** Active rows for one service -- what a fresh create/list should report as its defaults. */
    List<ProviderServiceDefault> findByServiceCodeAndActiveTrue(String serviceCode);

    /** Bulk form of the above, for listing many services' defaults in one query. */
    List<ProviderServiceDefault> findByServiceCodeInAndActiveTrue(List<String> serviceCodes);
}
