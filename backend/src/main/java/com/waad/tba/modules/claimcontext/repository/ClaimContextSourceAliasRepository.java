package com.waad.tba.modules.claimcontext.repository;

import com.waad.tba.modules.claimcontext.entity.ClaimContextSourceAlias;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Collection;

public interface ClaimContextSourceAliasRepository extends JpaRepository<ClaimContextSourceAlias, Long> {
    @Query("""
        select a from ClaimContextSourceAlias a join fetch a.claimContext c
        where a.active = true and c.active = true and a.normalizedAlias = :alias
          and (a.providerId = :providerId or a.providerId is null)
        order by case when a.providerId = :providerId then 0 else 1 end
        """)
    List<ClaimContextSourceAlias> resolveCandidates(@Param("alias") String alias,
                                                     @Param("providerId") Long providerId);

    @Query("""
        select a from ClaimContextSourceAlias a join fetch a.claimContext c
        where a.active = true and c.active = true and a.normalizedAlias in :aliases
          and (a.providerId = :providerId or a.providerId is null)
        order by a.normalizedAlias,
                 case when a.providerId = :providerId then 0 else 1 end
        """)
    List<ClaimContextSourceAlias> resolveCandidatesBulk(@Param("aliases") Collection<String> aliases,
                                                         @Param("providerId") Long providerId);
}
