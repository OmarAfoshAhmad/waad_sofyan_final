package com.waad.tba.common.guard;

import com.waad.tba.common.exception.BusinessRuleException;

import java.util.ArrayList;
import java.util.List;

/**
 * Fluent builder for soft-delete dependency guards.
 *
 * <p>
 * Centralises the "entity is in use → block deletion" pattern so every
 * service produces a consistent, Arabic-language error message without
 * duplicating the build logic.
 *
 * <pre>
 * DeletionGuard.of("جهة العمل")
 *         .check("مستفيدون", memberRepository.countByEmployerId(id))
 *         .check("وثائق تأمين نشطة", policyRepository.countByEmployerIdAndActiveTrue(id))
 *         .throwIfBlocked("أوقف تفعيل المستفيدين وأنهِ الوثائق أولاً.");
 * </pre>
 *
 * <p>
 * On block a {@link BusinessRuleException} is thrown, which
 * {@code GlobalExceptionHandler} maps to HTTP 422 with the full Arabic detail.
 */
public final class DeletionGuard {

    private final String entityNameAr;
    private final List<String> blockers = new ArrayList<>();

    private DeletionGuard(String entityNameAr) {
        this.entityNameAr = entityNameAr;
    }

    /**
     * Start building a guard for the given entity name (Arabic).
     *
     * @param entityNameAr e.g. "جهة العمل", "مقدم الخدمة"
     */
    public static DeletionGuard of(String entityNameAr) {
        return new DeletionGuard(entityNameAr);
    }

    /**
     * Register a dependency check. If {@code count > 0} the dependency becomes
     * a blocker and its label + count will appear in the error message.
     *
     * @param labelAr Arabic label for this dependency, e.g. "مستفيدون نشطون"
     * @param count   number of linked records (from any repository count query)
     * @return this builder (fluent)
     */
    public DeletionGuard check(String labelAr, long count) {
        if (count > 0) {
            blockers.add(labelAr + ": " + count);
        }
        return this;
    }

    /**
     * Throws {@link BusinessRuleException} if any registered dependency had
     * count &gt; 0, producing a message of the form:
     * 
     * <pre>
     * لا يمكن حذف جهة العمل لأنها مرتبطة بـ: (مستفيدون: 3، وثائق نشطة: 1).
     * أوقف تفعيل المستفيدين وأنهِ الوثائق أولاً.
     * </pre>
     *
     * @param guideAr short Arabic action hint shown to the user
     */
    public void throwIfBlocked(String guideAr) {
        if (!blockers.isEmpty()) {
            String details = String.join("، ", blockers);
            throw new BusinessRuleException(
                    "لا يمكن حذف " + entityNameAr
                            + " لأنه/ها مرتبط بـ: (" + details + "). "
                            + guideAr);
        }
    }
}
