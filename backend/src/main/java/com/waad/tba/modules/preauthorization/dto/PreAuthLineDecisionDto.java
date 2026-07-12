package com.waad.tba.modules.preauthorization.dto;

import com.waad.tba.modules.preauthorization.entity.PreAuthorizationLine.LineDecisionStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * DTO لاتخاذ قرار على مستوى سطر خدمة واحد في الموافقة المسبقة.
 *
 * القواعد:
 * - decisionStatus إلزامي دائماً.
 * - عند REJECTED أو PARTIALLY_APPROVED، يجب ذكر decisionNotes.
 * - approvedAmount = المبلغ الموافق عليه فعلياً (قد يقل عن contractPrice).
 * - varianceAmount يُحسب تلقائياً في الـ Service (contractPrice - approvedAmount).
 * - contractPrice لا يُمس أبداً — يبقى لأغراض التدقيق.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreAuthLineDecisionDto {

    /**
     * قرار السطر: APPROVED / PARTIALLY_APPROVED / REJECTED / INFO_REQUESTED
     */
    @NotNull(message = "قرار السطر مطلوب")
    private LineDecisionStatus decisionStatus;

    /**
     * المبلغ الموافق عليه فعلياً.
     * - null = الموافقة الكاملة (يساوي contractPrice).
     * - أي قيمة أقل من contractPrice = موافقة جزئية.
     * - صفر = رفض المبلغ كلياً مع الإبقاء على السطر مرئياً.
     */
    @DecimalMin(value = "0.00", message = "المبلغ الموافق عليه لا يمكن أن يكون سالباً")
    private BigDecimal approvedAmount;

    /**
     * كود سبب الرفض أو التعديل (من جدول أكواد الرفض المعتمدة).
     * اختياري — يُساعد في إنتاج تقارير إحصائية للأسباب.
     */
    @Size(max = 50, message = "كود السبب لا يتجاوز 50 حرفاً")
    private String decisionReasonCode;

    /**
     * ملاحظات المراجع على هذا السطر تحديداً.
     * إلزامي عند REJECTED أو PARTIALLY_APPROVED.
     */
    @Size(max = 1000, message = "الملاحظات لا تتجاوز 1000 حرف")
    private String decisionNotes;
}
