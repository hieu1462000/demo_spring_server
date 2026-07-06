package com.example.demo.key_provider;

import com.cshield.sdk.api.key_provider.server_private_key.ServerPrivateKeyProvider;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

public class EnvPrivateKeyProvider implements ServerPrivateKeyProvider {

    private final String envName;

    public EnvPrivateKeyProvider(String envName) {
        this.envName = envName;
    }

    @Override
    public PrivateKey load() {
        String raw = System.getenv(envName);
        if (raw == null || raw.trim().isEmpty()) {
            throw new IllegalStateException("Missing env " + envName);
        }

        try {
            // trim & remove quotes nếu có
            raw = raw.trim();
            if (raw.startsWith("\"") && raw.endsWith("\"")) {
                raw = raw.substring(1, raw.length() - 1);
            }

            // remove PEM header / footer + whitespace
            String base64 = raw
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", ""); // remove newline, space, tab

            byte[] decoded = Base64.getDecoder().decode(base64);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
            return KeyFactory.getInstance("RSA").generatePrivate(spec);
        } catch (Exception e) {
            throw new IllegalStateException("Invalid private key in env " + envName, e);
        }
    }
}
