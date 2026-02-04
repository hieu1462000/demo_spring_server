package com.example.demo.controller

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/non-authorize")
class NonAuthorizeController {

    @GetMapping("/hello")
    fun hello(): String = "Hello Spring Boot Kotlin 👋"
}