package com.waad.tba.modules.visit.scheduler;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.modules.visit.entity.Visit;
import com.waad.tba.modules.visit.entity.VisitStatus;
import com.waad.tba.modules.visit.repository.VisitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class VisitScheduler {

    private final VisitRepository visitRepository;

    /**
     * Runs every hour to check for open visits older than 24 hours
     * and automatically cancels/closes them to prevent deadlocks.
     */
    @Scheduled(cron = "0 0 * * * *") // Run at minute 0 of every hour
    @Transactional
    public void autoCloseOldVisits() {
        log.info("🕒 [VISIT SCHEDULER] Running auto-close for old open visits...");
        
        List<VisitStatus> openStatuses = Arrays.asList(
            VisitStatus.REGISTERED, 
            VisitStatus.IN_PROGRESS, 
            VisitStatus.PENDING_PREAUTH, 
            VisitStatus.PREAUTH_APPROVED
        );
        
        LocalDateTime twentyFourHoursAgo = LocalDateTime.now().minusHours(24);
        
        // Find open visits created before 24 hours ago
        // Actually since we don't have createdAt in the query directly, we can fetch all open visits and filter
        // Wait, Visit extends AuditableEntity which has createdAt
        
        // For simplicity since it's a scheduled job, we can just get all open visits and filter
        // A better approach is to add a query, but let's do it safely
        List<Visit> allVisits = visitRepository.findAll();
        int closedCount = 0;
        
        for (Visit visit : allVisits) {
            if (openStatuses.contains(visit.getStatus()) && visit.getCreatedAt() != null) {
                if (visit.getCreatedAt().isBefore(twentyFourHoursAgo)) {
                    visit.setStatus(VisitStatus.CANCELLED);
                    visit.setNotes(visit.getNotes() != null ? visit.getNotes() + "\n(Auto-closed by system due to 24h inactivity)" : "(Auto-closed by system due to 24h inactivity)");
                    visitRepository.save(visit);
                    closedCount++;
                }
            }
        }
        
        if (closedCount > 0) {
            log.info("✅ [VISIT SCHEDULER] Auto-closed {} abandoned visits.", closedCount);
        } else {
            log.info("✅ [VISIT SCHEDULER] No abandoned visits found.");
        }
    }
}
