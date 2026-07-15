package app.fynlo.data

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import app.fynlo.R
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

data class GoogleSignInResult(
    val idToken: String,
    val email: String,
)

object GoogleSignInHelper {

    private const val FALLBACK_WEB_CLIENT_ID =
        "1016532477819-i121rp15tdpsq64ifd705b7mr0ifpqr0.apps.googleusercontent.com"

    suspend fun signIn(context: Context): GoogleSignInResult {
        val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(
            serverClientId = webClientId(context)
        ).build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInWithGoogleOption)
            .build()

        val response = CredentialManager.create(context).getCredential(
            context = context,
            request = request,
        )

        val credential = response.credential
        if (
            credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
            return GoogleSignInResult(
                idToken = googleCredential.idToken,
                email = googleCredential.id,
            )
        }

        error("Google sign-in did not return a Google account.")
    }

    suspend fun clearCredentialState(context: Context) {
        CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
    }

    private fun webClientId(context: Context): String {
        return context.getString(R.string.default_web_client_id)
            .takeIf { it.isNotBlank() }
            ?: FALLBACK_WEB_CLIENT_ID
    }
}
