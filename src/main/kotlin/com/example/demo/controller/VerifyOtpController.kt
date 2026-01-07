package com.example.demo.controller

import com.example.demo.dto.ApiResponse
import com.example.demo.dto.VerifyOtpRequest
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/verify-otp")
class VerifyOtpController {

    @PostMapping
    fun verify(@RequestBody req: VerifyOtpRequest): ApiResponse {
        if (req?.otp.isNullOrBlank()) {
            return ApiResponse(
                success = false,
                code = "INVALID_REQUEST",
                message = "OTP không được để trống"
            )
        }

        if (req!!.otp != "123456") {
            return ApiResponse(
                success = false,
                code = "INVALID_OTP",
                message = "OTP không chính xác"
            )
        }

        return ApiResponse(
            success = true,
            code = "OK",
            message = "Xác thực OTP thành công"
        )
    }

    @GetMapping("/hello")
    fun hello(): String = "Hello Spring Boot Kotlin 👋"
}