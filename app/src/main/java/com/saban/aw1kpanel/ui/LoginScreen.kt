package com.saban.aw1kpanel.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.saban.aw1kpanel.R

@Composable
fun LoginScreen(
    state: UiState,
    initialHost: String,
    initialUser: String,
    onLogin: (host: String, user: String, pass: String) -> Unit,
    onPick: (String) -> Unit,
) {
    var host by remember { mutableStateOf(initialHost) }
    var user by remember { mutableStateOf(initialUser) }
    var pass by remember { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }

    val focus = LocalFocusManager.current
    // Klavye açılınca logo küçülür; alanlar sıkışıp üst üste binmez, sayfa kayar.
    val imeOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val logoSize by animateDpAsState(
        targetValue = if (imeOpen) 52.dp else 88.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "logo",
    )
    val canLogin = !state.loginBusy && pass.isNotEmpty() && host.isNotBlank()
    val fieldShape = RoundedCornerShape(16.dp)

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding(),
        ) {
            val minH = maxHeight
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = minH)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                KediLogo(logoSize)
                Spacer(Modifier.height(if (imeOpen) 8.dp else 16.dp))
                Text(
                    "AW1K Panel",
                    style = if (imeOpen) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                )
                if (!imeOpen) {
                    Text(
                        "Modemine bağlan",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(if (imeOpen) 12.dp else 24.dp))

                AnimatedVisibility(
                    visible = shown,
                    enter = fadeIn(tween(450)) + slideInVertically(tween(450)) { it / 8 },
                ) {
                    Column {
                        if (state.routers.isNotEmpty()) {
                            Text("Kayıtlı modemler", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(8.dp))
                            state.routers.forEach { r ->
                                Surface(
                                    onClick = { onPick(r.id) },
                                    enabled = !state.loginBusy,
                                    shape = RoundedCornerShape(18.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                ) {
                                    Row(
                                        Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        IconBadge(
                                            icon = R.drawable.ic_modem,
                                            tint = MaterialTheme.colorScheme.primary,
                                            background = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                            size = 40,
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(r.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                            Text(
                                                r.host,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                            Text("Yeni modem", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(8.dp))
                        }
                        OutlinedTextField(
                            value = host,
                            onValueChange = { host = it },
                            label = { Text("Modem adresi") },
                            singleLine = true,
                            shape = fieldShape,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = user,
                            onValueChange = { user = it },
                            label = { Text("Kullanıcı adı") },
                            singleLine = true,
                            shape = fieldShape,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = pass,
                            onValueChange = { pass = it },
                            label = { Text("Şifre") },
                            singleLine = true,
                            shape = fieldShape,
                            visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                if (canLogin) {
                                    focus.clearFocus()
                                    onLogin(host, user, pass)
                                }
                            }),
                            trailingIcon = {
                                TextButton(onClick = { showPass = !showPass }) { Text(if (showPass) "Gizle" else "Göster") }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        state.loginError?.let {
                            Spacer(Modifier.height(12.dp))
                            Text(it, color = Bad, style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(Modifier.height(20.dp))
                        Button(
                            onClick = { onLogin(host, user, pass) },
                            enabled = canLogin,
                            shape = RoundedCornerShape(26.dp),
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                        ) {
                            if (state.loginBusy) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Giriş")
                            }
                        }
                    }
                }
            }
        }
    }
}
