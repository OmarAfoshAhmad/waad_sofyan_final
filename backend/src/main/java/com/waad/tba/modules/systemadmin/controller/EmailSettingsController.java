package com.waad.tba.modules.systemadmin.controller;

import com.waad.tba.modules.systemadmin.dto.EmailSettingsDto;
import com.waad.tba.modules.systemadmin.service.EmailSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/settings/email")
@RequiredArgsConstructor
public class EmailSettingsController {

    private final EmailSettingsService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<EmailSettingsDto> getSettings() {
        return ResponseEntity.ok(service.getActiveSettings());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<EmailSettingsDto> updateSettings(@RequestBody EmailSettingsDto dto) {
        return ResponseEntity.ok(service.updateSettings(dto));
    }

    @PostMapping("/test-imap")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Boolean> testImap(@RequestBody EmailSettingsDto dto) {
        return ResponseEntity.ok(service.testImapConnection(dto));
    }

    @PostMapping("/test-smtp")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Boolean> testSmtp(@RequestBody EmailSettingsDto dto) {
        return ResponseEntity.ok(service.testSmtpConnection(dto));
    }
}
