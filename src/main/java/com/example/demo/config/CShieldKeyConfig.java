package com.example.demo.config;

import com.cshield.sdk.api.exception.ConfigurationException;
import com.cshield.sdk.api.signing_key.DefaultPrivateKeyProvider;
import com.cshield.sdk.api.signing_key.PrivateKeyProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Configuration
public class CShieldKeyConfig {

    // Private key ký response từ env CSHIELD_PRIVATE_KEY (PEM đã base64), thiếu thì dừng lúc khởi động
    @Bean
    public PrivateKeyProvider privateKeyProvider(Environment env) {
        String base64Pem = env.getProperty("CSHIELD_PRIVATE_KEY");
        if (base64Pem == null || base64Pem.isBlank()) {
            throw new ConfigurationException("Missing env var: CSHIELD_PRIVATE_KEY");
        }
        String pem = new String(Base64.getMimeDecoder().decode(base64Pem.trim()), StandardCharsets.UTF_8);
        return new DefaultPrivateKeyProvider(pem);
    }
}
