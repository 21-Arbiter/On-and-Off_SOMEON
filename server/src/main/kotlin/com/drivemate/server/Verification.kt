package com.drivemate.server

import java.time.LocalDate
import java.time.Period
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Mock identity verification (a real service would be PASS or similar): the phone page
 * says who it is and when they were born; 19 or over passes. The server signs the user
 * id so a car can prove it later, also after a restart. VERIFY_SECRET signs in production;
 * the fallback is for local runs.
 */
class Verification(secret: String = System.getenv("VERIFY_SECRET") ?: "drive-mate-local") {
    private val key = SecretKeySpec(secret.toByteArray(), "HmacSHA256")

    fun token(userId: String): String = Mac.getInstance("HmacSHA256").run {
        init(key)
        doFinal(userId.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    fun valid(userId: String, token: String?) = token != null && token == token(userId)

    companion object {
        const val MIN_AGE = 19

        fun oldEnough(born: LocalDate, today: LocalDate = LocalDate.now()) = Period.between(born, today).years >= MIN_AGE
    }
}
