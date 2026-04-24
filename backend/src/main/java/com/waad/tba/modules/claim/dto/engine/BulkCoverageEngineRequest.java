package com.waad.tba.modules.claim.dto.engine;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO طلب Bulk لمحرك التغطية.
 *
 * ══════════════════════════════════════════════════════════════════════
 * يُرسَل مرة واحدة لكل دفعة خدمات — بدلاً من request لكل سطر.
 * هذا يضمن:
 * 1. تناسق الحسابات بين الأسطر (batch context)
 * 2. تجنب race conditions
 * 3. حساب صحيح لتراكم سقوف المنفعة بين الأسطر
 * ══════════════════════════════════════════════════════════════════════
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkCoverageEngineRequest {

    /**
     * معرّف وثيقة المنافع المطبّقة على صاحب العمل.
     * المصدر الوحيد لقواعد التغطية.
     */
    @NotNull(message = "policyId مطلوب")
    private Long policyId;

    /**
     * معرّف المستفيد.
     * يُستخدم للتحقق من سجل استخدام السقوف (usage history).
     */
    @NotNull(message = "memberId مطلوب")
    private Long memberId;

    /**
     * سنة الخدمة — لتحديد نافذة حساب السقوف السنوية.
     * مثال: 2026
     */
    @NotNull(message = "serviceYear مطلوب")
    private Integer serviceYear;

    /**
     * معرّف المطالبة المستثناة من حساب السقوف (عند التعديل).
     * يمنع احتساب المطالبة الحالية ضمن سجل الاستخدام عند إعادة حسابها.
     * null عند إنشاء مطالبة جديدة.
     */
    private Long excludeClaimId;

    /**
     * هل تُطبَّق التغطية الكاملة 100% بغض النظر عن القواعد؟
     * عند true: تُتجاهل جميع السقوف والنسب وتُستخدم 100% لكل خدمة.
     */
    @Builder.Default
    private boolean fullCoverage = false;

    /**
     * قائمة أسطر الخدمات المطلوب حسابها.
     * الترتيب مهم — الأسطر تُحسب بالتسلسل لضمان صحة تراكم السقوف.
     */
    @NotEmpty(message = "يجب تحديد سطر خدمة واحد على الأقل")
    @Valid
    private List<ClaimLineInput> lines;
}
