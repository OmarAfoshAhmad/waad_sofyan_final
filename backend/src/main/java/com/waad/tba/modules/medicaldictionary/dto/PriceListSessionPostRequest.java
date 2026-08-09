package com.waad.tba.modules.medicaldictionary.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class PriceListSessionPostRequest {

    @NotNull(message = "رقم عقد مقدم الخدمة مطلوب")
    private Long contractId;

    /**
     * تاريخ بداية تطبيق هذه القائمة. عند تركه فارغاً يستخدم تاريخ بداية العقد.
     */
    private LocalDate effectiveFrom;

    /**
     * إذا كان true سيتم إغلاق السعر الفعال السابق لنفس كود/اسم الخدمة قبل إنشاء
     * السعر الجديد. إذا كان false سيتم رفض السطر عند وجود سعر فعال سابق.
     */
    /** Fail closed by default: callers must explicitly opt into replacing an
     * effective price after reviewing the dated diff. */
    private boolean replaceEffectivePrices = false;

    /**
     * يمنع ترحيل السطور المعلقة أو غير المعروفة. الافتراضي أكثر أماناً مالياً.
     */
    private boolean onlyReviewedItems = true;

    /**
     * بنود محددة من الجلسة. null يعني كل البنود (للتوافق مع شاشة الجلسات)،
     * والقائمة الفارغة مرفوضة حتى لا تتحول نقرة بلا تحديد إلى ترحيل شامل.
     */
    private List<Long> itemIds;
}
