package com.xxt.board.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 学习通 Cookie 的本地保险箱：AES-256-GCM，密钥在 Android Keystore（不可导出）。
 * 只存密文，备份已在 manifest 里关掉。
 */
class CookieVault(context: Context) {

    private val prefs = context.getSharedPreferences("secure", Context.MODE_PRIVATE)

    /** AndroidKeyStore 首次访问可能很慢；解密结果只算一次，save/clear 时同步失效 */
    @Volatile
    private var cached: String? = null

    fun save(cookie: String) {
        if (cookie.isBlank()) return
        val cipher = Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val cipherText = cipher.doFinal(cookie.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("ct", Base64.encodeToString(cipherText, Base64.NO_WRAP))
            .apply()
        cached = cookie
    }

    fun load(): String {
        cached?.let { return it }
        val iv = prefs.getString("iv", null) ?: return ""
        val ct = prefs.getString("ct", null) ?: return ""
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(
                Cipher.DECRYPT_MODE,
                key(),
                GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)),
            )
            String(cipher.doFinal(Base64.decode(ct, Base64.NO_WRAP)), Charsets.UTF_8)
        }.getOrDefault("").also { cached = it }
    }

    fun clear() {
        prefs.edit().clear().apply()
        cached = ""
    }

    fun hasCookie(): Boolean = load().contains("_d=")

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ALIAS = "xxt_cookie"
        const val TRANSFORM = "AES/GCM/NoPadding"
    }
}
