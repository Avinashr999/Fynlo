package app.fynlo.data

import com.google.firebase.auth.FirebaseAuthUserCollisionException
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

    val userId: String    get() = auth.currentUser?.uid ?: ""
    val userEmail: String get() = auth.currentUser?.email ?: ""
    val userName: String  get() = auth.currentUser?.displayName ?: ""
    val userPhoto: String get() = auth.currentUser?.photoUrl?.toString() ?: ""

    val isSignedInWithGoogle: Boolean
        get() = auth.currentUser?.providerData
            ?.any { it.providerId == GoogleAuthProvider.PROVIDER_ID } == true

    // Primary auth check — only Google sign-in counts, anonymous is NOT accepted
    val isSignedIn: Boolean get() = isSignedInWithGoogle

    suspend fun ensureSignedIn() {
        // No longer signs in anonymously — Google sign-in is required
        // This is a no-op; kept for compatibility
    }

    /**
     * Sign in with Google.
     *
     * Three cases handled:
     * 1. No current user          -> direct signInWithCredential
     * 2. Anonymous user           -> try linkWithCredential first;
     *    if the Google account already exists elsewhere (collision),
     *    fall back to signInWithCredential with that existing account
     * 3. Already a Google user    -> direct signInWithCredential (re-auth)
     */
    suspend fun signInWithGoogle(idToken: String): Result<FirebaseUser> {
        return runCatching {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val current    = auth.currentUser

            val result = if (current != null && current.isAnonymous) {
                try {
                    // Best case: link anonymous data to Google account
                    current.linkWithCredential(credential).await()
                } catch (e: FirebaseAuthUserCollisionException) {
                    // Google account already exists in Firebase —
                    // sign in directly to that existing account instead
                    auth.signInWithCredential(credential).await()
                }
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