package com.waad.tba.modules.preauthorization.repository;

import com.waad.tba.modules.preauthorization.entity.PreAuthEmailAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PreAuthEmailAttachmentRepository extends JpaRepository<PreAuthEmailAttachment, Long> {
}
