package com.waad.tba.modules.systemadmin.repository;

import com.waad.tba.modules.systemadmin.entity.EmailSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EmailSettingsRepository extends JpaRepository<EmailSettings, Long> {
    
    Optional<EmailSettings> findFirstByIsActiveTrueOrderByIdDesc();
}
