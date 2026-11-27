package com.mobileagent.app

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * API Key 本地加密：Android Keystore 硬件密钥 + AES-256-GCM。
 * - 密钥生成在安全硬件（TEE/StrongBox）里，exportKey 拿不到明文，应用被备份/抠文件也解不开
 * - 落盘格式 "enc:v1:<iv>:<ciphertext>"（Base64）；无前缀视为历史明文，读取时兼容，写回时升级加密
 * - Keystore 不可用的极少数设备上回退明文存（与旧行为一致），不影响功能
 */
object ApiKeyVault {

    private const val KEY_ALIAS = "yansui_api_keys"
    private const val PREFIX = "enc:v1:"
    private const val GCM_TAG_BITS = 128

    /** 加密；失败返回 null（调用方回退明文） */
    fun encrypt(plain: String): String? = runCatching {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, obtainKey())
        val iv = cipher.iv
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val b64 = Base64.getEncoder()
        PREFIX + b64.encodeToString(iv) + ":" + b64.encodeToString(ct)
    }.getOrNull()

    /** 解密；无前缀的历史明文原样返回，损坏/解不开返回 null */
    fun decrypt(stored: String): String? {
        if (!stored.startsWith(PREFIX)) return stored.takeIf { it.isNotBlank() } // 旧版明文
        return runCatching {
            val b64 = Base64.getDecoder()
            val parts = stored.removePrefix(PREFIX).split(":")
            if (parts.size != 2) return null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, obtainKey(), GCMParameterSpec(GCM_TAG_BITS, b64.decode(parts[0])))
            String(cipher.doFinal(b64.decode(parts[1])), Charsets.UTF_8)
        }.getOrNull()
    }

    /** 是否已加密过 */
    fun isEncrypted(stored: String): Boolean = stored.startsWith(PREFIX)

    private fun obtainKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        // 首次生成：AES-256，仅本应用可用，GCM 加解密
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }
}
