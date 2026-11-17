package com.mathverse.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * Numbers computed in the browser while a question was on screen. Images and
 * video are never accepted: these four numbers are the only fields, and any
 * other field makes the request fail (unknown fields are rejected).
 */
public record AttentionSummary(
        @NotNull(message = "Field required")
        @DecimalMin(value = "0", message = "Input should be greater than or equal to 0")
        @DecimalMax(value = "100", message = "Input should be less than or equal to 100")
        Double facePresentPct,

        @NotNull(message = "Field required")
        @DecimalMin(value = "0", message = "Input should be greater than or equal to 0")
        @DecimalMax(value = "100", message = "Input should be less than or equal to 100")
        Double onScreenPct,

        // null = no blink rate could be measured
        @DecimalMin(value = "0", message = "Input should be greater than or equal to 0")
        @DecimalMax(value = "200", message = "Input should be less than or equal to 200")
        Double blinkRatePerMin,

        @NotNull(message = "Field required")
        @DecimalMin(value = "0", message = "Input should be greater than or equal to 0")
        @DecimalMax(value = "3600", message = "Input should be less than or equal to 3600")
        Double sampleSeconds) {
}
