package com.example.demo.key_provider

import com.cshield.sdk.key_provider.PrivateKeyProvider
import com.cshield.sdk.key_provider.PublicKeyProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class CShieldKeyOverrideConfig {

    @Bean
    fun publicKeyProvider(): PublicKeyProvider =
        EnvPublicKeyProvider("CSHIELD_CLIENT_PUBLIC_KEY")

    @Bean
    fun privateKeyProvider(): PrivateKeyProvider =
        EnvPrivateKeyProvider("CSHIELD_SERVER_PRIVATE_KEY")
}
