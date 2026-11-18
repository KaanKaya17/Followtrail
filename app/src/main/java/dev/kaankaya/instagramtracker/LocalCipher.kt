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
        return cipher.iv+streamed(cipher,value,0)
    }
    fun open(value: ByteArray, aad: ByteArray): ByteArray {
        require(value.size in 29..maxBytes+28)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,value.copyOfRange(0,12)));cipher.updateAAD(aad)
        return streamed(cipher,value,12)
    }
    /**
     * Feed the Keystore in bounded chunks. On Android 6–7 a single large update/doFinal silently
     * corrupts or rejects AES-GCM data above roughly 100 KiB; chunked calls work on every API level.
     */
    private fun streamed(cipher: Cipher, input: ByteArray, from: Int): ByteArray {
        val output=java.io.ByteArrayOutputStream(input.size-from+16)
        var offset=from
        while(offset<input.size) {
            val count=minOf(CHUNK,input.size-offset)
            cipher.update(input,offset,count)?.let(output::write)
            offset+=count
        }
        output.write(cipher.doFinal())
        return output.toByteArray()
    }
    private companion object { const val CHUNK=16*1024 }
    fun destroy() {KeyStore.getInstance("AndroidKeyStore").apply {load(null)}.deleteEntry(alias)}
}
