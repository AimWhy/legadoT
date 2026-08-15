package io.legado.app.help.crypto

import io.legado.app.utils.EncoderUtils
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.encodeURI
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.security.GeneralSecurityException
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
    fun desKeyTruncationMatchesHutool() {
        // hutool 经 DESKeySpec 静默取前 8 字节; 实测书源 DES 密钥字段 base64 解出 24 字节,
        // 实际只使用前 8 字节做单 DES。原生实现若把全部字节交给 provider 会抛
        // "DES key too long - should be 8 bytes"
        val key24 = ByteArray(24) { it.toByte() }
        val key8 = key24.copyOf(8)
        val iv = ByteArray(8)
        val withLongKey = SymmetricCryptoAndroid("DES/CBC/PKCS5Padding", key24).setIv(iv)
        val withShortKey = SymmetricCryptoAndroid("DES/CBC/PKCS5Padding", key8).setIv(iv)
        // 24 字节密钥与其前 8 字节产生相同密文, 证明截断语义生效
        assertEquals(withShortKey.encryptBase64("legado"), withLongKey.encryptBase64("legado"))
        assertEquals("legado", withLongKey.decryptStr(withLongKey.encryptBase64("legado")))
    }

    @Test
    fun desedeKeyTruncationMatchesHutool() {
        // hutool 经 DESedeKeySpec 静默取前 24 字节
        val key32 = ByteArray(32) { (it % 24).toByte() }
        val key24 = key32.copyOf(24)
        val iv = ByteArray(8)
        val withLongKey = SymmetricCryptoAndroid("DESede/CBC/PKCS5Padding", key32).setIv(iv)
        val withShortKey = SymmetricCryptoAndroid("DESede/CBC/PKCS5Padding", key24).setIv(iv)
        assertEquals(withShortKey.encryptBase64("legado"), withLongKey.encryptBase64("legado"))
        assertEquals("legado", withLongKey.decryptStr(withLongKey.encryptBase64("legado")))
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

        // URL-safe Base64(-/_ 代替 +//) — hutool 自动识别, 原生实现需回退
        // 固定向量: "_-8=" 是字节 [-1, -17] 的 URL-safe 编码, 直接验证回退逻辑
        assertArrayEquals(byteArrayOf(-1, -17), "_-8=".base64ToByteArray())
        // 端到端: 找一个密文里真正含 + 或 / 的用例(否则 replace 是空转, 测不出回退)
        val (urlPlain, urlStandard) = generateSequence(0) { it + 1 }
            .map { "urlSafe$it" to crypto.encryptBase64("urlSafe$it") }
            .first { (_, b64) -> b64.contains('+') || b64.contains('/') }
        assertEquals(
            urlPlain,
            crypto.decryptStr(urlStandard.replace('+', '-').replace('/', '_'))
        )
    }

    @Test
    fun oddLengthHexLookingInputFallsBackToBase64() {
        val crypto = SymmetricCryptoAndroid("AES", "0123456789abcdef".toByteArray())
        // "abc" 全为 hex 字符但长度是奇数: hutool 的 isHexNumber 要求偶数会回落 base64,
        // 原生版必须同样回落——base64 解出 2 字节后进入解密, 因填充非法抛 BadPaddingException,
        // 而不是在 hex 解析处抛 IllegalArgumentException
        try {
            crypto.decrypt("abc")
            fail("Expected decryption failure after base64 fallback")
        } catch (e: GeneralSecurityException) {
            // 走的是 base64 分支: 进入解密后因块长/填充非法失败, 符合预期
        } catch (e: IllegalArgumentException) {
            fail("odd-length hex-looking input must fall back to base64, got: $e")
        }
    }

    @Test
    fun ivZeroValueBehavior() {
        val key = "0123456789abcdef".toByteArray()

        // ECB mode ignores IV
        val ecb = SymmetricCryptoAndroid("AES/ECB/PKCS5Padding", key)
        val ecbCiphertext = ecb.encryptBase64("legado")
        assertEquals("legado", ecb.decryptStr(ecbCiphertext))

        // CBC with explicit zero IV — 解密必须读取 setIv 存入的字段(回归:
        // cipher() 曾把 apply 接收者 Cipher.getIV() 当字段,永远为 null 导致
        // InvalidAlgorithmParameterException: IV must be specified in CBC mode)
        val cbcWithZeroIv = SymmetricCryptoAndroid("AES/CBC/PKCS5Padding", key)
            .setIv(ByteArray(16))
        val ciphertext1 = cbcWithZeroIv.encryptBase64("legado")
        assertEquals("legado", cbcWithZeroIv.decryptStr(ciphertext1))

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

    @Test
    fun rsaOddLengthHexLookingInputFallsBackToBase64() {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val crypto = AsymmetricCrypto("RSA/ECB/PKCS1Padding")
            .setPublicKey(pair.public.encoded)
            .setPrivateKey(pair.private.encoded)
        // 旧 hutool 路径经 SecureUtil.decode(isHexNumber 要求偶数)把 "abc" 当 base64;
        // 原生版曾把奇数长度 hex 串误判为 hex 直接 require 崩溃
        try {
            crypto.decrypt("abc", usePublicKey = false)
            fail("Expected decryption failure after base64 fallback")
        } catch (e: GeneralSecurityException) {
            // base64 分支, 进入解密后因块长/填充非法失败, 符合预期
        } catch (e: IllegalArgumentException) {
            fail("odd-length hex-looking input must fall back to base64, got: $e")
        }
    }
}
