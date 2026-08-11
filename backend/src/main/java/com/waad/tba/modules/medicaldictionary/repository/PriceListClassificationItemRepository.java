package com.waad.tba.modules.medicaldictionary.repository;

import com.waad.tba.modules.medicaldictionary.entity.PriceListClassificationItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface PriceListClassificationItemRepository extends JpaRepository<PriceListClassificationItem, Long> {
    List<PriceListClassificationItem> findBySession_IdOrderByRowNumberAscIdAsc(Long sessionId);
    List<PriceListClassificationItem> findByPostedPricingItemIdIn(Collection<Long> pricingItemIds);
    long countBySession_Id(Long sessionId);
}
