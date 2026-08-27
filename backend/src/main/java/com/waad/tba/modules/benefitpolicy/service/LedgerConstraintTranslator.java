package com.waad.tba.modules.benefitpolicy.service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * Turns a ledger constraint or trigger violation into a message a user can act
 * on, in Arabic, and keeps the database's own text out of the response.
 *
 * The generic exception handler puts {@code ex.getMessage()} into the response
 * body under "reason". Left alone, a violation from
 * benefit_bucket_consumptions would send the user English text describing
 * internal accounting mechanics -- constraint names, row ids, amounts held
 * against other movements. That is both unusable and more than a caller should
 * learn about the ledger's shape.
 *
 * Every rule here is enforced by Postgres, not by this class. The translation
 * exists so a refusal is explainable; the refusal itself is the database's.
 */
@Component
public class LedgerConstraintTranslator {

    /**
     * Constraint/trigger signature -> Arabic explanation. Ordered: the first
     * match wins, so the more specific signatures are listed first.
     */
    private static final Map<String, String> MESSAGES = new LinkedHashMap<>();

    static {
        MESSAGES.put("chk_bucket_consumption_entry_type_by_source",
                "نوع الحركة لا يطابق مصدرها. الحجز يكون بموافقة مسبقة فقط، والاستهلاك يكون بمطالبة.");
        MESSAGES.put("chk_bucket_consumption_scope_bucket",
                "حركة الوعاء يجب أن تحدد وعاءها، وحركة السقف العام يجب ألا تحمل وعاءً.");
        MESSAGES.put("chk_bucket_consumption_source_shape",
                "الحركة يجب أن تستند إلى مصدر واحد فقط، مع رأسه وبنده معاً.");
        MESSAGES.put("chk_bucket_consumption_reversal_shape",
                "حركة التعويض يجب أن تحدد الحركة الأصلية وسبب التعويض.");
        MESSAGES.put("chk_bucket_consumption_reversal_reason",
                "سبب التعويض غير معروف.");
        MESSAGES.put("chk_bucket_consumption_limit_scope",
                "نطاق السقف غير معروف.");
        MESSAGES.put("chk_bucket_consumption_source_type",
                "مصدر الحركة غير معروف.");

        // Trigger messages (RAISE EXCEPTION) -- matched on a distinctive
        // fragment of the English text the trigger raises.
        MESSAGES.put("append-only",
                "دفتر الاستهلاك لا يقبل التعديل أو الحذف. تُسجَّل حركة تعويض بدل تغيير حركة مسجلة.");
        MESSAGES.put("would exceed the original amount",
                "قيمة التعويض تتجاوز قيمة الحركة الأصلية المتبقية.");
        MESSAGES.put("cannot itself be reversed",
                "لا يمكن عكس حركة تعويض. يجب أن يشير التعويض إلى الحركة الأصلية.");
        MESSAGES.put("must name the movement it compensates",
                "حركة التعويض يجب أن تحدد الحركة الأصلية.");
        MESSAGES.put("same member, policy, scope, source and period",
                "حركة التعويض يجب أن تطابق الحركة الأصلية في المستفيد والوثيقة والنطاق والمصدر والفترة.");
        MESSAGES.put("Reversal target",
                "الحركة الأصلية المشار إليها غير موجودة.");
        MESSAGES.put("belongs to pre-authorization",
                "بند الموافقة المسبقة لا يتبع الموافقة المذكورة.");
        MESSAGES.put("belongs to claim",
                "بند المطالبة لا يتبع المطالبة المذكورة.");
        MESSAGES.put("does not exist",
                "المرجع المذكور في الحركة غير موجود.");
    }

    /**
     * @return the Arabic explanation for a recognised ledger violation, or
     *         {@code null} when the failure is not one of ours -- in which case
     *         the caller must not invent an explanation for it.
     */
    public String translate(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            String message = t.getMessage();
            if (message == null) {
                continue;
            }
            for (Map.Entry<String, String> candidate : MESSAGES.entrySet()) {
                if (message.contains(candidate.getKey())) {
                    return candidate.getValue();
                }
            }
            if (t.getCause() == t) {
                break; // a self-referencing cause would loop forever
            }
        }
        return null;
    }

    /** Whether this failure came from the consumption ledger's own rules. */
    public boolean isLedgerViolation(Throwable error) {
        return translate(error) != null;
    }
}
