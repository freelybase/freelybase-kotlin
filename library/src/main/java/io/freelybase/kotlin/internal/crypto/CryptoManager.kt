package io.freelybase.kotlin.internal.crypto

import android.util.Base64
import io.freelybase.kotlin.FreelyException
import io.freelybase.kotlin.internal.http.RawHttpClient
import java.security.KeyFactory
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal object CryptoManager {

    private var rsaPublicKey: java.security.PublicKey? = null

    suspend fun fetchPublicKey() {
        setPublicKeyPem(RawHttpClient.getPublicKeyPem())
    }

    fun setPublicKeyPem(pem: String) {
        val stripped = pem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\\s".toRegex(), "")
        val der = Base64.decode(stripped, Base64.DEFAULT)
        rsaPublicKey = KeyFactory.getInstance("RSA")
            .generatePublic(X509EncodedKeySpec(der))
    }

    fun isReady() = rsaPublicKey != null

    fun encryptRequest(plainJson: String, method: String, path: String): Pair<Map<String, String>, SecretKey> {
        val pub = rsaPublicKey ?: throw FreelyException("加密公钥未就绪")
        val aesKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val aad = "$method:$path".toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(128, iv))
        cipher.updateAAD(aad)
        val ct = cipher.doFinal(plainJson.toByteArray(Charsets.UTF_8))
        val rsaCipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
        rsaCipher.init(Cipher.ENCRYPT_MODE, pub)
        val ek = rsaCipher.doFinal(aesKey.encoded)
        return mapOf("ek" to b64e(ek), "iv" to b64e(iv), "ct" to b64e(ct)) to aesKey
    }

    fun decryptResponse(envelope: Map<String, String>, aesKey: SecretKey, method: String, path: String): String {
        val iv = b64d(envelope["iv"] ?: error("missing iv"))
        val ct = b64d(envelope["ct"] ?: error("missing ct"))
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, aesKey, GCMParameterSpec(128, iv))
        cipher.updateAAD("$method:$path:resp".toByteArray(Charsets.UTF_8))
        return cipher.doFinal(ct).toString(Charsets.UTF_8)
    }

    private fun b64e(data: ByteArray) = Base64.encodeToString(data, Base64.NO_WRAP)
    private fun b64d(data: String) = Base64.decode(data, Base64.DEFAULT)
}
