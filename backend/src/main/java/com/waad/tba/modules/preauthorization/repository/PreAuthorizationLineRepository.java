package com.waad.tba.modules.preauthorization.repository;

import com.waad.tba.modules.preauthorization.entity.PreAuthorizationLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for PreAuthorizationLine entity.
 * يُمكّن قرارات المراجعة على مستوى سطر الخدمة.
 */
@Repository
public interface PreAuthorizationLineRepository extends JpaRepository<PreAuthorizationLine, Long> {

    /**
     * جلب جميع سطور موافقة مسبقة محددة.
     */
    List<PreAuthorizationLine> findByPreAuthorizationId(Long preAuthId);

    /**
     * جلب سطر محدد من موافقة محددة (للتحقق من الملكية).
     */
    Optional<PreAuthorizationLine> findByIdAndPreAuthorizationId(Long lineId, Long preAuthId);

    /**
     * عدد السطور ذات الحالة PENDING في موافقة معينة.
     */
    @Query("SELECT COUNT(l) FROM PreAuthorizationLine l " +
           "WHERE l.preAuthorization.id = :preAuthId " +
           "AND l.decisionStatus = com.waad.tba.modules.preauthorization.entity.PreAuthorizationLine$LineDecisionStatus.PENDING")
    long countPendingLines(@Param("preAuthId") Long preAuthId);
}
