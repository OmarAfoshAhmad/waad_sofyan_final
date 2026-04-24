package com.waad.tba.modules.claim.dto.engine;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO يمثل سطر خدمة واحد مُرسَل من الـ Frontend إلى محرك التغطية.
 *
 * ══════════════════════════════════════════════════════════════════════
 * كل الحسابات المالية تحدث في الـ Backend — الـ Frontend يُرسل
 * البيانات الخام فقط (أسعار، كميات، معرّفات) ويستقبل النتيجة المحسوبة.
 * ══════════════════════════════════════════════════════════════════════
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClaimLineInput {

    /**
     * معرّف السطر المؤقت من الـ Frontend (UUID).
     * يُعاد في الرد للمطابقة بين الطلب والنتيجة.
     * مثال: "a1b2c3d4-e5f6-..."
     */
    @NotBlank(message = "lineId مطلوب لمطابقة النتائج")
    private String lineId;

    /**
     * معرّف الخدمة الطبية (medicalServiceId).
     * يُستخدم لجلب قاعدة التغطية من BenefitPolicyRule.
     */
    private Long serviceId;

    /**
     * معرّف عنصر التسعير في عقد المزود (pricingItemId).
     * يُستخدم للمرجعية والتدقيق.
     */
    private Long pricingItemId;

    /**
     * الكمية — عدد الوحدات أو الجلسات.
     * يجب أن تكون >= 1.
     */
    @NotNull(message = "الكمية مطلوبة")
    @Min(value = 1, message = "الكمية يجب أن تكون 1 على الأقل")
    private Integer quantity;

    /**
     * السعر الذي أدخله المستخدم (قبل تطبيق سقف العقد).
     * يُقارن مع contractPrice لتحديد المرفوض بسبب تجاوز السعر.
     */
    @NotNull(message = "السعر المُدخل مطلوب")
    @DecimalMin(value = "0.00", message = "السعر يجب أن يكون >= 0")
    private BigDecimal enteredUnitPrice;

    /**
     * السعر المتفق عليه في عقد المزود.
     * مصدره: provider_contract_pricing_items.contract_price
     * إذا كان 0 أو null → لا يوجد سقف سعري، يُستخدم السعر المُدخل كاملاً.
     */
    @DecimalMin(value = "0.00", message = "سعر العقد يجب أن يكون >= 0")
    private BigDecimal contractPrice;

    /**
     * معرّف التصنيف الطبي السياقي (context override).
     * مثال: 51 = CAT-OP (عيادات خارجية).
     * يُستخدم في خوارزمية "المرآة" لتحديد القاعدة الصحيحة.
     */
    private Long categoryId;

    /**
     * معرّف تصنيف الخدمة الأصلي (intrinsic category).
     * مثال: 201 = CAT-IP-PHYSIO (علاج طبيعي).
     * يُستخدم مع categoryId في خوارزمية "المرآة".
     */
    private Long serviceCategoryId;

    /**
     * هل هذا السطر مرفوض بالكامل (رفض يدوي من المستخدم)؟
     * إذا كان true → companyShare = 0، patientShare تُحسب من السعر الفعّال.
     */
    @Builder.Default
    private boolean rejected = false;

    /**
     * مبلغ الرفض الجزئي اليدوي (يُخصم من حصة الشركة فقط).
     * لا يتجاوز companyShare — يُعالَج داخل المحرك.
     */
    @DecimalMin(value = "0.00", message = "مبلغ الرفض الجزئي يجب أن يكون >= 0")
    @Builder.Default
    private BigDecimal manualRefusedAmount = BigDecimal.ZERO;

    /**
     * سبب الرفض اليدوي لأغراض التدقيق المالي.
     * اختياري، لكنه مهم لتتبع القرار اليدوي لاحقاً.
     */
    private String manualRefusalReason;
}
