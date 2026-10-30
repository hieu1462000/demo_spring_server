package com.example.demo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.util.Base64;

@SpringBootTest
class DemoApplicationTests {

    @TempDir
    static Path keyDir;

    // Sinh private key RSA tạm và realm test, không commit key vào repo
    @DynamicPropertySource
    static void cshieldProperties(DynamicPropertyRegistry registry) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(generator.generateKeyPair().getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
        Path keyFile = Files.writeString(keyDir.resolve("server_private.pem"), pem);
        registry.add("CSHIELD_PRIVATE_KEY_FILE", keyFile::toString);
        registry.add("CSHIELD_CUSTOMER_CODE", () -> "CMCCS");
        registry.add("CSHIELD_APPLICATION_ID", () -> "cshield-embedded-sample");
        registry.add("CSHIELD_ENVIRONMENT", () -> "DEV");
    }

    @Test
    void contextLoads() {
    }
}
