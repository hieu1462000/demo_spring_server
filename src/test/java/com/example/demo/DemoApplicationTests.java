package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.util.Base64;

@SpringBootTest
class DemoApplicationTests {

    // Sinh private key RSA tạm dạng base64 PEM như env CSHIELD_PRIVATE_KEY, không commit key vào repo
    @DynamicPropertySource
    static void cshieldProperties(DynamicPropertyRegistry registry) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(generator.generateKeyPair().getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
        String base64Pem = Base64.getEncoder().encodeToString(pem.getBytes(StandardCharsets.UTF_8));
        registry.add("CSHIELD_PRIVATE_KEY", () -> base64Pem);
    }

    @Test
    void contextLoads() {
    }
}
