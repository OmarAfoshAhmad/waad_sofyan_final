package com.waad.tba.modules.notification.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

/**
 * حمولة الإشعار الفوري المُرسَل للمراجعين عبر SSE.
 *
 * القاعدة: لا تُرسل بيانات مالية حساسة عبر SSE.
 *          فقط: معرّف + نوع الحدث + الأولوية + رسالة.
 *          الواجهة تجلب التفاصيل الكاملة بـ API منفصل.
 */
@Value
@Builder
public class NotificationPayload {

    /** نوع الحدث */
    String type;          // NEW_PREAUTH | STATUS_CHANGED | URGENT_PREAUTH | INFO_REQUESTED

    /** معرّف الموافقة المسبقة */
    Long preAuthId;

    /** الرقم المرجعي (PA-YYYYMMDD-XXXXX) */
    String referenceNumber;

    /** اسم مقدم الخدمة */
    String providerName;

    /**
     * مستوى الأولوية.
     * القيم: EMERGENCY | URGENT | NORMAL | LOW
     */
    String priority;

    /** الوضع الجديد (للعرض في الإشعار) */
    String newStatus;

    /** نص الإشعار بالعربية */
    String message;

    /** توقيت الحدث */
    @Builder.Default
    LocalDateTime timestamp = LocalDateTime.now();
}
