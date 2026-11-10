package com.openjarvis.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.lifecycleScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.openjarvis.ui.MainActivity
import kotlinx.coroutines.launch

class AuthActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var credentialManager: CredentialManager
    private lateinit var signInButton: Button
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()
        credentialManager = CredentialManager.create(this)

        if (auth.currentUser != null) {
            openMain()
            return
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(40, 40, 40, 40)
        }

        val title = TextView(this).apply {
            text = "ورود به Open Jarvis"
            textSize = 24f
            gravity = Gravity.CENTER
        }

        val description = TextView(this).apply {
            text = "برای ادامه با حساب Google وارد شوید."
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 28)
        }

        signInButton = Button(this).apply {
            text = "ورود با Google"
            setOnClickListener { startGoogleSignIn() }
        }

        progressBar = ProgressBar(this).apply {
            visibility = android.view.View.GONE
        }

        layout.addView(title, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        layout.addView(description, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        layout.addView(signInButton, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        layout.addView(progressBar, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        setContentView(layout)
    }

    private fun startGoogleSignIn() {
        setLoading(true)

        val googleIdOption = GetGoogleIdOption.Builder()
            .setServerClientId(getString(com.google.firebase.R.string.default_web_client_id))
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        lifecycleScope.launch {
            try {
                val result = credentialManager.getCredential(
                    context = this@AuthActivity,
                    request = request
                )
                handleCredential(result.credential)
            } catch (error: GetCredentialException) {
                setLoading(false)
                Toast.makeText(
                    this@AuthActivity,
                    "ورود با Google انجام نشد. دوباره تلاش کنید.",
                    Toast.LENGTH_LONG
                ).show()
            } catch (error: Exception) {
                setLoading(false)
                Toast.makeText(
                    this@AuthActivity,
                    "خطا در ورود: ${error.localizedMessage ?: "نامشخص"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun handleCredential(credential: Credential) {
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            setLoading(false)
            Toast.makeText(this, "نوع حساب Google معتبر نیست.", Toast.LENGTH_LONG).show()
            return
        }

        val googleToken = GoogleIdTokenCredential.createFrom(credential.data)
        val firebaseCredential = GoogleAuthProvider.getCredential(googleToken.idToken, null)

        auth.signInWithCredential(firebaseCredential)
            .addOnCompleteListener(this) { task ->
                setLoading(false)
                if (task.isSuccessful && auth.currentUser != null) {
                    openMain()
                } else {
                    Toast.makeText(
                        this,
                        "تأیید حساب ناموفق بود. تنظیمات Firebase را بررسی کنید.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }

    private fun setLoading(loading: Boolean) {
        if (!::signInButton.isInitialized || !::progressBar.isInitialized) return
        signInButton.isEnabled = !loading
        progressBar.visibility = if (loading) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun openMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}