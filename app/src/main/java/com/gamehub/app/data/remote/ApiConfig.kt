package com.gamehub.app.data.remote

import com.gamehub.app.BuildConfig
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

/** Validates the variant-specific API endpoint before Retrofit is created. */
object ApiConfig {
    val BASE_URL: String = validateBaseUrl(BuildConfig.API_BASE_URL, allowInsecureDevelopment = BuildConfig.DEBUG)

    internal fun validateBaseUrl(baseUrl: String, allowInsecureDevelopment: Boolean): String {
        require(baseUrl.isNotBlank()) { "The API base URL is not configured." }
        require(baseUrl.endsWith('/')) { "The API base URL must end with '/'." }

        val uri = try {
            URI(baseUrl)
        } catch (error: URISyntaxException) {
            throw IllegalArgumentException("The API base URL must be a valid absolute URL.", error)
        }
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        val host = uri.host?.lowercase(Locale.ROOT)?.removePrefix("[")?.removeSuffix("]")
        require(uri.isAbsolute && host != null) { "The API base URL must be an absolute URL with a hostname." }
        require(uri.userInfo == null && uri.query == null && uri.fragment == null) {
            "The API base URL must not contain credentials, a query or a fragment."
        }
        require(uri.port == -1 || uri.port in 1..65535) { "The API base URL port must be between 1 and 65535." }
        require(uri.port != -1 || !uri.rawAuthority.orEmpty().endsWith(':')) {
            "The API base URL port is malformed."
        }

        if (!allowInsecureDevelopment) {
            require(scheme == "https") { "Release builds require an HTTPS API base URL." }
            require(!host.isLocalOnlyHost()) { "Release builds must not use a local-only API base URL." }
        } else {
            require(scheme == "https" || scheme == "http") { "The API base URL must use HTTP or HTTPS." }
        }

        return baseUrl
    }

    private fun String.isLocalOnlyHost(): Boolean {
        val host = trimEnd('.')
        if (host == "localhost" || host.endsWith(".localhost") ||
            host == "localhost.localdomain" || host.endsWith(".localhost.localdomain") ||
            host == "ip6-localhost" || host == "ip6-loopback" ||
            host.endsWith(".local") || host.endsWith(".lan") ||
            host.endsWith(".internal") || host.endsWith(".home.arpa") ||
            host.endsWith(".intranet") ||
            ('.' !in host && ':' !in host)
        ) {
            return true
        }

        val ipv4 = parseLegacyIpv4(host)
        if (ipv4 != null) return ipv4.isLocalOnlyIpv4()

        if (':' !in host) return false
        val address = try {
            InetAddress.getByName(host)
        } catch (error: Exception) {
            throw IllegalArgumentException("The API base URL contains an invalid IP address.", error)
        }
        val bytes = address.address
        if (address.isAnyLocalAddress || address.isLoopbackAddress ||
            address.isLinkLocalAddress || address.isSiteLocalAddress
        ) {
            return true
        }
        if (address is Inet4Address) {
            val value = bytes.fold(0L) { acc, byte -> (acc shl 8) or (byte.toLong() and 0xff) }
            return value.isLocalOnlyIpv4()
        }
        if (address is Inet6Address && bytes.size == 16) {
            val uniqueLocal = bytes[0].toInt() and 0xfe == 0xfc
            val mappedIpv4 = bytes.sliceArray(0 until 10).all { it == 0.toByte() } &&
                bytes[10] == 0xff.toByte() && bytes[11] == 0xff.toByte()
            val compatibleIpv4 = bytes.sliceArray(0 until 12).all { it == 0.toByte() }
            if (uniqueLocal) return true
            if (mappedIpv4 || compatibleIpv4) {
                val value = bytes.copyOfRange(12, 16)
                    .fold(0L) { acc, byte -> (acc shl 8) or (byte.toLong() and 0xff) }
                return value.isLocalOnlyIpv4()
            }
        }
        return false
    }

    private fun Long.isLocalOnlyIpv4(): Boolean {
        val first = (this ushr 24).toInt()
        val second = (this ushr 16 and 0xff).toInt()
        return first == 0 || first == 10 || first == 127 ||
            first == 169 && second == 254 ||
            first == 172 && second in 16..31 ||
            first == 192 && second == 168 ||
            first == 100 && second in 64..127
    }

    private fun parseLegacyIpv4(host: String): Long? {
        val isNumericDottedAddress = host.all { it.isDigit() || it == '.' }
        val isHexAddress = host.contains("0x", ignoreCase = true) &&
            host.all { it.isDigit() || it == '.' || it.lowercaseChar() in 'a'..'f' || it.lowercaseChar() == 'x' }
        if (!isNumericDottedAddress && !isHexAddress) {
            return null
        }
        val parts = host.split('.')
        if (parts.isEmpty() || parts.size > 4 || parts.any(String::isEmpty)) {
            throw IllegalArgumentException("The API base URL contains an invalid IPv4 address.")
        }
        val values = parts.map { part ->
            val (digits, radix) = when {
                part.startsWith("0x", ignoreCase = true) -> part.drop(2) to 16
                part.length > 1 && part.startsWith('0') -> part.drop(1) to 8
                else -> part to 10
            }
            if (digits.isEmpty()) 0L else digits.toLongOrNull(radix)
                ?: throw IllegalArgumentException("The API base URL contains an invalid IPv4 address.")
        }
        for (index in 0 until values.lastIndex) {
            require(values[index] <= 255) { "The API base URL contains an invalid IPv4 address." }
        }
        val lastBits = 8 * (5 - values.size)
        require(values.last() < (1L shl lastBits)) { "The API base URL contains an invalid IPv4 address." }
        var result = 0L
        for (index in 0 until values.lastIndex) {
            result = (result shl 8) or values[index]
        }
        result = (result shl lastBits) or values.last()
        return result
    }
}
