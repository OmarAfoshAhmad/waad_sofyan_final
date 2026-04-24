package com.waad.tba.modules.preauthorization.repository;

import com.waad.tba.modules.preauthorization.entity.PreAuthEmailRequest;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

@Repository
public interface PreAuthEmailRequestRepository extends JpaRepository<PreAuthEmailRequest, Long> {
    Optional<PreAuthEmailRequest> findByMessageId(String messageId);

    boolean existsByMessageId(String messageId);

    @Override
    @EntityGraph(attributePaths = { "provider", "member", "attachments" })
    Optional<PreAuthEmailRequest> findById(Long id);

    @EntityGraph(attributePaths = { "provider", "member", "attachments" })
    Page<PreAuthEmailRequest> findByProcessed(Boolean processed, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = { "provider", "member", "attachments" })
    Page<PreAuthEmailRequest> findAll(Pageable pageable);
}
