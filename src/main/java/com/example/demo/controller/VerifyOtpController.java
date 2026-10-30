package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.dto.VerifyOtpRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/verify-otp")
public class VerifyOtpController {

    // OTP đúng là 123456
    @PostMapping
    public ApiResponse verify(@RequestBody(required = false) VerifyOtpRequest req) {
        if (req == null || !"123456".equals(req.getOtp())) {
            return new ApiResponse(false, "INVALID_OTP", "Xac thuc OTP khong thanh cong");
        }
        return new ApiResponse(true, "OK", "Xac thuc OTP thanh cong");
    }
}
