package com.example.demo.key_provider

import com.cshield.sdk.api.exception.MissingSignatureHeaderException
import com.cshield.sdk.api.exception.TimeoutRequestException
import com.cshield.sdk.api.key_provider.client_public_key.ClientPublicKeyProvider
import com.cshield.sdk.api.key_provider.server_private_key.ServerPrivateKeyProvider
import com.cshield.sdk.api.request.RequestVerifier
import com.cshield.sdk.api.response.ResponseSigner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class CShieldOverrideConfig {

    @Bean
    fun publicKeyProvider(): ClientPublicKeyProvider =
        EnvPublicKeyProvider("CSHIELD_CLIENT_PUBLIC_KEY")

    @Bean
    fun privateKeyProvider(): ServerPrivateKeyProvider =
        EnvPrivateKeyProvider("CSHIELD_SERVER_PRIVATE_KEY")

    @Bean
    fun myVerifier(provider: ClientPublicKeyProvider): RequestVerifier {
        return object : RequestVerifier(provider.load()) {
            override fun verify(
                method: String?,
                path: String?,
                timestamp: String?,
                bodyHash: String?,
                signature: String?
            ) {
                if (path != null && path.startsWith("/non-authorize")) {
                    return
                }

                if (timestamp == null || signature == null) {
                    throw MissingSignatureHeaderException("cs-timestamp / cs-signature")
                }

                val diff = System.currentTimeMillis() / 1000 - timestamp.toLong()
                if (diff > 30) { //30s
                    throw TimeoutRequestException()
                }
                val payload = "$method.$path.$timestamp.$bodyHash"
                verifier.verify(payload, signature)
            }
        }
    }

    @Bean
    fun mySigner(provider: ServerPrivateKeyProvider): ResponseSigner {
        return object : ResponseSigner(provider.load()) {
            override fun sign(
                status: Int,
                path: String?,
                timestamp: String?,
                bodyHash: String?
            ): String? {
                if (path!!.startsWith("/non-authorize")) {
                    println(path)
                    return "";
                }
                val payload = "$status.$path.$timestamp.$bodyHash"
                return signer.sign(payload)
            }

        }
    }
}
