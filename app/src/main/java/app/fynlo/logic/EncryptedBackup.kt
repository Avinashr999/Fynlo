package app.fynlo.logic

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * C22 (3.2.66) — password-based encryption for backup JSON.
 *
 * Wire format (all big-endian, no length prefixes — fields are fixed-size):
 *
 *   ┌────────────┬──────┬───────┬────────────────────────────────┐
 *   │  magic     │ salt │ nonce │  ciphertext + 16-byte GCM tag  │
 *   │  10 bytes  │  16  │  12   │  variable                      │
 *   └────────────┴──────┴───────┴────────────────────────────────┘
 *   magic = "FYNLOENC1\n"   (ASCII, lets restore auto-detect vs plain JSON)
 *
 * Crypto choices:
 *   - **PBKDF2WithHmacSHA256** for key derivation, 250 000 iterations
 *     (OWASP 2023 guidance for SHA-256). The salt is fresh per export.
 *   - **AES-256-GCM** for confidentiality + integrity. GCM's auth tag means
 *     a wrong password OR a tampered ciphertext both raise
 *     `AEADBadTagException` on decrypt — no need for a separate HMAC.
 *   - Fresh **12-byte nonce** per export (NIST SP 800-38D §8.2 — never
 *     reuse a (key, nonce) pair under GCM).
 *
 * Pure-JVM via `javax.crypto` — no new dependencies, runs unchanged on
 * any Android version we ship for (minSdk 26 supports PBKDF2WithHmacSHA256).
 */
object EncryptedBackup {

    private val MAGIC: ByteArray = "FYNLOENC1\n".toByteArray(Charsets.US_ASCII)
    private const val SALT_BYTES   = 16
    private const val NONCE_BYTES  = 12
    private const val GCM_TAG_BITS = 128
    private const val PBKDF_ITERS  = 250_000
    private const val KEY_BITS     = 256

    /** True if [blob] looks like a Fynlo-encrypted backup (header match). */
    fun isEncrypted(blob: ByteArray): Boolean =
        blob.size >= MAGIC.size && MAGIC.indices.all { blob[it] == MAGIC[it] }

    /**
     * Encrypts [plaintext] under [password]. Caller is expected to keep
     * the password secret + recoverable — there is no recovery path.
     */
    fun encrypt(plaintext: String, password: String): ByteArray {
        require(password.isNotEmpty()) { "Password must not be empty" }
        val rng = SecureRandom()
        val salt  = ByteArray(SALT_BYTES).also(rng::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(rng::nextBytes)
        val key   = deriveKey(password, salt)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, nonce))
        val ct = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        val out = ByteArray(MAGIC.size + SALT_BYTES + NONCE_BYTES + ct.size)
        System.arraycopy(MAGIC,  0, out, 0,                               MAGIC.size)
        System.arraycopy(salt,   0, out, MAGIC.size,                      SALT_BYTES)
        System.arraycopy(nonce,  0, out, MAGIC.size + SALT_BYTES,         NONCE_BYTES)
        System.arraycopy(ct,     0, out, MAGIC.size + SALT_BYTES + NONCE_BYTES, ct.size)
        return out
    }

    /**
     * Decrypts a Fynlo-encrypted backup. Throws:
     *  - [IllegalArgumentException] if the magic header doesn't match (caller
     *    should fall back to the plain-JSON restore path).
     *  - [javax.crypto.AEADBadTagException] if the password is wrong OR the
     *    ciphertext was tampered with — caller surfaces "wrong password or
     *    corrupted backup".
     */
    fun decrypt(blob: ByteArray, password: String): String {
        require(isEncrypted(blob)) { "Not a Fynlo Ledger encrypted backup" }
        require(password.isNotEmpty()) { "Password must not be empty" }
        require(blob.size >= MAGIC.size + SALT_BYTES + NONCE_BYTES) { "Backup truncated" }

        val salt  = blob.copyOfRange(MAGIC.size, MAGIC.size + SALT_BYTES)
        val nonce = blob.copyOfRange(MAGIC.size + SALT_BYTES, MAGIC.size + SALT_BYTES + NONCE_BYTES)
        val ct    = blob.copyOfRange(MAGIC.size + SALT_BYTES + NONCE_BYTES, blob.size)

        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, nonce))
        val pt = cipher.doFinal(ct)
        return String(pt, Charsets.UTF_8)
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF_ITERS, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val raw = factory.generateSecret(spec).encoded
        return SecretKeySpec(raw, "AES")
    }
}
