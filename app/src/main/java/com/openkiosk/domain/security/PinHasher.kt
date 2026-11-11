package com.openkiosk.domain.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Salted slow hash for the settings PIN, stored as `pbkdf2-sha256$iterations$salt$hash`
 * (Base64). The iteration count lives inside the string so it can be raised later without
 * invalidating PINs already stored.
 */
object PinHasher {
    // knob de campo: subir se o tablet verificar o PIN rapido demais (>100 ms); baixar se a
    // gaveta demorar a responder num aparelho lento. Um PIN de 4 digitos so tem 10^4 valores:
    // o hash lento encarece a forca bruta offline, quem protege de verdade e o bloqueio por tentativas.
    const val DEFAULT_ITERATIONS = 60_000

    private const val PREFIX = "pbkdf2-sha256"
    private const val SEP = '$'
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val SALT_BYTES = 16
    private const val KEY_BITS = 256

    // Teto para um valor lido do banco: um campo adulterado nao pode virar travamento de CPU.
    private const val MAX_ITERATIONS = 1_000_000

    private val random = SecureRandom()

    fun hash(pin: String, iterations: Int = DEFAULT_ITERATIONS): String {
        require(iterations in 1..MAX_ITERATIONS) { "iterations out of range" }
        val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
        val key = derive(pin, salt, iterations)
        val b64 = Base64.getEncoder()
        return listOf(PREFIX, iterations.toString(), b64.encodeToString(salt), b64.encodeToString(key))
            .joinToString(SEP.toString())
    }

    fun verify(pin: String, stored: String): Boolean {
        val parsed = parse(stored) ?: return false
        val candidate = derive(pin, parsed.salt, parsed.iterations)
        // isEqual compares every byte regardless of where the first difference is.
        return MessageDigest.isEqual(candidate, parsed.key)
    }

    fun isHashed(stored: String?): Boolean = stored != null && parse(stored) != null

    private class Parsed(val iterations: Int, val salt: ByteArray, val key: ByteArray)

    private fun parse(stored: String): Parsed? {
        val parts = stored.split(SEP)
        if (parts.size != 4 || parts[0] != PREFIX) return null
        val iterations = parts[1].toIntOrNull()?.takeIf { it in 1..MAX_ITERATIONS } ?: return null
        return try {
            val b64 = Base64.getDecoder()
            val salt = b64.decode(parts[2])
            val key = b64.decode(parts[3])
            if (salt.isEmpty() || key.size != KEY_BITS / 8) null else Parsed(iterations, salt, key)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun derive(pin: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
