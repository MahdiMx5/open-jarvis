package com.openjarvis.ui.onboarding

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.openjarvis.ui.theme.VoidColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class OnboardingActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (isOnboardingComplete()) {
            finish()
            return
        }
        
        setContent { OnboardingScreen(onComplete = { finish() }) }
    }
    
    private fun isOnboardingComplete(): Boolean {
        val prefs = getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("onboarding_complete", false)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    var currentScreen by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    Scaffold(
        containerColor = VoidColor.Void950
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (currentScreen) {
                0 -> WelcomeScreen(
                    onGetStarted = { currentScreen = 1 }
                )
                1 -> PermissionsScreen(
                    onBack = { currentScreen = 0 },
                    onContinue = { currentScreen = 2 }
                )
                2 -> ProviderScreen(
                    onBack = { currentScreen = 1 },
                    onContinue = { currentScreen = 3 },
                    onSkip = { currentScreen = 3 }
                )
                3 -> TryItScreen(
                    onBack = { currentScreen = 2 },
                    onSuccess = {
                        completeOnboarding(context)
                        onComplete()
                    }
                )
                4 -> ReadyScreen(onStart = {
                    completeOnboarding(context)
                    onComplete()
                })
            }
            
            if (currentScreen in 1..3) {
                Spacer(modifier = Modifier.weight(1f))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (currentScreen > 0 && currentScreen < 4) {
                        TextButton(onClick = { currentScreen-- }) {
                            Text("بازگشت", color = VoidColor.TextDisabled)
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }
                    
                    if (currentScreen < 4) {
                        TextButton(onClick = { currentScreen++ }) {
                            Text("رد کردن", color = VoidColor.TextDisabled)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeScreen(onGetStarted: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "OPEN\nJARVIS",
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            color = VoidColor.Cyan,
            textAlign = TextAlign.Center,
            lineHeight = 52.sp
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "هوش مصنوعی تو.\nکنترل بیشتر روی گوشی.",
            fontSize = 20.sp,
            color = VoidColor.TextPrimary,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "درخواستت را بنویس یا بگو؛\nجارویس در انجام کارها کمکت می‌کند.",
            fontSize = 14.sp,
            color = VoidColor.TextDisabled,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(64.dp))
        
        Button(
            onClick = onGetStarted,
            colors = ButtonDefaults.buttonColors(
                containerColor = VoidColor.Cyan
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "شروع کنیم",
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun PermissionsScreen(onBack: () -> Unit, onContinue: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Text(
            text = "دسترسی‌های موردنیاز",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = VoidColor.TextPrimary
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "برای کنترل بعضی بخش‌های گوشی، باید دسترسی‌ها را جداگانه و با اجازه‌ی خودت فعال کنی.",
            fontSize = 14.sp,
            color = VoidColor.TextDisabled
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        PermissionRow("دسترسی‌پذیری", "برای خواندن محتوای صفحه و اجرای بعضی فرمان‌ها؛ هنوز همه‌ی قابلیت‌ها پیاده‌سازی نشده‌اند")
        PermissionRow("نمایش روی برنامه‌ها", "برای نمایش پنجره‌ی شناور MX5")
        PermissionRow("دسترسی اعلان‌ها", "فقط در صورت نیاز به خواندن اعلان‌ها و با اجازه‌ی تو")
        
        Spacer(modifier = Modifier.weight(1f))
        
        Button(
            onClick = onContinue,
            colors = ButtonDefaults.buttonColors(containerColor = VoidColor.Cyan),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("ادامه")
        }
    }
}

@Composable
private fun PermissionRow(title: String, subtitle: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = VoidColor.Void800)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = VoidColor.Cyan,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(title, fontWeight = FontWeight.Medium, color = VoidColor.TextPrimary)
                Text(subtitle, fontSize = 12.sp, color = VoidColor.TextDisabled)
            }
        }
    }
}

@Composable
private fun ProviderScreen(onBack: () -> Unit, onContinue: () -> Unit, onSkip: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Text(
            text = "انتخاب سرویس هوش مصنوعی",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = VoidColor.TextPrimary
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "برای استفاده‌ی آنلاین، سرویس و کلید معتبر لازم است؛ سهمیه‌ی رایگان به قوانین همان سرویس بستگی دارد.",
            fontSize = 14.sp,
            color = VoidColor.TextDisabled
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ProviderCard("Groq", "نیازمند کلید و سهمیه‌ی فعال", Modifier.weight(1f))
            ProviderCard("Gemini", "نیازمند دسترسی و کلید معتبر", Modifier.weight(1f))
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ProviderCard("OpenRouter", "مدل رایگان ممکن است محدود باشد", Modifier.weight(1f))
            ProviderCard("آفلاین", "موتور اجرا هنوز آماده نیست", Modifier.weight(1f))
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        Button(
            onClick = onContinue,
            colors = ButtonDefaults.buttonColors(containerColor = VoidColor.Cyan),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Continue")
        }
    }
}

@Composable
private fun ProviderCard(name: String, subtitle: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = VoidColor.Void800),
        border = BorderStroke(1.dp, VoidColor.BorderSubtle)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(name, fontWeight = FontWeight.Bold, color = VoidColor.TextPrimary)
            Text(subtitle, fontSize = 12.sp, color = VoidColor.TextDisabled)
        }
    }
}

@Composable
private fun TryItScreen(onBack: () -> Unit, onSuccess: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "آماده‌ی راه‌اندازی", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = VoidColor.TextPrimary)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "این صفحه فرمان آزمایشی اجرا نمی‌کند و موفقیت ساختگی نشان نمی‌دهد. بعد از ورود، اتصال سرویس هوش مصنوعی و دسترسی‌های موردنیاز را از تنظیمات بررسی کن.",
            fontSize = 14.sp,
            color = VoidColor.TextDisabled,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onSuccess,
            colors = ButtonDefaults.buttonColors(containerColor = VoidColor.Cyan),
            modifier = Modifier.fillMaxWidth()
        ) { Text("ورود به برنامه") }
    }
}

@Composable
private fun ReadyScreen(onStart: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = VoidColor.Cyan,
            modifier = Modifier.size(64.dp)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "آماده‌ی شروع",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = VoidColor.TextPrimary
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "برنامه نصب شده؛ بعضی قابلیت‌ها هنوز در حال تکمیل هستند.",
            fontSize = 14.sp,
            color = VoidColor.TextDisabled
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = onStart,
            colors = ButtonDefaults.buttonColors(containerColor = VoidColor.Cyan),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "ورود به MX5",
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp)
            )
        }
    }
}

private fun completeOnboarding(context: Context) {
    context.getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE)
        .edit()
        .putBoolean("onboarding_complete", true)
        .apply()
}