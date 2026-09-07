package com.codeBigJay.mediaconverter.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// Simple root endpoint so platform health checks (Render/Railway) get a 200
// instead of Spring Boot's default 404 whitelabel page at "/".
@RestController
public class HealthController {
    @GetMapping("/")
    public String health() {
        return "Media converter backend is running";
    }
}
