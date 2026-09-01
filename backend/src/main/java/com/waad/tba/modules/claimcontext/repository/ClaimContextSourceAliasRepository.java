package com.waad.tba.modules.claimcontext.repository;

import com.waad.tba.modules.claimcontext.entity.ClaimContextSourceAlias;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Collection;

public interface ClaimContextSourceAliasRepository extends JpaRepository<ClaimContextSourceAlias, Long> {

    /**
     * The exact row a provider-scoped confirmation updates. Deliberately matches
     * on the normalized alias and this provider only: the unique index is over
     * (normalized_alias, COALESCE(provider_id, 0)), so a confirmation for one
     * provider must never collide with, or overwrite, the global row.
     */
    java.util.Optional<ClaimContextSourceAlias> findByNormalizedAliasAndProviderId(
            String normalizedAlias, Long providerId);

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
