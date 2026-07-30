package com.schoolbus.shared.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/system")
public class SystemController {

    @GetMapping("/ping")
    public ApiResponse<SystemStatusResponse> ping() {
        return ApiResponse.success(new SystemStatusResponse("UP", Instant.now()));
    }

    public record SystemStatusResponse(String status, Instant serverTime) {
    }
}
