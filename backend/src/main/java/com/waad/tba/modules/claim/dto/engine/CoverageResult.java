package com.waad.tba.modules.claim.dto.engine;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO نتيجة حساب محرك التغطية لسطر خدمة واحد.
 *
 * ══════════════════════════════════════════════════════════════════════
 * جميع المبالغ من نوع BigDecimal (مقرّبة لخانتين عشريتين).
 * لا يوجد أي حساب في الـ Frontend — هذا الـ DTO هو مصدر الحقيقة الوحيد.
 * ══════════════════════════════════════════════════════════════════════
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoverageResult {

    // ══════════════════════════════════════════════════════
    // IDENTITY — لمطابقة السطر مع طلب الـ Frontend
    // ══════════════════════════════════════════════════════

    /** نفس lineId المُرسَل في الطلب — للمطابقة في الـ Frontend */
    private String lineId;

    /** كود الخدمة (للتدقيق والترابط) */
    private String serviceCode;

    /** اسم الخدمة (للتدقيق والترابط) */
    private String serviceName;

    // ══════════════════════════════════════════════════════
    // PRICE RESOLUTION — خطوة 1: تحديد السعر الفعّال
    // ══════════════════════════════════════════════════════

    /**
     * السعر الفعّال بعد تطبيق سقف العقد.
     * = min(enteredUnitPrice, contractPrice) إذا كان contractPrice > 0
     * = enteredUnitPrice إذا لم يكن هناك سقف سعري
     */
    private BigDecimal effectiveUnitPrice;

    /**
     * إجمالي السطر بالسعر الفعّال.
     * = effectiveUnitPrice × quantity
     */
    private BigDecimal effectiveTotal;

    /**
     * الإجمالي المطلوب بالسعر الذي أدخله المستخدم (قبل السقف).
     * = enteredUnitPrice × quantity
     */
    private BigDecimal requestedTotal;

    // ══════════════════════════════════════════════════════
    // COVERAGE — خطوة 2: التغطية من القاعدة
    // ══════════════════════════════════════════════════════

    /**
     * نسبة التغطية المطبّقة (0-100).
     * المصدر: BenefitPolicyRule.effectiveCoveragePercent
     * أو policy.defaultCoveragePercent إذا لم توجد قاعدة خاصة.
     */
    private Integer coveragePercent;

    /** هل الخدمة غير مشمولة بالتغطية كلياً؟ */
    private boolean notCovered;

    /** هل تتطلب موافقة مسبقة؟ */
    private boolean requiresPreApproval;

    // ══════════════════════════════════════════════════════
    // LIMITS — خطوة 3: تطبيق سقوف المنفعة
    // ══════════════════════════════════════════════════════

    /**
     * تفاصيل سقوف المنفعة والاستخدام.
     * null إذا لم توجد قاعدة أو لا توجد سقوف.
     */
    private UsageDetails usageDetails;

    // ══════════════════════════════════════════════════════
    // FINANCIAL SPLIT — خطوة 4: توزيع المبالغ
    // ══════════════════════════════════════════════════════

    /**
     * المبلغ المعتمد للتوزيع (بعد حسم المرفوض بسبب السقوف والسعر).
     * = effectiveTotal - limitRefused
     */
    private BigDecimal approvedTotal;

    /**
     * حصة الشركة (شركة التأمين).
     * = approvedTotal × coveragePercent / 100 - manualRefused
     */
    private BigDecimal companyShare;

    /**
     * حصة المستفيد (المريض).
     * = approvedTotal - companyShare (قبل الرفض اليدوي)
     */
    private BigDecimal patientShare;

    /** سبب الرفض التلقائي — null إذا لم يكن هناك رفض */
    private String refusalReason;

    // ══════════════════════════════════════════════════════
    // AUDIT — للتدقيق والشفافية
    // ══════════════════════════════════════════════════════

    /** المبلغ المرفوض بسبب تجاوز سعر العقد */
    private BigDecimal priceRefused;

    /** المبلغ المرفوض بسبب تجاوز سقف المنفعة */
    private BigDecimal limitRefused;

    /**
     * الرفض المحسوب تلقائياً من النظام.
     * = priceRefused + limitRefused
     */
    private BigDecimal systemRefusedAmount;

    /** الرفض اليدوي كما أدخله المستخدم (لا يُعاد اشتقاقه). */
    private BigDecimal manualRefusedAmount;

    /** سبب الرفض اليدوي لغرض التدقيق. */
    private String manualRefusalReason;

    /** معرّف القاعدة المطبّقة (للتدقيق) */
    private Long appliedRuleId;

    /** معرّف التصنيف الذي طُبّقت عليه القاعدة (بعد خوارزمية المرآة) */
    private Long resolvedCategoryId;

    /**
     * القيمة النهائية المرفوضة (مشتقة فقط، غير مخزنة).
     * Final = System + Manual
     */
    public BigDecimal getFinalRefusedAmount() {
        BigDecimal system = systemRefusedAmount != null ? systemRefusedAmount : BigDecimal.ZERO;
        BigDecimal manual = manualRefusedAmount != null ? manualRefusedAmount : BigDecimal.ZERO;
        return system.add(manual);
    }

    /**
     * Backward-compatible alias كي لا تنكسر الواجهات الحالية.
     * يعيد نفس قيمة FinalRefusedAmount.
     */
    public BigDecimal getRefusedAmount() {
        return getFinalRefusedAmount();
    }

    @JsonIgnore
    public boolean isRefusedAmountStored() {
        return false;
    }

    // ══════════════════════════════════════════════════════
    // NESTED DTO: تفاصيل الاستخدام والسقوف
    // ══════════════════════════════════════════════════════

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UsageDetails {

        /** معرّف القاعدة المطبّقة */
        private Long ruleId;

        /** هل توجد سقوف؟ */
        private boolean hasLimit;

        /** سقف عدد المرات (null = غير محدود) */
        private Integer timesLimit;

        /** سقف المبلغ السنوي (null = غير محدود) */
        private BigDecimal amountLimit;

        /** عدد مرات الاستخدام السابقة (من قاعدة البيانات، لا تشمل هذا السطر) */
        private Integer usedCount;

        /** المبلغ المستخدم سابقاً من السقف */
        private BigDecimal usedAmount;

        /** المبلغ المتبقي بعد هذه المطالبة */
        private BigDecimal remainingAmount;

        /** هل تجاوز المستفيد عدد المرات المسموح؟ */
        private boolean timesExceeded;

        /** هل تجاوز المستفيد المبلغ المسموح؟ */
        private boolean amountExceeded;

        /** هل تجاوز بأي شكل من الأشكال؟ */
        private boolean exceeded;
    }
}
