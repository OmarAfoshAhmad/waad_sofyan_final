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
}
