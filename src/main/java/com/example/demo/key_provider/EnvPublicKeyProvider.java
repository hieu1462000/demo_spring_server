package com.example.demo.key_provider;

import com.cshield.sdk.api.key_provider.client_public_key.ClientPublicKeyProvider;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class EnvPublicKeyProvider implements ClientPublicKeyProvider {
    private final String envName;

    public EnvPublicKeyProvider(String envName) {
        this.envName = envName;
    }

    @Override
    public PublicKey load() {
        String raw = System.getenv(envName);
        if (raw == null || raw.trim().isEmpty()) {
            throw new IllegalStateException("Missing env " + envName);
        }

        try {
            raw = raw.trim();
            if (raw.startsWith("\"") && raw.endsWith("\"")) {
                raw = raw.substring(1, raw.length() - 1);
            }

            String base64 = raw
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", ""); // remove newline, space, tab

            byte[] decoded = Base64.getDecoder().decode(base64);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
            return KeyFactory.getInstance("RSA").generatePublic(spec);
        } catch (Exception e) {
            throw new IllegalStateException("Invalid base64 in env " + envName, e);
        }
    }
}
