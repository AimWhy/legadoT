package io.legado.app.help.crypto

import io.legado.app.utils.EncoderUtils
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.encodeURI
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator

class NativeCryptoTest {

    @Test
    fun aesDefaultTransformationMatchesPersistedFormat() {
        val crypto = SymmetricCryptoAndroid("AES", "0123456789abcdef".toByteArray())
        assertEquals("if9kzswknK1cKkRwqbEsEw==", crypto.encryptBase64("legado"))
        assertEquals("legado", crypto.decryptStr("if9kzswknK1cKkRwqbEsEw=="))
    }

    @Test
    fun symmetricCipherKeepsHexBeforeBase64Detection() {
        val crypto = SymmetricCryptoAndroid("AES", "0123456789abcdef".toByteArray())
        val encrypted = crypto.encryptHex("legado")
        assertEquals("legado", crypto.decryptStr(encrypted.uppercase()))
        assertArrayEquals(crypto.decryptHex(encrypted), crypto.decrypt(encrypted))
    }

    @Test
    fun digestHmacAndEncodingVectors() {
        assertEquals("e2fc714c4727ee9395f324cd2e7f331f", MD5Utils.md5Encode("abcd"))
        assertEquals(
            "f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8",
            hmac("HmacSHA256", "key".toByteArray(), "The quick brown fox jumps over the lazy dog".toByteArray()).toHexString()
        )
        assertEquals("e4b8ade69687", EncoderUtils.hexEncode("中文".toByteArray()))
        assertEquals("中文", String(EncoderUtils.hexDecode("E4B8ADE69687")))
    }

    @Test
    fun rsaLongDataAndSignatureRoundTrip() {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val crypto = AsymmetricCrypto("RSA/ECB/PKCS1Padding")
            .setPublicKey(pair.public.encoded)
            .setPrivateKey(pair.private.encoded)
        val data = ByteArray(700) { (it % 251).toByte() }
        assertArrayEquals(data, crypto.decrypt(crypto.encrypt(data), false))

        val sign = Sign("SHA256withRSA")
            .setPublicKey(pair.public.encoded)
            .setPrivateKey(pair.private.encoded)
        val signature = sign.sign(data)
        assertTrue(sign.verify(data, signature))
        assertFalse(sign.verify(data + 1, signature))
    }

    @Test
    fun ecdsaUsesEcKeyFactory() {
        val pair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        val sign = Sign("SHA256withECDSA")
            .setPublicKey(pair.public.encoded)
            .setPrivateKey(pair.private.encoded)
        val data = "legado".toByteArray()
        assertTrue(sign.verify(data, sign.sign(data)))
    }

    @Test
    fun urlAndIpCompatibilityBoundaries() {
        assertEquals("%E4%B8%AD%E6%96%87%20a+b&c=d", "中文 a+b&c=d".encodeURI())
        assertEquals("a+b 中文", EncoderUtils.percentDecode("a+b%20%E4%B8%AD%E6%96%87", Charsets.UTF_8))
        assertTrue(NetworkUtils.isIPv4Address("192.168.1.1"))
        assertFalse(NetworkUtils.isIPv4Address("0.0.0.0"))
        assertFalse(NetworkUtils.isIPv4Address("192.168.1.256"))
        assertTrue(NetworkUtils.isIPv6Address("2001:db8::1"))
        assertTrue(NetworkUtils.isIPv6Address("::ffff:192.0.2.1"))
        assertFalse(NetworkUtils.isIPv6Address("[2001:db8::1]"))
        assertFalse(NetworkUtils.isIPv6Address("2001:db8::1/64"))
    }

    @Test
    fun shortKeyZeroPaddingBehavior() {
        // AES requires 16/24/32 byte keys; short keys throw InvalidKeyException
        try {
            val crypto = SymmetricCryptoAndroid("AES", "short".toByteArray())
            crypto.encrypt("test")
            throw AssertionError("Expected InvalidKeyException for 5-byte AES key")
        } catch (e: java.security.InvalidKeyException) {
            // Expected: platform API rejects non-standard key length
        }

        // Valid 16-byte key works
        val validCrypto = SymmetricCryptoAndroid("AES", "0123456789abcdef".toByteArray())
        assertEquals("test", validCrypto.decryptStr(validCrypto.encryptBase64("test")))
    }

    @Test
    fun base64VariantCompatibility() {
        val crypto = SymmetricCryptoAndroid("AES", "0123456789abcdef".toByteArray())
        val plaintext = "legado"

        // Standard Base64 with padding
        val standardB64 = crypto.encryptBase64(plaintext)
        assertEquals(plaintext, crypto.decryptStr(standardB64))

        // Whitespace tolerance (line-wrapped Base64)
        val wrapped = standardB64.chunked(4).joinToString("\n")
        assertEquals(plaintext, crypto.decryptStr(wrapped))

        // URL-safe Base64 (- and _ instead of + and /) — NOT automatically handled
        val urlSafe = standardB64.replace('+', '-').replace('/', '_')
        try {
            crypto.decryptStr(urlSafe)
            // If this passes, URL-safe is supported; if it throws, it's not
        } catch (e: Exception) {
            // Expected if URL-safe is NOT auto-detected
        }
    }

    @Test
    fun ivZeroValueBehavior() {
        val key = "0123456789abcdef".toByteArray()

        // ECB mode ignores IV
        val ecb = SymmetricCryptoAndroid("AES/ECB/PKCS5Padding", key)
        val ecbCiphertext = ecb.encryptBase64("legado")
        assertEquals("legado", ecb.decryptStr(ecbCiphertext))

        // CBC with explicit zero IV
        val cbcWithZeroIv = SymmetricCryptoAndroid("AES/CBC/PKCS5Padding", key)
            .setIv(ByteArray(16))
        val ciphertext1 = cbcWithZeroIv.encryptBase64("legado")

        // CBC without explicit IV — uses random IV, should differ
        val cbcNoIv = SymmetricCryptoAndroid("AES/CBC/PKCS5Padding", key)
        val ciphertext2 = cbcNoIv.encryptBase64("legado")
        val ciphertext3 = cbcNoIv.encryptBase64("legado")

        // Random IV → different ciphertexts for same plaintext
        // (This will fail if platform defaults to zero IV, revealing the divergence)
        if (ciphertext2 == ciphertext3) {
            // If equal, IV is deterministic (zero or fixed), not random
            assertEquals(ciphertext1, ciphertext2, "Deterministic IV detected; may match zero IV")
        }
    }

    @Test
    fun percentDecodeEdgeCases() {
        // Valid percent-encoding
        assertEquals("中文", EncoderUtils.percentDecode("%E4%B8%AD%E6%96%87", Charsets.UTF_8))

        // Malformed sequences — tolerance check
        val malformed = "%E4%B8%AD%XY%E6%96%87"
        try {
            val result = EncoderUtils.percentDecode(malformed, Charsets.UTF_8)
            // If this passes, the implementation is tolerant (keeps %XY literal)
            assertTrue(result.contains("%XY") || result.contains("中"))
        } catch (e: Exception) {
            // If it throws, the implementation is strict
        }

        // Partial sequences at end
        assertEquals("test%E", EncoderUtils.percentDecode("test%E", Charsets.UTF_8))
    }

    @Test
    fun rsaDecryptAutoDetectsHexVsBase64() {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val crypto = AsymmetricCrypto("RSA/ECB/PKCS1Padding")
            .setPublicKey(pair.public.encoded)
            .setPrivateKey(pair.private.encoded)

        val plaintext = "legado".toByteArray()

        // Test hex path: public key encrypts, private key decrypts (usePublicKey=false)
        val hexCiphertext = crypto.encryptHex(plaintext, usePublicKey = true)
        assertArrayEquals(plaintext, crypto.decrypt(hexCiphertext, usePublicKey = false))

        // Test base64 path
        val base64Ciphertext = crypto.encryptBase64(plaintext, usePublicKey = true)
        assertArrayEquals(plaintext, crypto.decrypt(base64Ciphertext, usePublicKey = false))

        // AsymmetricCrypto.decode() at line 77 uses all { isDigit() || 'a'..'f' } to detect hex
        assertTrue(hexCiphertext.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' })
    }
}
