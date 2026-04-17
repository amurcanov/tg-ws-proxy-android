package com.amurcanov.tgwsproxy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.amurcanov.tgwsproxy.R

private const val InfoTabLinkTag = "info_tab_link"
private const val AndroidAppDonateUrl = ""
private const val OriginalIdeaDonateUrl = ""
private val DonateActionButtonColor = Color(0xFF00AEA5)
private val OriginalIdeaDonateColor = Color(0xFFFF8A24)

@Composable
fun InfoTab() {
    var showHelpDialog by remember { mutableStateOf(false) }
    var showDonateDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Дополнительная информация",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Start
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        AppSectionCard {
            Text(
                text = "amurcanov",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

            GitHubSection(
                buttonText = "Страница разработчика",
                url = "https://github.com/amurcanov"
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

            GitHubSection(
                buttonText = "Поднять вопрос",
                url = "https://github.com/amurcanov/tg-ws-proxy-android/issues/new"
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

            Button(
                onClick = { showHelpDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.HelpOutline,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Справка", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

            Button(
                onClick = { showDonateDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    "Донат разработчикам",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        ForkInfoText(
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))
    }

    if (showDonateDialog) {
        DonateDialog(
            onDismiss = { showDonateDialog = false }
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
private fun GitHubSection(
    buttonText: String,
    url: String
) {
    val context = LocalContext.current
    Button(
        onClick = { openUrlInBrowser(context, url) },
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(id = R.drawable.ic_github),
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = buttonText,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            )
        }
    }
}

@Composable
private fun ForkInfoText(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val linkColor = MaterialTheme.colorScheme.primary
    val contentColor = MaterialTheme.colorScheme.onBackground
    val annotatedText = remember(linkColor) {
        buildAnnotatedString {
            append("Приложение является форком репозитория ")
            appendExternalLink("tg-ws-proxy", "https://github.com/Flowseal/tg-ws-proxy", linkColor)
            append(" от ")
            appendExternalLink("Flowseal", "https://github.com/Flowseal", linkColor)
            append(", с собственной переработкой, адаптацией, улучшениями под андроид приложения. ")
            append("Дополнительная полезная информация по работе прокси находится здесь - ")
            appendExternalLink("IMDelewer", "https://github.com/Flowseal/tg-ws-proxy/issues/389", linkColor)
        }
    }

    ClickableText(
        text = annotatedText,
        modifier = modifier,
        style = MaterialTheme.typography.bodyLarge.copy(
            color = contentColor,
            fontWeight = FontWeight.Medium,
            lineHeight = 24.sp,
            textAlign = TextAlign.Center
        ),
        onClick = { offset ->
            annotatedText
                .getStringAnnotations(InfoTabLinkTag, offset, offset)
                .firstOrNull()
                ?.let { annotation ->
                    openUrlInBrowser(context, annotation.item)
                }
        }
    )
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

private fun AnnotatedString.Builder.appendExternalLink(
    label: String,
    url: String,
    color: Color
) {
    val start = length
    append(label)
    addStyle(
        style = SpanStyle(
            color = color,
            fontWeight = FontWeight.Bold,
            textDecoration = TextDecoration.Underline
        ),
        start = start,
        end = length
    )
    addStringAnnotation(
        tag = InfoTabLinkTag,
        annotation = url,
        start = start,
        end = length
    )
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
