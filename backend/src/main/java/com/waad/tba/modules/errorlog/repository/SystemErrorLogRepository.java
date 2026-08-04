package com.waad.tba.modules.errorlog.repository;

import com.waad.tba.modules.errorlog.entity.SystemErrorLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SystemErrorLogRepository
        extends JpaRepository<SystemErrorLog, Long>, JpaSpecificationExecutor<SystemErrorLog> {

    long countByResolvedFalse();
}
