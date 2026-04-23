package com.amurcanov.tgwsproxy.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amurcanov.tgwsproxy.BuildConfig
import com.amurcanov.tgwsproxy.LogEntry
import com.amurcanov.tgwsproxy.LogManager
import com.amurcanov.tgwsproxy.R
import com.amurcanov.tgwsproxy.SettingsStore
import com.amurcanov.tgwsproxy.UPDATE_DIALOG_ACTION_POSTPONED
import com.amurcanov.tgwsproxy.UPDATE_DIALOG_ACTION_UPDATE
import com.amurcanov.tgwsproxy.fetchLatestReleaseInfo
import com.amurcanov.tgwsproxy.isNewerVersion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.launch

private const val AndroidForkRepoUrl = ""
private const val AndroidForkIssuesUrl = "$AndroidForkRepoUrl/issues/new"
private const val DeveloperProfileUrl = ""
private const val OriginalProjectUrl = ""
private const val ProxyReferenceUrl = ""
private const val AndroidAppDonateUrl = ""
private const val OriginalIdeaDonateUrl = ""

private val DonateActionButtonColor = Color(0xFF00AEA5)
private val OriginalIdeaDonateColor = Color(0xFFFF8A24)

private val Android16BlobShape: Shape = GenericShape { size, _ ->
    val centerX = size.width / 2f
    val centerY = size.height / 2f
    val outerRadius = min(size.width, size.height) / 2f
    val innerRadius = outerRadius * 0.92f
    val points = 14

    for (i in 0 until points * 2) {
        val angle = (-PI / 2.0) + (i * PI / points)
        val radius = if (i % 2 == 0) outerRadius else innerRadius
        val x = centerX + (radius * cos(angle)).toFloat()
        val y = centerY + (radius * sin(angle)).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

@Composable
fun InfoTab(settingsStore: SettingsStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showHelpDialog by remember { mutableStateOf(false) }
    var showDonateDialog by remember { mutableStateOf(false) }
    var actionsExpanded by rememberSaveable { mutableStateOf(true) }
    var projectExpanded by rememberSaveable { mutableStateOf(true) }
    var isCheckingUpdates by remember { mutableStateOf(false) }
    var pendingManualRelease by remember { mutableStateOf<com.amurcanov.tgwsproxy.AppReleaseInfo?>(null) }
    val savedPort by settingsStore.port.collectAsStateWithLifecycle(initialValue = "1443")
    val savedPoolSize by settingsStore.poolSize.collectAsStateWithLifecycle(initialValue = 4)
    val savedCfEnabled by settingsStore.cfproxyEnabled.collectAsStateWithLifecycle(initialValue = true)
    val savedCustomCfDomainEnabled by settingsStore.customCfDomainEnabled.collectAsStateWithLifecycle(initialValue = false)
    val savedCustomCfDomain by settingsStore.customCfDomain.collectAsStateWithLifecycle(initialValue = "")
    val updateLatestVersion by settingsStore.updateLatestVersion.collectAsStateWithLifecycle(initialValue = "")
    val updateLastError by settingsStore.updateLastError.collectAsStateWithLifecycle(initialValue = "")
    val currentLogs by LogManager.logs.collectAsStateWithLifecycle()
    val currentVersion = remember { "v${BuildConfig.VERSION_NAME.removePrefix("v")}" }
    val updateStatusSubtitle = remember(isCheckingUpdates, updateLatestVersion, updateLastError, currentVersion) {
        when {
            isCheckingUpdates -> "Проверяем GitHub releases..."
            updateLatestVersion.isNotBlank() && isNewerVersion(currentVersion, updateLatestVersion) ->
                "На GitHub доступна версия $updateLatestVersion"
            updateLatestVersion.isNotBlank() -> "Последняя версия: $updateLatestVersion"
            updateLastError.isNotBlank() -> "Последняя проверка завершилась ошибкой"
            else -> "Проверить GitHub вручную"
        }
    }
    val reportText = remember(
        savedPort,
        savedPoolSize,
        savedCfEnabled,
        savedCustomCfDomainEnabled,
        savedCustomCfDomain,
        currentLogs
    ) {
        buildSupportReport(
            port = savedPort,
            poolSize = savedPoolSize,
            cfEnabled = savedCfEnabled,
            customCfDomainEnabled = savedCustomCfDomainEnabled,
            customCfDomain = savedCustomCfDomain,
            logs = currentLogs
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 28.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Информация",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        InfoHeroCard(onSupportClick = { showDonateDialog = true })

        ExpandableSectionCard(
            title = "Действия",
            itemCount = "4 пункта",
            expanded = actionsExpanded,
            onToggle = { actionsExpanded = !actionsExpanded },
            icon = {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                InfoActionTile(
                    title = "Поднять вопрос",
                    subtitle = "Открыть GitHub issue",
                    modifier = Modifier.weight(1f),
                    onClick = { openUrlInBrowser(context, AndroidForkIssuesUrl) },
                    icon = {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_github),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                )

                InfoActionTile(
                    title = "Собрать отчёт",
                    subtitle = "Android, ABI, настройки, ошибки",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val clipboard = context.getSystemService(ClipboardManager::class.java)
                        clipboard?.setPrimaryClip(ClipData.newPlainText("TgWsProxy Report", reportText))
                        Toast.makeText(context, "Отчёт сформирован и скопирован", Toast.LENGTH_SHORT).show()
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                )
            }

            WideActionTile(
                title = "Справка",
                subtitle = "Коротко про Cloudflare, пул WS, ручные DC и долгий запуск",
                onClick = { showHelpDialog = true },
                icon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            )

            WideActionTile(
                title = "Проверить обновления",
                subtitle = updateStatusSubtitle,
                onClick = {
                    if (isCheckingUpdates) return@WideActionTile
                    isCheckingUpdates = true
                    scope.launch {
                        val checkedAt = System.currentTimeMillis()
                        val release = fetchLatestReleaseInfo(currentVersion)
                        settingsStore.saveUpdateState(
                            lastCheckAt = checkedAt,
                            latestVersion = release?.versionTag ?: "",
                            error = if (release == null) "Не удалось проверить" else ""
                        )
                        isCheckingUpdates = false

                        if (release == null) {
                            val message = if (updateLatestVersion.isNotBlank()) {
                                "Не удалось проверить. Последняя известная версия: $updateLatestVersion"
                            } else {
                                "Не удалось проверить обновления"
                            }
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            return@launch
                        }

                        if (isNewerVersion(currentVersion, release.versionTag)) {
                            settingsStore.saveUpdateDialogShown(release.versionTag, checkedAt)
                            pendingManualRelease = release
                        } else {
                            Toast.makeText(
                                context,
                                "У вас уже последняя версия: ${release.versionTag}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Update,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            )
        }

        ExpandableSectionCard(
            title = "О проекте",
            itemCount = "4 ссылки",
            expanded = projectExpanded,
            onToggle = { projectExpanded = !projectExpanded },
            icon = {
                Icon(
                    imageVector = Icons.Default.Code,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        ) {
            ProjectLinkRow(
                title = "Автор Android-версии",
                subtitle = "GitHub профиль amurcanov",
                onClick = { openUrlInBrowser(context, DeveloperProfileUrl) },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )

            ProjectLinkRow(
                title = "Репозиторий Android-форка",
                subtitle = "Исходники и релизы этого приложения",
                onClick = { openUrlInBrowser(context, AndroidForkRepoUrl) },
                icon = {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_github),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )

            ProjectLinkRow(
                title = "Оригинальный tg-ws-proxy",
                subtitle = "Исходная идея и upstream от Flowseal",
                onClick = { openUrlInBrowser(context, OriginalProjectUrl) },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )

            ProjectLinkRow(
                title = "Полезный материал",
                subtitle = "Заметки по работе прокси от IMDelewer",
                onClick = { openUrlInBrowser(context, ProxyReferenceUrl) },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

    }

    if (showDonateDialog) {
        DonateDialog(onDismiss = { showDonateDialog = false })
    }

    pendingManualRelease?.let { release ->
        AppUpdateDialog(
            release = release,
            onPostpone = {
                pendingManualRelease = null
                Toast.makeText(context, "Обновление отложено на 24 часа.", Toast.LENGTH_SHORT).show()
                scope.launch {
                    val now = System.currentTimeMillis()
                    settingsStore.saveUpdatePostpone(
                        version = release.versionTag,
                        until = now + 24L * 60L * 60L * 1000L
                    )
                    settingsStore.saveUpdateDialogAction(
                        version = release.versionTag,
                        action = UPDATE_DIALOG_ACTION_POSTPONED,
                        actedAt = now
                    )
                }
            },
            onUpdate = {
                pendingManualRelease = null
                scope.launch {
                    settingsStore.saveUpdateDialogAction(
                        version = release.versionTag,
                        action = UPDATE_DIALOG_ACTION_UPDATE,
                        actedAt = System.currentTimeMillis()
                    )
                    openUrlInBrowser(context, release.releaseUrl)
                }
            }
        )
    }

    if (showHelpDialog) {
        Dialog(
            onDismissRequest = { showHelpDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = RoundedCornerShape(32.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.85f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Spacer(Modifier.height(28.dp))

                    Text(
                        "Справка",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        HelpSection(
                            title = "Авто / Адреса датацентров",
                            text = "При включенном CloudFlare ручные адреса DC обычно не нужны: прокси использует CF-маршрут. Если CloudFlare выключен, соединение идёт напрямую на Telegram DC, и тогда можно задать адреса вручную. В обычном режиме чаще всего достаточно DC2 и DC4; остальные адреса нужны в основном для ручной настройки и диагностики."
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        HelpSection(
                            title = "CloudFlare CDN",
                            text = "Этот режим направляет соединение через WebSocket-домены за Cloudflare. На части мобильных сетей он работает стабильнее, но итог зависит от маршрута, DNS и конкретного провайдера. Если на вашей сети подключение стало дольше или менее стабильным, имеет смысл сравнить работу с выключенным CF."
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        HelpSection(
                            title = "Пул WS",
                            text = "Количество заранее подготовленных WebSocket-соединений. Больший пул может уменьшить задержку при первом подключении и загрузке медиа, но увеличивает число фоновых соединений. Для большинства сценариев достаточно 2-4; повышать значение стоит только если реально видна польза на вашей сети."
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        HelpSection(
                            title = "Секретный ключ",
                            text = "Специальный 16-байтовый ключ шифрования MTProto. Меняйте его только в случае, если старой ссылкой для подключения завладели посторонние."
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        HelpSection(
                            title = "Экспериментальный режим",
                            text = "Открывает ручную настройку всех обычных и media-датацентров: DC1, DC3, DC5, DC203 и их media-вариантов. Он нужен для диагностики, тестов и нестандартных маршрутов. Если у вас нет явной задачи под ручную маршрутизацию, лучше держать этот режим выключенным."
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        HelpSection(
                            title = "Автозапуск",
                            text = "Включает попытку поднять прокси автоматически после перезагрузки устройства. Это удобно, если вы используете локальный прокси постоянно. На некоторых прошивках запуск может произойти не мгновенно: система иногда завершает его только после полной загрузки Android или первого разблокирования."
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        HelpSection(
                            title = "Если долго подключается",
                            text = "Если после запуска прокси Telegram долго висит на подключении, это обычно означает неудачный текущий маршрут, а не падение приложения. В такой ситуации полезно быстро перезапустить прокси и сравнить поведение с включенным и выключенным CloudFlare."
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    Button(
                        onClick = { showHelpDialog = false },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text("Понятно", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }

                    Spacer(Modifier.height(28.dp))
                }
            }
        }
    }
}

@Composable
private fun InfoHeroCard(onSupportClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val isDark = colors.background.luminance() < 0.22f
    val heroBrush = remember(colors.primaryContainer, colors.secondaryContainer, colors.surfaceVariant) {
        Brush.linearGradient(
            listOf(
                colors.primaryContainer,
                colors.secondaryContainer,
                colors.surfaceVariant
            )
        )
    }
    val glassColor = if (isDark) {
        colors.surface.copy(alpha = 0.46f)
    } else {
        Color.White.copy(alpha = 0.54f)
    }
    val glassBorder = colors.outlineVariant.copy(alpha = if (isDark) 0.50f else 0.32f)
    val titleColor = if (isDark) colors.onSurface else colors.onSurface
    val supportAccent = if (isDark) DonateActionButtonColor.copy(alpha = 0.92f) else DonateActionButtonColor

    Surface(
        shape = RoundedCornerShape(32.dp),
        color = Color.Transparent,
        shadowElevation = 10.dp,
        tonalElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .background(heroBrush)
                .padding(22.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 30.dp, y = (-34).dp)
                    .size(138.dp)
                    .clip(Android16BlobShape)
                    .background(colors.primary.copy(alpha = 0.10f))
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 26.dp, y = 30.dp)
                    .size(112.dp)
                    .clip(Android16BlobShape)
                    .background(colors.secondary.copy(alpha = 0.12f))
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HeroMetaPill(
                        text = "Amurcanov Fork",
                        containerColor = glassColor,
                        borderColor = glassBorder,
                        modifier = Modifier.weight(1f)
                    )
                    HeroMetaPill(
                        text = "Flowseal Base",
                        containerColor = colors.primary.copy(alpha = if (isDark) 0.18f else 0.10f),
                        borderColor = colors.primary.copy(alpha = if (isDark) 0.22f else 0.14f),
                        modifier = Modifier.weight(1f)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Telegram WS Proxy",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Black,
                            fontSize = 30.sp,
                            lineHeight = 34.sp
                        ),
                        color = titleColor
                    )
                    Text(
                        text = "Локальный MTProto-прокси для Android с прямым маршрутом и Cloudflare-режимом. Удобен для сетей, где Telegram грузится нестабильно или упирается в маршрут.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        lineHeight = 21.sp
                    )
                }

                Button(
                    onClick = onSupportClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(22.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = supportAccent,
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Поддержать развитие",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroMetaPill(
    text: String,
    containerColor: Color,
    borderColor: Color,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            icon?.invoke()
            if (icon != null) {
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ExpandableSectionCard(
    title: String,
    itemCount: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    icon: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "section_arrow_rotation"
    )

    AppSectionCard(
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .clickable(onClick = onToggle)
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(
                    modifier = Modifier.size(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    icon()
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            MetaChip(text = itemCount)

            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(24.dp)
                    .rotate(arrowRotation)
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f))
                content()
            }
        }
    }
}

@Composable
private fun MetaChip(text: String) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun InfoActionTile(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.70f),
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 116.dp)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(
                    modifier = Modifier.size(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    icon()
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
private fun WideActionTile(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.70f),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 15.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(
                    modifier = Modifier.size(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    icon()
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun ProjectLinkRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.64f),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(
                    modifier = Modifier.defaultMinSize(minWidth = 40.dp, minHeight = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    icon()
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun SupportAccentCard(onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme

    Surface(
        shape = RoundedCornerShape(32.dp),
        color = colors.secondaryContainer.copy(alpha = 0.94f),
        shadowElevation = 10.dp,
        tonalElevation = 2.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 18.dp, y = (-20).dp)
                    .size(88.dp)
                    .clip(Android16BlobShape)
                    .background(colors.primary.copy(alpha = 0.09f))
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colors.surface.copy(alpha = 0.88f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = DonateActionButtonColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Поддержка проекта",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Если приложение тебе реально помогает, проект можно поддержать.",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = colors.onSurface
                    )
                    Text(
                        text = "Внутри есть варианты доната для автора Android-версии и для автора оригинальной идеи.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        lineHeight = 21.sp
                    )
                }

                Button(
                    onClick = onClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DonateActionButtonColor,
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Открыть варианты доната",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

private fun buildSupportReport(
    port: String,
    poolSize: Int,
    cfEnabled: Boolean,
    customCfDomainEnabled: Boolean,
    customCfDomain: String,
    logs: List<LogEntry>
): String {
    val androidVersion = Build.VERSION.RELEASE ?: "?"
    val sdkInt = Build.VERSION.SDK_INT
    val primaryAbi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty().ifBlank { "unknown" }
    val supportedAbis = Build.SUPPORTED_ABIS.joinToString().ifBlank { "unknown" }
    val supported32Abis = Build.SUPPORTED_32_BIT_ABIS.joinToString().ifBlank { "none" }
    val supported64Abis = Build.SUPPORTED_64_BIT_ABIS.joinToString().ifBlank { "none" }
    val manufacturer = Build.MANUFACTURER.orEmpty().ifBlank { "unknown" }
    val brand = Build.BRAND.orEmpty().ifBlank { "unknown" }
    val model = Build.MODEL.orEmpty().ifBlank { "unknown" }
    val device = Build.DEVICE.orEmpty().ifBlank { "unknown" }
    val product = Build.PRODUCT.orEmpty().ifBlank { "unknown" }
    val hardware = Build.HARDWARE.orEmpty().ifBlank { "unknown" }
    val board = Build.BOARD.orEmpty().ifBlank { "unknown" }
    val romDisplay = Build.DISPLAY.orEmpty().ifBlank { "unknown" }
    val buildId = Build.ID.orEmpty().ifBlank { "unknown" }
    val buildFingerprint = Build.FINGERPRINT.orEmpty().ifBlank { "unknown" }
    val buildType = Build.TYPE.orEmpty().ifBlank { "unknown" }
    val socManufacturer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Build.SOC_MANUFACTURER.orEmpty().ifBlank { "unknown" }
    } else {
        "n/a"
    }
    val socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Build.SOC_MODEL.orEmpty().ifBlank { "unknown" }
    } else {
        "n/a"
    }
    val mode = if (cfEnabled) "Cloudflare" else "Прямой"
    val cfDomainLine = if (cfEnabled && customCfDomainEnabled && customCfDomain.isNotBlank()) {
        "\nCF-домен: ${customCfDomain.trim()}"
    } else {
        ""
    }

    val recentErrors = logs
        .asReversed()
        .filter { it.priority >= 5 }
        .take(10)

    val errorsBlock = if (recentErrors.isEmpty()) {
        "нет"
    } else {
        recentErrors.joinToString("\n") { entry ->
            val level = when (entry.priority) {
                6 -> "ERROR"
                5 -> "WARN"
                else -> "INFO"
            }
            "- [$level] ${entry.message}${if (entry.count > 1) " (x${entry.count})" else ""}"
        }
    }

    return buildString {
        appendLine("Версия приложения: ${BuildConfig.VERSION_NAME}")
        appendLine("Андроид: $androidVersion (SDK $sdkInt)")
        appendLine("Устройство: $manufacturer / $brand / $model")
        appendLine("Код устройства: $device")
        appendLine("Продукт: $product")
        appendLine("ABI: $primaryAbi")
        appendLine("Все ABI: $supportedAbis")
        appendLine("32-bit ABI: $supported32Abis")
        appendLine("64-bit ABI: $supported64Abis")
        appendLine("SoC: $socManufacturer / $socModel")
        appendLine("Hardware: $hardware")
        appendLine("Board: $board")
        appendLine("ROM: $romDisplay")
        appendLine("Build ID: $buildId")
        appendLine("Build type: $buildType")
        appendLine("Fingerprint: $buildFingerprint")
        appendLine("Настройки:")
        appendLine("Режим: $mode")
        appendLine("WS-пул: $poolSize")
        append("Порт: ${port.trim().ifBlank { "1443" }}")
        append(cfDomainLine)
        appendLine()
        appendLine()
        appendLine("Последние ошибки:")
        append(errorsBlock)
    }.trim()
}

@Composable
private fun DonateDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 10.dp,
            shadowElevation = 14.dp,
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Донат разработчикам",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    FilledTonalIconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Закрыть"
                        )
                    }
                }

                DonateSection(
                    title = "Задонатить автору данного андроид приложения вы можете тут",
                    buttonColor = AppColors.donate,
                    onClick = { openUrlInBrowser(context, AndroidAppDonateUrl) }
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_yoomoney),
                        contentDescription = "ЮMoney",
                        tint = Color.Unspecified,
                        modifier = Modifier
                            .width(126.dp)
                            .height(28.dp)
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

                DonateSection(
                    title = "Задонатить автору оригинальной идеи вы можете тут",
                    buttonColor = OriginalIdeaDonateColor,
                    onClick = { openUrlInBrowser(context, OriginalIdeaDonateUrl) }
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_crypto_wordmark),
                        contentDescription = "Crypto",
                        tint = Color.Unspecified,
                        modifier = Modifier
                            .width(138.dp)
                            .height(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DonateSection(
    title: String,
    buttonColor: Color,
    onClick: () -> Unit,
    buttonContent: @Composable RowScope.() -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        Button(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp),
            shape = RoundedCornerShape(22.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = buttonColor,
                contentColor = Color.White
            )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                content = buttonContent
            )
        }
    }
}

@Composable
private fun HelpSection(title: String, text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 20.sp
        )
    }
}
