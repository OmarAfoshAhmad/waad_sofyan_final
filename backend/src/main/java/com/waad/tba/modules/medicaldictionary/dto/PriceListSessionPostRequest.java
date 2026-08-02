package com.waad.tba.modules.medicaldictionary.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

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
    private boolean replaceEffectivePrices = true;

    /**
     * يمنع ترحيل السطور المعلقة أو غير المعروفة. الافتراضي أكثر أماناً مالياً.
     */
    private boolean onlyReviewedItems = true;
}
