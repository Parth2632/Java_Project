package com.mathverse.controller;

import com.mathverse.config.Settings;
import com.mathverse.dto.HealthOut;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private final Settings settings;

    public HealthController(Settings settings) {
        this.settings = settings;
    }

    @GetMapping("/health")
    public HealthOut health() {
        return new HealthOut("ok", settings.demoMode());
    }
}
