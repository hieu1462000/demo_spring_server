package com.example.demo.key_provider

import com.cshield.sdk.key_provider.PublicKeyProvider
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.*

class EnvPublicKeyProvider(
    private val envName: String
) : PublicKeyProvider {

    override fun load(): PublicKey {
        var base64 = System.getenv(envName)
        check(!(base64 == null || base64.isBlank())) { "Missing env $envName" }

        try {
            base64 = base64.trim();
            if (base64.startsWith("\"") && base64.endsWith("\"")) {
                base64 = base64.substring(1, base64.length - 1);
            }
            val decoded = Base64.getDecoder().decode(base64)
            val spec = X509EncodedKeySpec(decoded)
            return KeyFactory.getInstance("RSA").generatePublic(spec)
        } catch (e: Exception) {
            throw IllegalStateException(
                "Invalid base64 in env $envName", e
            )
        }
    }
}
