package com.waad.tba.modules.monitoring.service;

import com.waad.tba.modules.monitoring.entity.SystemMonitoringSettings;
import com.waad.tba.security.SecretEncryptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Sends alert messages to a Telegram chat/thread.
 *
 * The bot token is stored encrypted at rest ({@link SecretEncryptionService}) — the
 * reference implementation persisted it as plain text. It is decrypted only in memory,
 * immediately before the outbound HTTP call, and never logged or echoed back.
 */
@Service
@RequiredArgsConstructor
public class TelegramAlertService {

    private final MonitoringSettingsService settingsService;
    private final SecretEncryptionService secretEncryptionService;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    public void sendTestMessage() {
        SystemMonitoringSettings settings = settingsService.getOrCreate();
        validate(settings);
        String text = """
                ✅ WAAD رسالة اختبار
                النظام: WAAD TPA
                البيئة: %s
                الوقت: %s
                النتيجة: تم إعداد Telegram بنجاح
                """.formatted(settings.getAlertEnvironment(), LocalDateTime.now());
        send(settings, text);
    }

    public void sendMonitoringMessage(String text) {
        SystemMonitoringSettings settings = settingsService.getSchedulerSettings();
        validate(settings);
        send(settings, text);
    }

    public void sendMonitoringMessage(SystemMonitoringSettings settings, String text) {
        validate(settings);
        send(settings, text);
    }

    private void validate(SystemMonitoringSettings settings) {
        if (!Boolean.TRUE.equals(settings.getTelegramEnabled())) {
            throw new IllegalStateException("تنبيهات Telegram غير مفعلة. فعّل الإعدادات واحفظها أولاً.");
        }
        if (isBlank(settings.getTelegramBotToken()) || isBlank(settings.getTelegramChatId())) {
            throw new IllegalStateException("بيانات Telegram غير مكتملة. تحقق من Bot Token و Chat ID.");
        }
    }

    private void send(SystemMonitoringSettings settings, String text) {
        try {
            String token = secretEncryptionService.decrypt(settings.getTelegramBotToken());
            StringBuilder body = new StringBuilder()
                    .append("chat_id=").append(enc(settings.getTelegramChatId()))
                    .append("&text=").append(enc(text));
            if (!isBlank(settings.getTelegramThreadId())) {
                body.append("&message_thread_id=").append(enc(settings.getTelegramThreadId()));
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.telegram.org/bot" + token + "/sendMessage"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(
                        "فشل إرسال رسالة الاختبار. تحقق من Bot Token و Chat ID واتصال السيرفر بالإنترنت.");
            }
        } catch (IOException e) {
            throw new IllegalStateException("فشل الاتصال بخدمة Telegram. تحقق من اتصال السيرفر بالإنترنت.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("تم إيقاف اختبار Telegram قبل اكتماله.", e);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("صيغة Bot Token أو Chat ID غير صحيحة.", e);
        }
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
