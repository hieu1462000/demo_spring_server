package com.example.demo.config;

import com.cshield.sdk.api.exception.MissingSignatureHeaderException;
import com.cshield.sdk.api.exception.TimeoutRequestException;
import com.cshield.sdk.api.key_provider.client_public_key.ClientPublicKeyProvider;
import com.cshield.sdk.api.key_provider.server_private_key.ServerPrivateKeyProvider;
import com.cshield.sdk.api.request.RequestVerifier;
import com.cshield.sdk.api.response.ResponseSigner;
import com.example.demo.key_provider.EnvPrivateKeyProvider;
import com.example.demo.key_provider.EnvPublicKeyProvider;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CShieldOverrideConfig {

    @Bean
    public ClientPublicKeyProvider publicKeyProvider() {
        return new EnvPublicKeyProvider("CSHIELD_CLIENT_PUBLIC_KEY");
    }

    @Bean
    public ServerPrivateKeyProvider privateKeyProvider() {
        return new EnvPrivateKeyProvider("CSHIELD_SERVER_PRIVATE_KEY");
    }

    @Bean
    public RequestVerifier myVerifier(ClientPublicKeyProvider provider) {
        return new RequestVerifier(provider.load()) {
            @Override
            public void verify(
                    String method,
                    String path,
                    String timestamp,
                    String bodyHash,
                    String signature
            ) {
                if (path != null && path.startsWith("/non-authorize")) {
                    return;
                }

                if (timestamp == null || signature == null) {
                    throw new MissingSignatureHeaderException("cs-timestamp / cs-signature");
                }

                long diff = System.currentTimeMillis() / 1000 - Long.parseLong(timestamp);
                if (diff > 30) { //30s
                    throw new TimeoutRequestException();
                }
                String payload = method + "." + path + "." + timestamp + "." + bodyHash;
                super.verifier.verify(payload, signature);
            }
        };
    }

    @Bean
    public ResponseSigner mySigner(ServerPrivateKeyProvider provider) {
        return new ResponseSigner(provider.load()) {
            @Override
            public String sign(
                    int status,
                    String path,
                    String timestamp,
                    String bodyHash
            ) {
                if (path != null && path.startsWith("/non-authorize")) {
                    System.out.println(path);
                    return "";
                }
                String payload = status + "." + path + "." + timestamp + "." + bodyHash;
                return super.signer.sign(payload);
            }
        };
    }
}
