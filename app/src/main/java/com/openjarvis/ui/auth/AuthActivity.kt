package com.openjarvis.ui.auth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
    private var email by mutableStateOf("")
    private var password by mutableStateOf("")
    private var confirmPassword by mutableStateOf("")
    private var createAccount by mutableStateOf(false)
    private var passwordVisible by mutableStateOf(false)

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
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = VoidColor.Void950
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .imePadding()
                                .padding(horizontal = 24.dp, vertical = 28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.mx5_mark),
                                contentDescription = "Open Mx5 logo",
                                modifier = Modifier
                                    .size(132.dp)
                                    .padding(bottom = 12.dp),
                                contentScale = ContentScale.Fit
                            )
                            Text(
                                "Open Mx5",
                                color = VoidColor.Cyan,
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                if (createAccount) "ساخت حساب کاربری" else "ورود به حساب کاربری",
                                color = VoidColor.TextPrimary,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(22.dp))

                            OutlinedTextField(
                                value = email,
                                onValueChange = { email = it.trim() },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("ایمیل") },
                                singleLine = true,
                                enabled = !loading,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                            )
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("رمز عبور") },
                                singleLine = true,
                                enabled = !loading,
                                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                trailingIcon = {
                                    TextButton(onClick = { passwordVisible = !passwordVisible }) {
                                        Text(if (passwordVisible) "پنهان" else "نمایش")
                                    }
                                }
                            )

                            if (createAccount) {
                                Spacer(Modifier.height(10.dp))
                                OutlinedTextField(
                                    value = confirmPassword,
                                    onValueChange = { confirmPassword = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("تکرار رمز عبور") },
                                    singleLine = true,
                                    enabled = !loading,
                                    visualTransformation = PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                                )
                            }

                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = { submitEmailPassword() },
                                enabled = !loading && email.isNotBlank() && password.isNotBlank(),
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (loading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text(if (createAccount) "ایجاد حساب" else "ورود با ایمیل")
                                }
                            }

                            TextButton(
                                onClick = {
                                    createAccount = !createAccount
                                    errorMessage = null
                                    confirmPassword = ""
                                },
                                enabled = !loading
                            ) {
                                Text(
                                    if (createAccount) "از قبل حساب داری؟ ورود" else "حساب نداری؟ ثبت‌نام کن",
                                    color = VoidColor.Cyan
                                )
                            }

                            if (!createAccount) {
                                TextButton(
                                    onClick = { sendPasswordReset() },
                                    enabled = !loading && email.contains("@")
                                ) {
                                    Text("فراموشی رمز عبور", color = VoidColor.TextSecondary)
                                }
                            }

                            Divider(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                                color = VoidColor.BorderSubtle
                            )

                            OutlinedButton(
                                onClick = { startGoogleSignIn() },
                                enabled = !loading,
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("ادامه با Google", color = VoidColor.TextPrimary)
                            }

                            if (errorMessage != null) {
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    errorMessage.orEmpty(),
                                    modifier = Modifier.fillMaxWidth().background(
                                        VoidColor.Void800,
                                        RoundedCornerShape(12.dp)
                                    ).padding(14.dp),
                                    color = VoidColor.TextSecondary,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "رمز عبور به‌صورت امن توسط Firebase مدیریت می‌شود.",
                                color = VoidColor.TextDisabled,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }

    private fun submitEmailPassword() {
        val normalizedEmail = email.trim()
        if (!normalizedEmail.contains("@")) {
            errorMessage = "ایمیل معتبر وارد کن."
            return
        }
        if (password.length < 6) {
            errorMessage = "رمز عبور باید حداقل ۶ نویسه داشته باشد."
            return
        }
        if (createAccount && password != confirmPassword) {
            errorMessage = "رمز عبور و تکرار آن یکسان نیستند."
            return
        }

        loading = true
        errorMessage = null
        val operation = if (createAccount) {
            auth.createUserWithEmailAndPassword(normalizedEmail, password)
        } else {
            auth.signInWithEmailAndPassword(normalizedEmail, password)
        }
        operation.addOnCompleteListener(this) { task ->
            loading = false
            if (task.isSuccessful && auth.currentUser != null) {
                password = ""
                confirmPassword = ""
                openMain()
            } else {
                val code = (task.exception as? com.google.firebase.auth.FirebaseAuthException)?.errorCode.orEmpty()
                Log.w(TAG, "Email authentication failed: $code")
                errorMessage = when (code) {
                    "ERROR_EMAIL_ALREADY_IN_USE" -> "این ایمیل قبلاً ثبت شده است؛ وارد حساب شو."
                    "ERROR_INVALID_EMAIL" -> "قالب ایمیل معتبر نیست."
                    "ERROR_WEAK_PASSWORD" -> "رمز عبور ضعیف است؛ حداقل ۶ نویسه وارد کن."
                    "ERROR_USER_NOT_FOUND", "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL" -> "ایمیل یا رمز عبور درست نیست."
                    "ERROR_OPERATION_NOT_ALLOWED" -> "ورود با ایمیل و رمز هنوز در Firebase فعال نشده است."
                    "ERROR_TOO_MANY_REQUESTS" -> "تعداد تلاش‌ها زیاد شده؛ کمی بعد دوباره امتحان کن."
                    else -> "ورود یا ثبت‌نام انجام نشد. اتصال اینترنت و تنظیمات Firebase را بررسی کن."
                }
            }
        }
    }

    private fun sendPasswordReset() {
        val normalizedEmail = email.trim()
        if (!normalizedEmail.contains("@")) {
            errorMessage = "ابتدا ایمیل حساب را وارد کن."
            return
        }
        loading = true
        errorMessage = null
        auth.sendPasswordResetEmail(normalizedEmail).addOnCompleteListener(this) { task ->
            loading = false
            errorMessage = if (task.isSuccessful) {
                "اگر این ایمیل در سیستم ثبت شده باشد، لینک بازیابی رمز ارسال می‌شود."
            } else {
                "ارسال لینک بازیابی انجام نشد؛ ایمیل و اتصال اینترنت را بررسی کن."
            }
        }
    }

    private fun startGoogleSignIn() {
        loading = true
        errorMessage = null
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
                Log.w(TAG, "Google credential request failed: $type; message=${error.message}", error)
                errorMessage = when (type) {
                    "GetCredentialCancellationException" -> "فرایند ورود لغو شد."
                    "NoCredentialException" -> "حساب Google قابل استفاده‌ای روی دستگاه پیدا نشد."
                    else -> "ورود Google ناموفق بود ($type). تنظیمات OAuth و سرویس‌های دستگاه را بررسی کن."
                }
            } catch (error: Exception) {
                loading = false
                Log.e(TAG, "Unexpected Google sign-in failure: ${error::class.java.simpleName}", error)
                errorMessage = "خطای غیرمنتظره در ورود Google رخ داد."
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
                        val code = (task.exception as? com.google.firebase.auth.FirebaseAuthException)?.errorCode.orEmpty()
                        Log.w(TAG, "Firebase Google authentication failed: $code")
                        errorMessage = "تأیید حساب توسط Firebase ناموفق بود ($code)."
                    }
                }
        } catch (error: Exception) {
            loading = false
            Log.e(TAG, "Google credential parsing failed: ${error::class.java.simpleName}", error)
            errorMessage = "اطلاعات ورود Google معتبر نبود. دوباره تلاش کن."
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
