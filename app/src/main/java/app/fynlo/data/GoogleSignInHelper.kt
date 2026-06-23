@file:Suppress("DEPRECATION")

package app.fynlo.data

import android.content.Context
import app.fynlo.R
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions

object GoogleSignInHelper {

    private const val FALLBACK_WEB_CLIENT_ID =
        "1016532477819-i121rp15tdpsq64ifd705b7mr0ifpqr0.apps.googleusercontent.com"

    fun getClient(context: Context): GoogleSignInClient {
        val webClientId = context.getString(R.string.default_web_client_id)
            .takeIf { it.isNotBlank() }
            ?: FALLBACK_WEB_CLIENT_ID

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .requestProfile()
            .build()
        return GoogleSignIn.getClient(context, gso)
    }
}
