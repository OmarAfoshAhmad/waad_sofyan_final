package com.waad.tba.modules.providercontract.scheduler;

import com.waad.tba.modules.providercontract.service.ProviderContractService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler for Provider Contracts.
 * Auto-expires active contracts when they pass their end date.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProviderContractScheduler {

    private final ProviderContractService providerContractService;

    /**
     * Run daily at 1:00 AM (server time).
     */
    @Scheduled(cron = "0 0 1 * * ?")
    public void autoExpireContracts() {
        log.info("[ProviderContractScheduler] Starting auto-expiration of contracts...");
        int expiredCount = providerContractService.markExpiredContracts();
        log.info("[ProviderContractScheduler] Auto-expired {} contracts.", expiredCount);
    }
}
