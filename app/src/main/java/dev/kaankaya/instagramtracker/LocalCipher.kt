package dev.kaankaya.instagramtracker

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** One owner of the Keystore AES-GCM envelope, with caller-selected record identity as AAD. */
internal class LocalCipher(private val alias: String, private val maxBytes: Int) {
    private fun key(): SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply {load(null)}
        (store.getKey(alias,null) as? SecretKey)?.let {return it}
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).setRandomizedEncryptionRequired(true).build())
        }.generateKey()
    }
    fun seal(value: ByteArray, aad: ByteArray): ByteArray {
        if(value.size>maxBytes)throw ApiFailure(ApiFailure.Kind.CAPACITY)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE,key());cipher.updateAAD(aad)
        return cipher.iv+cipher.doFinal(value)
    }
    fun open(value: ByteArray, aad: ByteArray): ByteArray {
        require(value.size in 29..maxBytes+28)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,value.copyOfRange(0,12)));cipher.updateAAD(aad)
        return cipher.doFinal(value.copyOfRange(12,value.size))
    }
    fun destroy() {KeyStore.getInstance("AndroidKeyStore").apply {load(null)}.deleteEntry(alias)}
}
