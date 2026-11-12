package com.openjarvis.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.openjarvis.ui.auth.AuthActivity
import com.openjarvis.ui.theme.OpenJarvisTheme
import com.openjarvis.ui.theme.VoidColor

class ProfileActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { OpenJarvisTheme { ProfileScreen(onBack = { finish() }) } }
    }
}

@Composable
private fun ProfileScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    val user = auth.currentUser
    var displayName by remember { mutableStateOf(user?.displayName.orEmpty()) }
    var status by remember { mutableStateOf<String?>(null) }

    Surface(modifier = Modifier.fillMaxSize(), color = VoidColor.Void950) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "بازگشت", tint = VoidColor.TextPrimary)
                }
                Text("پروفایل من", color = VoidColor.TextPrimary, fontSize = 20.sp)
            }
            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = null,
                tint = VoidColor.Cyan,
                modifier = Modifier.align(Alignment.CenterHorizontally).height(72.dp)
            )
            Text(user?.email ?: "حساب مهمان", color = VoidColor.TextSecondary, modifier = Modifier.align(Alignment.CenterHorizontally))
            Text("اطلاعات حساب", color = VoidColor.Cyan, fontSize = 14.sp)
            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("نام نمایشی") },
                singleLine = true
            )
            Text("وضعیت ایمیل: " + if (user?.isEmailVerified == true) "تأیید شده" else "تأیید نشده", color = VoidColor.TextSecondary)
            Text("روش ورود: " + (user?.providerData?.filter { it.providerId != "firebase" }?.joinToString { it.providerId }?.takeIf { it.isNotBlank() } ?: "ایمیل / رمز عبور"), color = VoidColor.TextSecondary)
            Text("شناسه حساب: " + (user?.uid?.take(12)?.plus("…") ?: "—"), color = VoidColor.TextSecondary)
            Button(
                onClick = {
                    val current = auth.currentUser
                    if (current == null) status = "حساب واردشده پیدا نشد."
                    else if (displayName.isBlank()) status = "نام نمایشی نمی‌تواند خالی باشد."
                    else {
                        val request = UserProfileChangeRequest.Builder().setDisplayName(displayName.trim()).build()
                        current.updateProfile(request).addOnCompleteListener { task ->
                            status = if (task.isSuccessful) "نام پروفایل ذخیره شد." else "ذخیره ناموفق بود: ${task.exception?.localizedMessage ?: "خطای نامشخص"}"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("ذخیره نام") }
            if (user?.email?.isNotBlank() == true && !user.isEmailVerified) {
                TextButton(onClick = {
                    user.sendEmailVerification().addOnCompleteListener { task ->
                        status = if (task.isSuccessful) "لینک تأیید به ایمیلت ارسال شد." else "ارسال ناموفق بود: ${task.exception?.localizedMessage ?: "خطای نامشخص"}"
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("ارسال دوباره ایمیل تأیید") }
            }
            if (user?.email?.isNotBlank() == true) {
                TextButton(onClick = {
                    auth.sendPasswordResetEmail(user.email!!).addOnCompleteListener { task ->
                        status = if (task.isSuccessful) "لینک بازیابی رمز ارسال شد." else "ارسال ناموفق بود: ${task.exception?.localizedMessage ?: "خطای نامشخص"}"
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("بازیابی رمز عبور") }
            }
            if (status != null) Text(status.orEmpty(), color = VoidColor.Cyan, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = {
                auth.signOut()
                val intent = Intent(context, AuthActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                context.startActivity(intent)
                (context as? ComponentActivity)?.finish()
            }, modifier = Modifier.fillMaxWidth()) { Text("خروج از حساب", color = Color(0xFFFF7777)) }
        }
    }
}
