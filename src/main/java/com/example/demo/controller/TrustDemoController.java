package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TrustDemoController {

    // Đòi trust >= L4 (c-shield.routes)
    @PostMapping("/payment")
    public ApiResponse payment() {
        return new ApiResponse(true, "OK", "Payment authorized (trust >= L4)");
    }

    // Đòi L4 và trust tươi trong max-trust-age-seconds (c-shield.routes)
    @PostMapping("/transfer")
    public ApiResponse transfer() {
        return new ApiResponse(true, "OK", "Transfer authorized (fresh L4)");
    }
}
