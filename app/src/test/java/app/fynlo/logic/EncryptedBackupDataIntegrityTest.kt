package app.fynlo.logic

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.AEADBadTagException

/**
 * C22 (3.2.66) — EncryptedBackup unit tests. Pure-JVM (no Android deps);
 * runs in the regular `*DataIntegrity*` filter.
 *
 * GCM is non-deterministic by design (fresh nonce per encrypt), so the
 * round-trip and tampering assertions are written against the contract,
 * not specific byte sequences.
 */
class EncryptedBackupDataIntegrityTest {

    private val sample = """{"schemaVersion":2,"accounts":[],"transactions":[]}"""

    @Test
    fun roundTripRecoversPlaintextExactly() {
        val pw = "correct-horse-battery-staple"
        val ct = EncryptedBackup.encrypt(sample, pw)
        val pt = EncryptedBackup.decrypt(ct, pw)
        assertEquals(sample, pt)
    }

    @Test
    fun magicHeaderPrefixIsPresent() {
        val ct = EncryptedBackup.encrypt(sample, "pw")
        // First 10 bytes match "FYNLOENC1\n".
        val expected = "FYNLOENC1\n".toByteArray(Charsets.US_ASCII)
        assertArrayEquals(expected, ct.copyOf(expected.size))
    }

    @Test
    fun isEncryptedDetectsCiphertext() {
        val ct = EncryptedBackup.encrypt(sample, "pw")
        assertTrue(EncryptedBackup.isEncrypted(ct))
    }

    @Test
    fun isEncryptedRejectsPlainJson() {
        val plain = sample.toByteArray(Charsets.UTF_8)
        assertFalse(EncryptedBackup.isEncrypted(plain))
    }

    @Test
    fun isEncryptedRejectsTooShortBlob() {
        // Even an empty / 5-byte blob shouldn't crash isEncrypted.
        assertFalse(EncryptedBackup.isEncrypted(ByteArray(0)))
        assertFalse(EncryptedBackup.isEncrypted(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun wrongPasswordRaisesAuthError() {
        val ct = EncryptedBackup.encrypt(sample, "real-password")
        try {
            EncryptedBackup.decrypt(ct, "guess")
            error("Decryption should have failed for wrong password.")
        } catch (e: AEADBadTagException) {
            // expected
        }
    }

    @Test
    fun tamperedCiphertextRaisesAuthError() {
        val ct = EncryptedBackup.encrypt(sample, "pw")
        // Flip one byte deep in the ciphertext body — past the magic +
        // salt + nonce header. GCM auth tag should fail.
        ct[ct.size / 2] = (ct[ct.size / 2].toInt() xor 0x01).toByte()
        try {
            EncryptedBackup.decrypt(ct, "pw")
            error("Decryption should have failed for tampered ciphertext.")
        } catch (e: AEADBadTagException) {
            // expected
        }
    }

    @Test
    fun decryptRejectsBlobWithWrongMagic() {
        val notOurs = "PLAINJSONBYTES".toByteArray(Charsets.UTF_8) +
                      ByteArray(64) { it.toByte() }
        try {
            EncryptedBackup.decrypt(notOurs, "pw")
            error("Decryption should refuse non-Fynlo blobs.")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun encryptRejectsEmptyPassword() {
        try {
            EncryptedBackup.encrypt(sample, "")
            error("Empty password should be rejected.")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun twoEncryptsOfSamePlaintextDifferDueToFreshNonce() {
        // Same plaintext, same password → different ciphertexts because
        // the nonce (and salt) are fresh per encrypt. Critical for GCM
        // security; if these matched we'd be reusing (key, nonce) pairs.
        val a = EncryptedBackup.encrypt(sample, "pw")
        val b = EncryptedBackup.encrypt(sample, "pw")
        // Round-trip both still works.
        assertEquals(sample, EncryptedBackup.decrypt(a, "pw"))
        assertEquals(sample, EncryptedBackup.decrypt(b, "pw"))
        // The body (past the magic) must differ.
        val magicLen = "FYNLOENC1\n".length
        assertNotEquals(
            "Salt+nonce+ct must differ across encrypts of the same plaintext.",
            a.toList().drop(magicLen),
            b.toList().drop(magicLen),
        )
    }

    @Test
    fun roundTripWorksOnLargePayloads() {
        // Stress: 500 KB plaintext. A real backup is far smaller but this
        // checks GCM chunking doesn't lose any bytes.
        val big = "x".repeat(500_000)
        val ct = EncryptedBackup.encrypt(big, "pw")
        val pt = EncryptedBackup.decrypt(ct, "pw")
        assertEquals(big.length, pt.length)
        assertEquals(big, pt)
    }
}
