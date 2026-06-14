package io.github.columnwise.trainchecker.data.api.ktx

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KtxAesHelper @Inject constructor() {
    fun encrypt(password: String, key: String): String {
        val keyBytes = key.toByteArray(Charsets.UTF_8)
        val iv = key.substring(0, 16).toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), IvParameterSpec(iv))
        val encrypted = cipher.doFinal(password.toByteArray(Charsets.UTF_8))
        val b64once = Base64.encode(encrypted, Base64.NO_WRAP)
        return Base64.encodeToString(b64once, Base64.NO_WRAP)
    }
}
