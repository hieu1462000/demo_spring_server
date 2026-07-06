package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.dto.VerifyOtpRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/verify-otp")
public class VerifyOtpController {

    @PostMapping
    public ApiResponse verify(@RequestBody(required = false) VerifyOtpRequest req) {
        if (req == null || req.getOtp() == null || req.getOtp().trim().isEmpty()) {
            return new ApiResponse(false, "INVALID_REQUEST", "OTP không được để trống");
        }

        if (!"123456".equals(req.getOtp())) {
            return new ApiResponse(false, "INVALID_OTP", "OTP không chính xác");
        }

        return new ApiResponse(true, "OK", "Xác thực OTP thành công");
    }

    @GetMapping("/hello")
    public String hello() {
        return "Hello Spring Boot Java 👋";
    }
}
