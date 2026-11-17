package com.mathverse.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Rounding exactly as Python's round() does it, so both backends store and show
 * the same numbers.
 *
 * Python rounds a value that is exactly halfway to the nearest EVEN digit
 * (round(2.5) == 2, round(0.125, 2) == 0.12). Java's Math.round always rounds
 * halves up, which would make some percentages differ by 1 between the two
 * backends.
 */
public final class Rounding {

    private Rounding() {
    }

    /** Python: round(x, digits) */
    public static double round(double x, int digits) {
        // new BigDecimal(x) is the exact value of the double, as Python uses it.
        return new BigDecimal(x).setScale(digits, RoundingMode.HALF_EVEN).doubleValue();
    }

    /** Python: round(x), giving a whole number */
    public static int round(double x) {
        return (int) Math.rint(x); // rint rounds halves to the even neighbour
    }
}
