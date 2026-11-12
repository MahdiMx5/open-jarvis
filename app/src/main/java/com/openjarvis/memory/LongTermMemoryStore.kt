package com.openjarvis.memory

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class LongTermMemory(
    val id: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * Local-first long-term memory.
 *
 * The encrypted payload is stored in app-private SharedPreferences. Its AES key is
 * non-exportable and protected by Android Keystore. Cloud backup must only receive
 * ciphertext and must not be enabled until a user-controlled recovery-key flow and
 * Firebase security rules are in place.
 */
class LongTermMemoryStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun remember(rawContent: String): Boolean = synchronized(lock) {
        val content = rawContent.trim().take(MAX_CONTENT_LENGTH)
        if (content.isBlank() || containsSensitiveCredential(content)) return@synchronized false

        val current = readMemories().toMutableList()
        val existingIndex = current.indexOfFirst {
            it.content.equals(content, ignoreCase = true)
        }
        val now = System.currentTimeMillis()
        if (existingIndex >= 0) {
            current[existingIndex] = current[existingIndex].copy(updatedAt = now)
        } else {
            current.add(
                LongTermMemory(
                    id = UUID.randomUUID().toString(),
                    content = content,
                    createdAt = now,
                    updatedAt = now
                )
            )
        }

        writeMemories(current.sortedByDescending { it.updatedAt }.take(MAX_MEMORIES))
    }

    fun getAll(): List<LongTermMemory> = synchronized(lock) {
        readMemories().sortedByDescending { it.updatedAt }
    }

    fun delete(memoryId: String): Boolean = synchronized(lock) {
        val current = readMemories()
        val updated = current.filterNot { it.id == memoryId }
        if (updated.size == current.size) return@synchronized false
        writeMemories(updated)
    }

    fun clearAll(): Boolean = synchronized(lock) {
        writeMemories(emptyList())
    }

    fun buildContext(query: String, limit: Int = 10): String = synchronized(lock) {
        val memories = readMemories()
        if (memories.isEmpty()) return@synchronized ""

        val normalized = query.lowercase()
        val terms = normalized
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length >= 3 }
            .distinct()

        val ranked = memories.map { memory ->
            val content = memory.content.lowercase()
            val score = terms.count { term -> content.contains(term) }
            memory to score
        }.filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<LongTermMemory, Int>> { it.second }
                .thenByDescending { it.first.updatedAt })
            .take(limit)
            .map { it.first }

        val selected = if (ranked.isNotEmpty()) ranked else if (isRecallRequest(normalized)) {
            memories.sortedByDescending { it.updatedAt }.take(limit)
        } else {
            emptyList()
        }

        selected.joinToString("\n") { "- ${it.content.take(500)}" }
    }

    private fun readMemories(): List<LongTermMemory> {
        val encrypted = preferences.getString(KEY_DATA, null) ?: return emptyList()
        val plaintext = decrypt(encrypted)
        val array = JSONArray(plaintext)
        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            LongTermMemory(
                id = item.getString("id"),
                content = item.getString("content"),
                createdAt = item.getLong("createdAt"),
                updatedAt = item.getLong("updatedAt")
            )
        }
    }

    private fun writeMemories(memories: List<LongTermMemory>): Boolean {
        val array = JSONArray()
        memories.forEach { memory ->
            array.put(
                JSONObject().apply {
                    put("id", memory.id)
                    put("content", memory.content)
                    put("createdAt", memory.createdAt)
                    put("updatedAt", memory.updatedAt)
                }
            )
        }
        val encrypted = encrypt(array.toString())
        return preferences.edit().putString(KEY_DATA, encrypted).commit()
    }

    private fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
        val payload = cipher.iv + ciphertext
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val payload = Base64.decode(encoded, Base64.NO_WRAP)
        require(payload.size > GCM_IV_LENGTH) { "Encrypted memory payload is invalid" }
        val iv = payload.copyOfRange(0, GCM_IV_LENGTH)
        val ciphertext = payload.copyOfRange(GCM_IV_LENGTH, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val PREFERENCES = "jarvis_long_term_memory"
        private const val KEY_DATA = "encrypted_memories"
        private const val KEY_ALIAS = "open_jarvis_long_term_memory_v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_BITS = 128
        private const val MAX_CONTENT_LENGTH = 1000
        private const val MAX_MEMORIES = 500
        private val lock = Any()

        private val rememberPrefixes = listOf(
            "یادت باشه", "یادت باشد", "به خاطر بسپار", "بخاطر بسپار",
            "این رو یادت بمونه", "این را یادت بماند", "فراموش نکن",
            "remember that", "remember this", "please remember"
        )

        fun extractRememberRequest(command: String): String? {
            val value = command.trim()
            val prefix = rememberPrefixes.firstOrNull {
                value.startsWith(it, ignoreCase = true)
            } ?: return null
            return value.substring(prefix.length)
                .trim()
                .trimStart(':', '،', ',', '-', ' ')
                .takeIf { it.isNotBlank() }
        }

        private fun containsSensitiveCredential(value: String): Boolean {
            val normalized = value.lowercase()
            val sensitiveTerms = listOf(
                "password", "passcode", "one-time password", "otp", "verification code",
                "credit card", "debit card", "cvv", "cvc", "bank account",
                "رمز عبور", "رمز ورود", "رمز کارت", "کد یکبار مصرف", "کد یک‌بار مصرف",
                "کد تایید", "کد تأیید", "شماره کارت", "اطلاعات بانکی"
            )
            return sensitiveTerms.any { normalized.contains(it) }
        }

        private fun isRecallRequest(value: String): Boolean {
            return listOf(
                "چی یادت", "چه چیزی یادت", "چی به خاطر", "خاطراتم",
                "what do you remember", "recall my memories", "show my memories"
            ).any { value.contains(it) }
        }
    }
}
