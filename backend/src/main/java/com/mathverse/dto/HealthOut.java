package com.mathverse.dto;

/** GET /health: {"status": "ok", "demo_mode": false} */
public record HealthOut(String status, boolean demoMode) {
}
