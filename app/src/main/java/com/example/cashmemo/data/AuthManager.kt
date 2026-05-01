package com.example.cashmemo.data

import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

/**
 * Manages Firebase Authentication.
 * Uses anonymous sign-in so the app works without a login screen
 * while still scoping all Firestore data to a per-device user ID.
 * Later this can be upgraded to Google Sign-In to enable cross-device sync.
 */
class AuthManager {

    private val auth = Firebase.auth

    /** The current user's UID. Empty string if not signed in yet. */
    val userId: String
        get() = auth.currentUser?.uid ?: ""

    val isSignedIn: Boolean
        get() = auth.currentUser != null

    /**
     * Ensures the user is signed in anonymously.
     * Safe to call multiple times — no-ops if already signed in.
     */
    suspend fun ensureSignedIn() {
        if (auth.currentUser == null) {
            auth.signInAnonymously().await()
        }
    }
}