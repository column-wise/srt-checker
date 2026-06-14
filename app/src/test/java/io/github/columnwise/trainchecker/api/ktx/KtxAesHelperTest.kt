package io.github.columnwise.trainchecker.api.ktx

import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.*
import org.junit.Test

// Test the AES logic directly (can't use android.util.Base64 in unit tests)
class KtxAesHelperTest {
    private fun encrypt(password: String, key: String): String {
        val keyBytes = key.toByteArray(Charsets.UTF_8)
        val iv = key.substring(0, 16).toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), IvParameterSpec(iv))
        val encrypted = cipher.doFinal(password.toByteArray(Charsets.UTF_8))
        val b64once = Base64.getEncoder().encode(encrypted)
        return Base64.getEncoder().encodeToString(b64once)
    }

    @Test fun `encrypt produces non-empty string`() {
        val key = "1234567890abcdef"
        val result = encrypt("mypassword", key)
        assertTrue(result.isNotEmpty())
    }

    @Test fun `encrypt same input same key gives same output`() {
        val key = "1234567890abcdef"
        assertEquals(encrypt("password", key), encrypt("password", key))
    }

    @Test fun `encrypt different passwords differ`() {
        val key = "1234567890abcdef"
        assertNotEquals(encrypt("pass1", key), encrypt("pass2", key))
    }
}
