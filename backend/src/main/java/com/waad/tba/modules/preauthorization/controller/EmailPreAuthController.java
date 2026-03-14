package com.waad.tba.modules.preauthorization.controller;

import com.waad.tba.modules.preauthorization.entity.PreAuthEmailRequest;
import com.waad.tba.modules.preauthorization.repository.PreAuthEmailRequestRepository;
import com.waad.tba.modules.preauthorization.service.EmailPreAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/pre-auth/emails")
@RequiredArgsConstructor
public class EmailPreAuthController {

    private final EmailPreAuthService emailPreAuthService;
    private final com.waad.tba.modules.preauthorization.repository.PreAuthEmailRequestRepository emailRequestRepository;
    private final com.waad.tba.modules.preauthorization.service.PreAuthorizationService preAuthorizationService;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER', 'PROVIDER_STAFF')")
    public org.springframework.data.domain.Page<com.waad.tba.modules.preauthorization.dto.PreAuthEmailRequestDto> getAll(@RequestParam(required = false) Boolean processed, org.springframework.data.domain.Pageable pageable) {
        return emailPreAuthService.getAll(processed, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER', 'PROVIDER_STAFF')")
    public com.waad.tba.modules.preauthorization.dto.PreAuthEmailRequestDto getById(@PathVariable Long id) {
        return emailPreAuthService.getById(id);
    }

    @PostMapping("/fetch")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER')")
    public void fetchEmails() {
        emailPreAuthService.processEmails();
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER')")
    public void approve(@PathVariable Long id, 
                        @RequestParam Long memberId, 
                        @RequestParam Long serviceId,
                        @RequestParam(required = false) String notes,
                        java.security.Principal principal) {
        preAuthorizationService.createPreAuthorizationFromEmail(id, memberId, serviceId, notes, principal.getName());
    }

    @PostMapping("/{id}/mark-processed")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER')")
    public void markProcessed(@PathVariable Long id, @RequestParam Long preAuthId) {
        emailPreAuthService.markAsProcessed(id, preAuthId);
    }

    @PostMapping("/{id}/reidentify")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER')")
    public void reidentify(@PathVariable Long id) {
        emailPreAuthService.reidentifyRequest(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public void delete(@PathVariable Long id) {
        emailRequestRepository.deleteById(id);
    }
}
