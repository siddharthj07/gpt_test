package com.example.securevaultcamera

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class CryptoManager {

    fun encrypt(plain: ByteArray, passkey: String): ByteArray {
        val salt = ByteArray(SALT_SIZE).also { secureRandom.nextBytes(it) }
        val iv = ByteArray(IV_SIZE).also { secureRandom.nextBytes(it) }
        val key = deriveKey(passkey, salt)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val encrypted = cipher.doFinal(plain)

        return MAGIC + salt + iv + encrypted
    }

    fun decrypt(payload: ByteArray, passkey: String): ByteArray {
        require(payload.size > MAGIC.size + SALT_SIZE + IV_SIZE) { "Invalid encrypted payload" }
        require(payload.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) { "Unrecognized file" }

        var offset = MAGIC.size
        val salt = payload.copyOfRange(offset, offset + SALT_SIZE)
        offset += SALT_SIZE

        val iv = payload.copyOfRange(offset, offset + IV_SIZE)
        offset += IV_SIZE

        val encrypted = payload.copyOfRange(offset, payload.size)

        val key = deriveKey(passkey, salt)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(encrypted)
    }

    private fun deriveKey(passkey: String, salt: ByteArray): SecretKeySpec {
        val normalized = MessageDigest.getInstance("SHA-256").digest(passkey.toByteArray())
        val spec = PBEKeySpec(
            normalized.joinToString(separator = "") { "%02x".format(it) }.toCharArray(),
            salt,
            ITERATIONS,
            KEY_BITS
        )
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    companion object {
        private val MAGIC = "SVC1".toByteArray()
        private const val SALT_SIZE = 16
        private const val IV_SIZE = 12
        private const val GCM_TAG_BITS = 128
        private const val ITERATIONS = 120_000
        private const val KEY_BITS = 256
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private val secureRandom = SecureRandom()
    }
}
