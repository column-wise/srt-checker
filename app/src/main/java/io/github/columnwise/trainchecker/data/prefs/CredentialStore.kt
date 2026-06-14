package io.github.columnwise.trainchecker.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CredentialStore @Inject constructor(@ApplicationContext ctx: Context) {
    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        ctx,
        "train_credentials",
        MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    var srtId: String
        get() = prefs.getString("srt_id", "") ?: ""
        set(v) = prefs.edit().putString("srt_id", v).apply()

    var srtPw: String
        get() = prefs.getString("srt_pw", "") ?: ""
        set(v) = prefs.edit().putString("srt_pw", v).apply()

    var pollIntervalSeconds: Int
        get() = prefs.getInt("poll_interval", 15)
        set(v) = prefs.edit().putInt("poll_interval", v).apply()
}
