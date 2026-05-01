package com.example.cashmemo.data

import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await

class AuthManager {

    private val auth = Firebase.auth

    private val _user = MutableStateFlow<FirebaseUser?>(auth.currentUser)
    val user: StateFlow<FirebaseUser?> = _user

    val userId: String   get() = auth.currentUser?.uid   ?: ""
    val userEmail: String get() = auth.currentUser?.email ?: ""
    val userName: String  get() = auth.currentUser?.displayName ?: ""
    val userPhoto: String get() = auth.currentUser?.photoUrl?.toString() ?: ""

    val isSignedInWithGoogle: Boolean
        get() = auth.currentUser?.providerData
            ?.any { it.providerId == GoogleAuthProvider.PROVIDER_ID } == true

    val isSignedIn: Boolean get() = auth.currentUser != null

    /** Anonymous sign-in — used as fallback if Google Sign-In not done yet. */
    suspend fun ensureSignedIn() {
        if (auth.currentUser == null) {
            auth.signInAnonymously().await()
            _user.value = auth.currentUser
        }
    }

    /**
     * Sign in with Google using the idToken from GoogleSignInAccount.
     * If the user was previously anonymous, this links the accounts
     * so no data is lost.
     */
    suspend fun signInWithGoogle(idToken: String): Result<FirebaseUser> {
        return runCatching {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val current = auth.currentUser
            val result = if (current != null && current.isAnonymous) {
                // Link anonymous account to Google — preserves existing data
                current.linkWithCredential(credential).await()
            } else {
                auth.signInWithCredential(credential).await()
            }
            _user.value = result.user
            result.user!!
        }
    }

    fun signOut() {
        auth.signOut()
        _user.value = null
    }
}