package com.waad.tba.common.finance;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Single definition of how money is represented and rounded system-wide.
 *
 * Every monetary column is NUMERIC(15,2) and every rounding is HALF_UP — this
 * was already the de-facto convention (HALF_UP appears in 47 places and nowhere
 * else), but it lived only as a repeated literal, which is how the settlement
 * module ended up with entities declaring scale = 3 against scale = 2 columns.
 * Libyan dinar amounts are never finer than 0.01, so a third decimal is always
 * either a bug or a value the database would silently round away on write.
 *
 * Deliberately minimal: normalization only. Allocation/distribution helpers are
 * not added here until there is a real caller for them.
 */
public final class Money {

    /** Decimal places for every monetary value in the system. */
    public static final int SCALE = 2;

    /** Rounding applied whenever a monetary value must be reduced to SCALE. */
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE);

    private Money() {
    }

    /** Returns the amount at the canonical scale; null is treated as zero. */
    public static BigDecimal normalize(BigDecimal amount) {
        return amount == null ? ZERO : amount.setScale(SCALE, ROUNDING);
    }

    /**
     * True when the amount is already exactly representable at the canonical
     * scale, i.e. normalizing it would not change its value. Use this to detect
     * sub-cent input that must be rejected rather than silently rounded.
     */
    public static boolean isExact(BigDecimal amount) {
        return amount != null && amount.compareTo(normalize(amount)) == 0;
    }
}
