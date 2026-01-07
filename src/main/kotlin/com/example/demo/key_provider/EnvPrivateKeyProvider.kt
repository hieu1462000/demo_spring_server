package com.example.demo.key_provider

import com.cshield.sdk.key_provider.PrivateKeyProvider
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.*


class EnvPrivateKeyProvider(
    private val envName: String
) : PrivateKeyProvider {

    override fun load(): PrivateKey {
        var base64 = System.getenv(envName)
        check(!base64.isNullOrBlank()) { "Missing env $envName" }


        base64 = base64.trim()
        if (base64.startsWith("\"") && base64.endsWith("\"")) {
            base64 = base64.substring(1, base64.length - 1)
        }

        try {
            val decoded = Base64.getDecoder().decode(base64)
            val spec = PKCS8EncodedKeySpec(decoded)
            return KeyFactory.getInstance("RSA").generatePrivate(spec)
        } catch (e: Exception) {
            throw IllegalStateException("Invalid private key in env $envName", e)
        }
    }

}