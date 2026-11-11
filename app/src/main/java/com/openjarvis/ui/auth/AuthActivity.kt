package com.openjarvis.ui.auth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.lifecycleScope
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.openjarvis.R
import com.openjarvis.ui.MainActivity
import com.openjarvis.ui.theme.OpenJarvisTheme
import com.openjarvis.ui.theme.VoidColor
import kotlinx.coroutines.launch

class AuthActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var credentialManager: CredentialManager
    private var loading by mutableStateOf(false)
    private var errorMessage by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        credentialManager = CredentialManager.create(this)

        if (auth.currentUser != null) {
            openMain()
            return
        }

        setContent {
            OpenJarvisTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(VoidColor.Void950)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Surface(
                                modifier = Modifier.size(76.dp),
                                shape = RoundedCornerShape(24.dp),
                                color = VoidColor.Void800,
                                border = BorderStroke(1.dp, VoidColor.BorderGlow)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        "J",
                                        color = VoidColor.Cyan,
                                        fontSize = 38.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(Modifier.height(28.dp))
                            Text(
                                "به Open Jarvis خوش آمدید",
                                color = VoidColor.TextPrimary,
                                fontSize = 23.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "برای همگام‌سازی حساب و ادامه، با حساب Google وارد شوید.",
                                color = VoidColor.TextSecondary,
                                fontSize = 14.sp,
                                lineHeight = 22.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(30.dp))
                            Button(
                                onClick = { startGoogleSignIn() },
                                enabled = !loading,
                                modifier = Modifier.fillMaxWidth().height(54.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = VoidColor.Cyan,
                                    contentColor = Color(0xFF061116)
                                )
                            ) {
                                if (loading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = Color(0xFF061116),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text("ادامه با Google", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                }
                            }
                            if (errorMessage != null) {
                                Spacer(Modifier.height(18.dp))
                                Surface(
                                    color = VoidColor.Void800,
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, VoidColor.Red.copy(alpha = 0.45f))
                                ) {
                                    Text(
                                        errorMessage.orEmpty(),
                                        modifier = Modifier.padding(14.dp),
                                        color = VoidColor.TextSecondary,
                                        fontSize = 12.sp,
                                        lineHeight = 19.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun startGoogleSignIn() {
        loading = true
        errorMessage = null

        // This is an explicit button flow. Unlike the account-picker bottom sheet,
        // it also supports users who need to add or re-authenticate a Google account.
        val signInOption = GetSignInWithGoogleOption.Builder(
            serverClientId = getString(R.string.default_web_client_id)
        ).build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        lifecycleScope.launch {
            try {
                val result = credentialManager.getCredential(
                    context = this@AuthActivity,
                    request = request
                )
                handleCredential(result.credential)
            } catch (error: GetCredentialException) {
                loading = false
                val type = error::class.java.simpleName
                Log.w(TAG, "Google credential request failed: $type")
                errorMessage = when (type) {
                    "GetCredentialCancellationException" -> "فرایند ورود لغو شد."
                    "NoCredentialException" -> "حساب Google قابل استفاده‌ای روی دستگاه پیدا نشد. حساب و سرویس‌های Google را بررسی کنید."
                    "GetCredentialProviderConfigurationException" -> "تنظیمات ورود Google یا سرویس‌های دستگاه کامل نیست."
                    else -> "ورود Google ناموفق بود ($type). اتصال اینترنت و تنظیمات OAuth را بررسی کنید."
                }
            } catch (error: Exception) {
                loading = false
                Log.e(TAG, "Unexpected Google sign-in failure: ${error::class.java.simpleName}")
                errorMessage = "خطای غیرمنتظره در ورود رخ داد. لطفاً دوباره تلاش کنید."
            }
        }
    }

    private fun handleCredential(credential: Credential) {
        if (credential !is CustomCredential ||
            credential.type != TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            loading = false
            errorMessage = "نوع اطلاعات دریافتی از Google پشتیبانی نمی‌شود."
            return
        }

        try {
            val googleToken = GoogleIdTokenCredential.createFrom(credential.data)
            val firebaseCredential = GoogleAuthProvider.getCredential(googleToken.idToken, null)
            auth.signInWithCredential(firebaseCredential)
                .addOnCompleteListener(this) { task ->
                    loading = false
                    if (task.isSuccessful && auth.currentUser != null) {
                        openMain()
                    } else {
                        val type = task.exception?.javaClass?.simpleName ?: "UnknownAuthError"
                        Log.w(TAG, "Firebase Google authentication failed: $type")
                        errorMessage = "تأیید حساب توسط Firebase ناموفق بود ($type). شناسه برنامه و SHA گواهی امضا باید بررسی شوند."
                    }
                }
        } catch (error: Exception) {
            loading = false
            Log.e(TAG, "Google credential parsing failed: ${error::class.java.simpleName}")
            errorMessage = "اطلاعات ورود Google معتبر نبود. دوباره تلاش کنید."
        }
    }

    private fun openMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    companion object {
        private const val TAG = "JarvisAuth"
    }
}
