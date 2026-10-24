package com.gamehub.app.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class ApiConfigTest {
    @Test
    fun acceptsHttpsEndpointForRelease() {
        assertEquals(
            "https://api.example.test/",
            ApiConfig.validateBaseUrl("https://api.example.test/", allowInsecureDevelopment = false)
        )
        assertEquals(
            "https://api.example.test:8443/v1/",
            ApiConfig.validateBaseUrl("https://api.example.test:8443/v1/", allowInsecureDevelopment = false)
        )
        assertEquals(
            "https://api.example.test:65535/",
            ApiConfig.validateBaseUrl("https://api.example.test:65535/", allowInsecureDevelopment = false)
        )
        assertEquals(
            "https://localhost.example.test/",
            ApiConfig.validateBaseUrl("https://localhost.example.test/", allowInsecureDevelopment = false)
        )
    }

    @Test
    fun acceptsPublicIpv6Endpoint() {
        assertEquals(
            "https://[2001:4860:4860::8888]/",
            ApiConfig.validateBaseUrl("https://[2001:4860:4860::8888]/", allowInsecureDevelopment = false)
        )
    }

    @Test
    fun rejectsHttpEndpointForRelease() {
        assertInvalid("http://api.example.test/", allowInsecureDevelopment = false)
    }

    @Test
    fun rejectsLoopbackEndpointForRelease() {
        assertInvalid("https://127.0.0.1/", allowInsecureDevelopment = false)
        assertInvalid("https://127.25.1.2/", allowInsecureDevelopment = false)
        assertInvalid("https://localhost/", allowInsecureDevelopment = false)
        assertInvalid("https://localhost./", allowInsecureDevelopment = false)
        assertInvalid("https://api.localhost/", allowInsecureDevelopment = false)
        assertInvalid("https://api.localhost./", allowInsecureDevelopment = false)
        assertInvalid("https://localhost.localdomain/", allowInsecureDevelopment = false)
        assertInvalid("https://ip6-localhost/", allowInsecureDevelopment = false)
        assertInvalid("https://api.local/", allowInsecureDevelopment = false)
        assertInvalid("https://api.lan/", allowInsecureDevelopment = false)
        assertInvalid("https://api.internal/", allowInsecureDevelopment = false)
        assertInvalid("https://api.home.arpa/", allowInsecureDevelopment = false)
        assertInvalid("https://10.0.0.5/", allowInsecureDevelopment = false)
        assertInvalid("https://192.168.1.5/", allowInsecureDevelopment = false)
        assertInvalid("https://169.254.1.5/", allowInsecureDevelopment = false)
        assertInvalid("https://172.20.1.5/", allowInsecureDevelopment = false)
        assertInvalid("https://100.64.0.1/", allowInsecureDevelopment = false)
    }

    @Test
    fun rejectsIpv6AndIpv4MappedLoopbackEndpoints() {
        assertInvalid("https://[::1]/", allowInsecureDevelopment = false)
        assertInvalid("https://[::ffff:127.0.0.1]/", allowInsecureDevelopment = false)
        assertInvalid("https://[::ffff:7f00:1]/", allowInsecureDevelopment = false)
        assertInvalid("https://[::127.0.0.1]/", allowInsecureDevelopment = false)
        assertInvalid("https://[fc00::1]/", allowInsecureDevelopment = false)
    }

    @Test
    fun rejectsAlternateNumericIpv4LoopbackEndpoints() {
        assertInvalid("https://2130706433/", allowInsecureDevelopment = false)
        assertInvalid("https://127.1/", allowInsecureDevelopment = false)
        assertInvalid("https://0177.1/", allowInsecureDevelopment = false)
        assertInvalid("https://0x7f.0.0.1/", allowInsecureDevelopment = false)
        assertInvalid("https://0x7f000001/", allowInsecureDevelopment = false)
    }

    @Test
    fun rejectsMissingHostAndInvalidUrl() {
        assertInvalid("", allowInsecureDevelopment = true)
        assertInvalid("https:///path/", allowInsecureDevelopment = false)
        assertInvalid("not a url", allowInsecureDevelopment = false)
    }

    @Test
    fun rejectsCredentialsQueryFragmentAndMissingTrailingSlash() {
        assertInvalid("https://user:password@api.example.test/", allowInsecureDevelopment = false)
        assertInvalid("https://api.example.test/?token=secret", allowInsecureDevelopment = false)
        assertInvalid("https://api.example.test/#fragment", allowInsecureDevelopment = false)
        assertInvalid("https://api.example.test", allowInsecureDevelopment = false)
    }

    @Test
    fun allowsLocalHttpEndpointForDevelopment() {
        assertEquals(
            "http://10.0.2.2:3000/",
            ApiConfig.validateBaseUrl("http://10.0.2.2:3000/", allowInsecureDevelopment = true)
        )
    }

    @Test
    fun rejectsMissingAndBlankReleaseEndpoints() {
        assertInvalid("", allowInsecureDevelopment = false)
        assertInvalid("   ", allowInsecureDevelopment = false)
    }

    @Test
    fun validatesExplicitPorts() {
        assertEquals(
            "https://api.example.test:443/v1/",
            ApiConfig.validateBaseUrl("https://api.example.test:443/v1/", allowInsecureDevelopment = false)
        )
        assertInvalid("https://api.example.test:0/", allowInsecureDevelopment = false)
        assertInvalid("https://api.example.test:65536/", allowInsecureDevelopment = false)
        assertInvalid("https://api.example.test:-1/", allowInsecureDevelopment = false)
        assertInvalid("https://api.example.test:abc/", allowInsecureDevelopment = false)
        assertInvalid("https://api.example.test:/", allowInsecureDevelopment = false)
    }

    private fun assertInvalid(url: String, allowInsecureDevelopment: Boolean) {
        try {
            ApiConfig.validateBaseUrl(url, allowInsecureDevelopment)
            throw AssertionError("Expected invalid URL to be rejected: $url")
        } catch (_: IllegalArgumentException) {
            // Expected validation failure.
        }
    }
}
