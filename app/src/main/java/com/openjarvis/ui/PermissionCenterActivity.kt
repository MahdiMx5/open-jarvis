package com.openjarvis.ui

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.openjarvis.accessibility.JarvisAccessibilityService
import com.openjarvis.ui.theme.OpenJarvisTheme
import com.openjarvis.ui.theme.VoidColor

private data class PermissionItem(
    val id: String,
    val title: String,
    val description: String,
    val explanation: String,
    val granted: Boolean,
    val special: Boolean
)

class PermissionCenterActivity : ComponentActivity() {
    private var refreshVersion by mutableIntStateOf(0)
    private lateinit var runtimePermissionLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runtimePermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { refreshVersion++ }

        setContent {
            OpenJarvisTheme {
                PermissionCenterScreen(
                    refreshVersion = refreshVersion,
                    permissions = permissionItems(),
                    canStartVoice = hasMicrophonePermission() && hasOverlayPermission(),
                    onBack = { finish() },
                    onRequest = { id -> requestPermission(id) },
                    onStartVoice = {
                        if (!hasMicrophonePermission() || !hasOverlayPermission()) {
                            Toast.makeText(this, "ابتدا مجوز میکروفون و پنجره شناور را فعال کن.", Toast.LENGTH_LONG).show()
                        } else {
                            try {
                                OverlayService.start(this)
                                Toast.makeText(this, "جارویس فعال شد.", Toast.LENGTH_SHORT).show()
                                finish()
                            } catch (error: Exception) {
                                Toast.makeText(this, "شروع جارویس ناموفق بود.", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshVersion++
    }

    private fun hasMicrophonePermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun hasOverlayPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)

    private fun isAccessibilityEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        val component = ComponentName(this, JarvisAccessibilityService::class.java).flattenToString()
        return enabled.split(':').any { it.equals(component, ignoreCase = true) }
    }

    private fun permissionItems(): List<PermissionItem> {
        val items = mutableListOf(
            PermissionItem(
                id = Manifest.permission.RECORD_AUDIO,
                title = "میکروفون",
                description = "برای شنیدن فرمان صوتی و عبارت بیدارباش جارویس",
                explanation = "با این دسترسی، جارویس می‌تواند هنگام فعال بودن قابلیت صوتی صدای فرمان تو را دریافت کند. هر زمان نخواستی می‌توانی دسترسی را از تنظیمات گوشی لغو کنی.",
                granted = hasMicrophonePermission(),
                special = false
            ),
            PermissionItem(
                id = "OVERLAY",
                title = "نمایش پنجره شناور",
                description = "برای نمایش کنترل جارویس روی برنامه‌های دیگر",
                explanation = "این دسترسی فقط برای نمایش پنجره شناور جارویس روی برنامه‌های دیگر استفاده می‌شود. فعال‌سازی نهایی در صفحه تنظیمات خود اندروید انجام می‌شود.",
                granted = hasOverlayPermission(),
                special = true
            ),
            PermissionItem(
                id = "ACCESSIBILITY",
                title = "دسترسی‌پذیری و کنترل صفحه",
                description = "برای لمس، نوشتن و انجام فرمان‌های درخواستی داخل برنامه‌ها",
                explanation = "این دسترسی قدرتمند است: سرویس جارویس می‌تواند محتوای قابل مشاهده صفحه را بخواند و در پاسخ به فرمان تو روی عناصر برنامه‌ها لمس یا متن وارد کند. فقط در صورت نیاز به کنترل برنامه‌ها فعالش کن؛ این دسترسی به معنی اجازه نامحدود برای انجام هر کاری نیست.",
                granted = isAccessibilityEnabled(),
                special = true
            ),
        )
        if (Build.VERSION.SDK_INT >= 33) {
            items.add(
                1,
                PermissionItem(
                    id = Manifest.permission.POST_NOTIFICATIONS,
                    title = "اعلان‌های جارویس",
                    description = "برای نمایش وضعیت و اعلان‌های مربوط به اجرای دستیار",
                    explanation = "این اجازه برای نمایش اعلان‌های وضعیت و نتیجه کارهای جارویس استفاده می‌شود. اگر ردش کنی، ممکن است بعضی اعلان‌ها نمایش داده نشوند.",
                    granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
                    special = false
                )
            )
        }

        val mediaPermission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        items.add(
            PermissionItem(
                id = mediaPermission,
                title = "فایل‌های صوتی",
                description = "برای دسترسی به فایل‌های صوتی محلی در قابلیت‌های رسانه‌ای",
                explanation = "این دسترسی فقط برای خواندن فایل‌های صوتی‌ای است که در قابلیت‌های رسانه‌ای جارویس انتخاب می‌کنی. اگر از این قابلیت استفاده نمی‌کنی، نیازی به فعال‌کردنش نیست.",
                granted = ContextCompat.checkSelfPermission(this, mediaPermission) == PackageManager.PERMISSION_GRANTED,
                special = false
            )
        )
        return items
    }

    private fun requestPermission(id: String) {
        when (id) {
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.READ_EXTERNAL_STORAGE -> runtimePermissionLauncher.launch(id)
            "OVERLAY" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                }
            }
            "ACCESSIBILITY" -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }
}

@Composable
private fun PermissionCenterScreen(
    refreshVersion: Int,
    permissions: List<PermissionItem>,
    canStartVoice: Boolean,
    onBack: () -> Unit,
    onRequest: (String) -> Unit,
    onStartVoice: () -> Unit
) {
    var selected by remember { mutableStateOf<PermissionItem?>(null) }

    androidx.compose.runtime.key(refreshVersion) {
        Scaffold(containerColor = VoidColor.Void950) { padding ->
            androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp, vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "بازگشت", tint = VoidColor.TextPrimary)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("مرکز دسترسی‌ها", color = VoidColor.TextPrimary, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                            Text("هر دسترسی فقط با تأیید خودت فعال می‌شود.", color = VoidColor.TextSecondary, fontSize = 12.sp)
                        }
                        Icon(Icons.Default.Security, contentDescription = null, tint = VoidColor.Cyan, modifier = Modifier.size(26.dp))
                    }

                    Spacer(Modifier.height(14.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = VoidColor.Void800),
                        border = BorderStroke(1.dp, VoidColor.BorderSubtle),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            "جارویس هیچ دسترسی ویژه‌ای را بی‌اجازه فعال نمی‌کند. با انتخاب هر مورد، ابتدا توضیح و دکمه‌های «اجازه می‌دهم» و «نه، فعلاً نه» را می‌بینی؛ سپس اندروید پنجره یا صفحه رسمی خودش را نشان می‌دهد.",
                            modifier = Modifier.padding(14.dp),
                            color = VoidColor.TextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 20.sp
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        items(permissions, key = { it.id }) { item ->
                            PermissionCard(item = item, onClick = { selected = item })
                        }
                    }

                    Button(
                        onClick = onStartVoice,
                        enabled = canStartVoice,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(15.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = VoidColor.Cyan,
                            contentColor = Color(0xFF061116),
                            disabledContainerColor = VoidColor.Void700,
                            disabledContentColor = VoidColor.TextDisabled
                        )
                    ) {
                        Text("اجرای دستیار صوتی", fontWeight = FontWeight.Bold)
                    }
                    Text(
                        if (canStartVoice) "میکروفون و پنجره شناور آماده‌اند." else "برای اجرای دستیار صوتی، میکروفون و پنجره شناور را فعال کن.",
                        modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
                        color = VoidColor.TextDisabled,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    selected?.let { item ->
        AlertDialog(
            onDismissRequest = { selected = null },
            containerColor = VoidColor.Void900,
            title = { Text(if (item.granted) "این دسترسی فعال است" else "اجازهٔ دسترسی؟", color = VoidColor.TextPrimary, textAlign = TextAlign.Right) },
            text = {
                Column {
                    Text(item.title, color = VoidColor.Cyan, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(7.dp))
                    Text(item.explanation, color = VoidColor.TextSecondary, lineHeight = 21.sp, fontSize = 13.sp)
                    if (item.granted) {
                        Spacer(Modifier.height(8.dp))
                        Text("این دسترسی هم‌اکنون فعال است.", color = VoidColor.Green, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val id = item.id
                    selected = null
                    if (!item.granted) onRequest(id)
                }) {
                    Text(if (item.granted) "متوجه شدم" else "اجازه می‌دهم", color = VoidColor.Cyan)
                }
            },
            dismissButton = {
                TextButton(onClick = { selected = null }) {
                    Text(if (item.granted) "بستن" else "نه، فعلاً نه", color = VoidColor.TextSecondary)
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PermissionCard(item: PermissionItem, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = VoidColor.Void900),
        border = BorderStroke(1.dp, if (item.granted) VoidColor.Cyan.copy(alpha = 0.35f) else VoidColor.BorderSubtle),
        shape = RoundedCornerShape(15.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (item.id) {
                    Manifest.permission.RECORD_AUDIO -> Icons.Default.Mic
                    Manifest.permission.POST_NOTIFICATIONS -> Icons.Default.Notifications
                    Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_EXTERNAL_STORAGE -> Icons.Default.Lock
                    "ACCESSIBILITY" -> Icons.Default.Visibility
                    else -> Icons.Default.Lock
                },
                contentDescription = null,
                tint = if (item.granted) VoidColor.Cyan else VoidColor.TextDisabled,
                modifier = Modifier.size(25.dp)
            )
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(item.title, color = VoidColor.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Spacer(Modifier.height(3.dp))
                Text(item.description, color = VoidColor.TextDisabled, fontSize = 11.sp, lineHeight = 17.sp)
            }
            if (item.granted) {
                Icon(Icons.Default.CheckCircle, contentDescription = "فعال", tint = VoidColor.Green, modifier = Modifier.size(21.dp))
            } else {
                Text("بررسی", color = VoidColor.Cyan, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}
