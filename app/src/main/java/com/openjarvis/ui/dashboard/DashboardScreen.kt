package com.openjarvis.ui.dashboard

import android.util.Log
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.Canvas
import com.openjarvis.graphify.GraphifyRepository
import com.openjarvis.graphify.nodes.AppNode
import com.openjarvis.graphify.nodes.TaskNode
import com.openjarvis.ui.theme.VoidColor

@Composable
fun DashboardScreen(
    onStartOverlay: () -> Unit,
    onOpenSettings: () -> Unit,
    graphifyRepo: GraphifyRepository,
    modifier: Modifier = Modifier
) {
    var recentTasks by remember { mutableStateOf<List<TaskNode>>(emptyList()) }
    var mostUsedApps by remember { mutableStateOf<List<AppNode>>(emptyList()) }
    var isSystemActive by remember { mutableStateOf(false) }

    LaunchedEffect(graphifyRepo) {
        try {
            recentTasks = graphifyRepo.getRecentTasks(10)
            mostUsedApps = graphifyRepo.getMostUsedApps(6)
        } catch (error: Exception) {
            Log.e("OpenJarvisDashboard", "Unable to load dashboard data", error)
            recentTasks = emptyList()
            mostUsedApps = emptyList()
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(VoidColor.Void950, Color(0xFF10172A), VoidColor.Void950)
                    )
                )
        ) {
            AmbientGlow()
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 30.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                item {
                    Header(onOpenSettings)
                }
                item {
                    AssistantHero(
                        active = isSystemActive,
                        onActivate = {
                            onStartOverlay()
                            isSystemActive = true
                        }
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetricCard("کارهای اخیر", recentTasks.size.toString(), Modifier.weight(1f))
                        MetricCard("برنامه‌ها", mostUsedApps.size.toString(), Modifier.weight(1f))
                        MetricCard("وضعیت", if (isSystemActive) "فعاله" else "آماده", Modifier.weight(1f))
                    }
                }
                item { SectionTitle("آخرین کارها", "فعالیت‌های اخیر جارویس") }
                if (recentTasks.isEmpty()) {
                    item {
                        EmptyCard("هنوز کاری ثبت نشده", "دکمهٔ فعال‌سازی را بزن تا شروع کنیم.")
                    }
                } else {
                    items(recentTasks.take(5)) { task -> TaskItem(task) }
                }
                item { SectionTitle("برنامه‌های پرکاربرد", "دسترسی سریع") }
                if (mostUsedApps.isEmpty()) {
                    item { EmptyCard("هنوز داده‌ای نداریم", "بعد از استفاده، برنامه‌های پرتکرار اینجا دیده می‌شن.") }
                } else {
                    items(mostUsedApps) { app -> AppItem(app) }
                }
                item {
                    Text(
                        "فارسی و English • دستیار هوشمند شخصی",
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        color = VoidColor.TextDisabled,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun AmbientGlow() {
    val transition = rememberInfiniteTransition(label = "ambient")
    val pulse by transition.animateFloat(
        initialValue = 0.72f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    Canvas(Modifier.fillMaxSize()) {
        val center = Offset(size.width * 0.82f, size.height * 0.12f)
        drawCircle(
            brush = Brush.radialGradient(
                listOf(VoidColor.Violet.copy(alpha = 0.17f * pulse), Color.Transparent),
                center = center,
                radius = 260.dp.toPx()
            ),
            center = center,
            radius = 260.dp.toPx()
        )
        val second = Offset(size.width * 0.08f, size.height * 0.48f)
        drawCircle(
            brush = Brush.radialGradient(
                listOf(VoidColor.Cyan.copy(alpha = 0.07f), Color.Transparent),
                center = second,
                radius = 190.dp.toPx()
            ),
            center = second,
            radius = 190.dp.toPx()
        )
    }
}

@Composable
private fun Header(onSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text("OPEN JARVIS", color = VoidColor.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Black, letterSpacing = 1.8.sp)
            Text("دستیار هوشمند تو", color = VoidColor.TextSecondary, fontSize = 12.sp)
        }
        Surface(
            modifier = Modifier
                .size(44.dp)
                .clickable(onClick = onSettings),
            shape = CircleShape,
            color = VoidColor.Void800,
            border = androidx.compose.foundation.BorderStroke(1.dp, VoidColor.BorderSubtle)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Settings, contentDescription = "تنظیمات", tint = VoidColor.TextPrimary)
            }
        }
    }
}

@Composable
private fun AssistantHero(active: Boolean, onActivate: () -> Unit) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = VoidColor.Void800.copy(alpha = 0.96f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, VoidColor.BorderGlow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.size(8.dp).clip(CircleShape)
                        .background(if (active) VoidColor.Green else VoidColor.Amber)
                )
                Text(if (active) "جارویس فعاله" else "آماده‌ام، بزن بریم", color = VoidColor.TextSecondary, fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                Text("AI ASSISTANT", color = VoidColor.Cyan, fontSize = 10.sp, letterSpacing = 1.4.sp)
            }
            Text(
                "سلام! من جارویسم.",
                color = VoidColor.TextPrimary,
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "فارسی راحت حرف بزن؛ انگلیسی هم بلدم. بگو چی لازم داری تا با هم انجامش بدیم.",
                color = VoidColor.TextSecondary,
                fontSize = 14.sp,
                lineHeight = 23.sp
            )
            Button(
                onClick = onActivate,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(17.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = VoidColor.VioletDim,
                    contentColor = Color.White
                )
            ) {
                Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(19.dp))
                Spacer(Modifier.size(9.dp))
                Text(if (active) "باز کردن دستیار" else "فعال‌کردن جارویس", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = VoidColor.Void800.copy(alpha = 0.88f),
        border = androidx.compose.foundation.BorderStroke(1.dp, VoidColor.BorderSubtle)
    ) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(value, color = VoidColor.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(title, color = VoidColor.TextDisabled, fontSize = 11.sp)
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, color = VoidColor.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = VoidColor.TextDisabled, fontSize = 11.sp)
    }
}

@Composable
private fun EmptyCard(title: String, detail: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = VoidColor.Void800.copy(alpha = 0.72f),
        border = androidx.compose.foundation.BorderStroke(1.dp, VoidColor.BorderSubtle)
    ) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, color = VoidColor.TextSecondary, fontWeight = FontWeight.SemiBold)
            Text(detail, color = VoidColor.TextDisabled, fontSize = 12.sp)
        }
    }
}

@Composable
private fun TaskItem(task: TaskNode) {
    val success = task.result.contains("opened", true) ||
        task.result.contains("success", true) ||
        task.result.contains("موفق")
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(17.dp),
        color = VoidColor.Void800.copy(alpha = 0.82f),
        border = androidx.compose.foundation.BorderStroke(1.dp, VoidColor.BorderSubtle)
    ) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(if (success) VoidColor.Green else VoidColor.Amber))
            Spacer(Modifier.size(11.dp))
            Column(Modifier.weight(1f)) {
                Text(task.command, color = VoidColor.TextPrimary, fontSize = 13.sp, maxLines = 1)
                Text(task.result, color = VoidColor.TextDisabled, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun AppItem(app: AppNode) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = VoidColor.Void800.copy(alpha = 0.82f),
        border = androidx.compose.foundation.BorderStroke(1.dp, VoidColor.BorderSubtle)
    ) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(11.dp))
                    .background(Brush.linearGradient(listOf(VoidColor.VioletDim, VoidColor.Cyan))),
                contentAlignment = Alignment.Center
            ) {
                Text(app.label.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.size(12.dp))
            Text(app.label, color = VoidColor.TextPrimary, fontSize = 13.sp)
        }
    }
}
