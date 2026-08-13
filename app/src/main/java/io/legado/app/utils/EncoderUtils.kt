package io.legado.app.utils

import android.util.Base64

/**
 * 编码工具 escape base64
 */
@Suppress("unused")
object EncoderUtils {

    private const val HEX = "0123456789abcdef"
    private const val UPPER_HEX = "0123456789ABCDEF"

    fun hexEncode(bytes: ByteArray): String = buildString(bytes.size * 2) {
        bytes.forEach { byte ->
            val value = byte.toInt() and 0xff
            append(HEX[value ushr 4])
            append(HEX[value and 0x0f])
        }
    }

    fun hexDecode(value: String): ByteArray {
        require(value.length % 2 == 0) { "Hex input must contain an even number of characters" }
        return ByteArray(value.length / 2) { index ->
            value.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    fun percentEncode(value: String, charset: java.nio.charset.Charset, safe: String): String {
        val safeAscii = BooleanArray(128)
        safe.forEach { if (it.code < safeAscii.size) safeAscii[it.code] = true }
        return buildString {
            value.toByteArray(charset).forEach { byte ->
                val unsigned = byte.toInt() and 0xff
                if (unsigned < safeAscii.size && safeAscii[unsigned]) {
                    append(unsigned.toChar())
                } else {
                    append('%')
                    append(UPPER_HEX[unsigned ushr 4])
                    append(UPPER_HEX[unsigned and 0x0f])
                }
            }
        }
    }

    fun percentDecode(value: String, charset: java.nio.charset.Charset): String {
        val output = java.io.ByteArrayOutputStream(value.length)
        var index = 0
        while (index < value.length) {
            if (value[index] == '%' && index + 2 < value.length) {
                val decoded = value.substring(index + 1, index + 3).toIntOrNull(16)
                if (decoded != null) {
                    output.write(decoded)
                    index += 3
                    continue
                }
            }
            val codePoint = value.codePointAt(index)
            output.write(String(Character.toChars(codePoint)).toByteArray(charset))
            index += Character.charCount(codePoint)
        }
        return output.toByteArray().toString(charset)
    }

    fun escape(src: String): String {
        val tmp = StringBuilder()
        for (char in src) {
            val charCode = char.code
            if (charCode in 48..57 || charCode in 65..90 || charCode in 97..122) {
                tmp.append(char)
                continue
            }

            val prefix = when {
                charCode < 16 -> "%0"
                charCode < 256 -> "%"
                else -> "%u"
            }
            tmp.append(prefix).append(charCode.toString(16))
        }
        return tmp.toString()
    }

    @JvmOverloads
    fun base64Decode(str: String, flags: Int = Base64.DEFAULT): String {
        val bytes = Base64.decode(str, flags)
        return String(bytes)
    }

    @JvmOverloads
    fun base64Encode(str: String, flags: Int = Base64.NO_WRAP): String? {
        return Base64.encodeToString(str.toByteArray(), flags)
    }

    @JvmOverloads
    fun base64Encode(bytes: ByteArray, flags: Int = Base64.NO_WRAP): String {
        return Base64.encodeToString(bytes, flags)
    }
    
    @JvmOverloads
    fun base64DecodeToByteArray(str: String, flags: Int = Base64.DEFAULT): ByteArray {
        return Base64.decode(str, flags)
    }

}
