package com.waad.tba.modules.monitoring.controller;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.monitoring.dto.MonitoringDtos.MonitoringSettingsDto;
import com.waad.tba.modules.monitoring.dto.MonitoringDtos.SystemHealthDto;
import com.waad.tba.modules.monitoring.dto.MonitoringDtos.TelegramTestResultDto;
import com.waad.tba.modules.monitoring.service.MonitoringSettingsService;
import com.waad.tba.modules.monitoring.service.SystemHealthService;
import com.waad.tba.modules.monitoring.service.TelegramAlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/system/monitoring")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class MonitoringController {

    private final MonitoringSettingsService settingsService;
    private final TelegramAlertService telegramAlertService;
    private final SystemHealthService systemHealthService;

    @GetMapping("/health")
    public ApiResponse<SystemHealthDto> health() {
        return ApiResponse.success(systemHealthService.fullHealth());
    }

    @GetMapping("/settings")
    public ApiResponse<MonitoringSettingsDto> settings() {
        return ApiResponse.success(settingsService.getSettings());
    }

    @PutMapping("/settings")
    public ApiResponse<MonitoringSettingsDto> updateSettings(@RequestBody MonitoringSettingsDto request,
                                                              Authentication authentication) {
        String username = authentication == null ? "SYSTEM" : authentication.getName();
        return ApiResponse.success(settingsService.update(request, username),
                "Monitoring settings updated", "تم حفظ إعدادات التنبيهات والمراقبة");
    }

    @PostMapping("/telegram/test")
    public ResponseEntity<ApiResponse<TelegramTestResultDto>> testTelegram() {
        try {
            telegramAlertService.sendTestMessage();
            String message = "تم إرسال رسالة الاختبار بنجاح.";
            settingsService.recordTest(true, message);
            return ResponseEntity.ok(ApiResponse.success(
                    new TelegramTestResultDto(true, message, LocalDateTime.now()), "Telegram test sent", message));
        } catch (Exception e) {
            String message = safeMessage(e);
            settingsService.recordTest(false, message);
            return ResponseEntity.badRequest().body(ApiResponse.<TelegramTestResultDto>builder()
                    .status("error")
                    .message("Telegram test failed")
                    .messageAr(message)
                    .data(new TelegramTestResultDto(false, message, LocalDateTime.now()))
                    .timestamp(LocalDateTime.now())
                    .build());
        }
    }

    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            return "فشل إرسال رسالة الاختبار. تحقق من Bot Token و Chat ID واتصال السيرفر بالإنترنت.";
        }
        if (message.toLowerCase().contains("token")) {
            return "فشل إرسال رسالة الاختبار. تحقق من Bot Token و Chat ID واتصال السيرفر بالإنترنت.";
        }
        return message;
    }
}
