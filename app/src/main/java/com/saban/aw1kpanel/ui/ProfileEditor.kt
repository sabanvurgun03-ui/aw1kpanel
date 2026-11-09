package com.saban.aw1kpanel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.json.JSONObject
import com.saban.aw1kpanel.data.LinkParser

/** JSON metninde address / uuid / password değerlerini aynı uzunlukta • ile gösterir (metin değişmez). */
private val JsonSecretMask = VisualTransformation { text ->
    val src = text.text
    val out = StringBuilder(src)
    Regex("\"(address|uuid|password|pass)\"\\s*:\\s*\"([^\"]*)\"", RegexOption.IGNORE_CASE).findAll(src).forEach { m ->
        val g = m.groups[2] ?: return@forEach
        for (i in g.range) out.setCharAt(i, '•')
    }
    androidx.compose.ui.text.input.TransformedText(
        androidx.compose.ui.text.AnnotatedString(out.toString()),
        androidx.compose.ui.text.input.OffsetMapping.Identity,
    )
}

/** KediRay düğümü formunda sıralı gösterilen alanlar (modemin düğüm alanlarıyla aynı adlar). */
private val KEDI_ORDER = listOf(
    "remarks", "protocol", "address", "port", "uuid", "password", "method", "flow", "encryption",
    "network", "path", "host", "tls", "sni", "alpn", "fp", "ech", "insecure",
)

/** Modem boş alanları kaydetmez; bu alanlar düğümde değer yokken de formda görünür ki elle doldurulabilsin. */
private val KEDI_ALWAYS = setOf(
    "remarks", "protocol", "address", "port", "uuid", "flow", "encryption",
    "network", "path", "host", "tls", "sni", "alpn", "fp", "ech", "insecure",
)

private val FIELD_HINTS = mapOf(
    "sni" to "TLS sunucu adı (SNI). Boş bırakılırsa gönderilmez.",
    "host" to "WebSocket / HTTP Host başlığı. Boş bırakılırsa gönderilmez.",
)

/** Form alanları: anahtar -> metin. Tür bilgisi kaydederken özgün JSON'dan korunur. */
private fun toForm(json: String, kedi: Boolean = false): Pair<JSONObject, MutableMap<String, String>>? = try {
    val o = JSONObject(json)
    val m = linkedMapOf<String, String>()
    o.keys().forEach { k ->
        val v = o.get(k)
        if (v is String || v is Number || v is Boolean) m[k] = v.toString()
    }
    val form: MutableMap<String, String> = if (kedi && o.optString("raw").isEmpty()) {
        val ordered = linkedMapOf<String, String>()
        KEDI_ORDER.forEach { k ->
            val v = m[k]
            if (v != null) ordered[k] = v else if (k in KEDI_ALWAYS) ordered[k] = ""
        }
        m.forEach { (k, v) -> if (k !in ordered) ordered[k] = v }
        ordered
    } else {
        m
    }
    o to form
} catch (e: Exception) {
    null
}

private fun toJson(base: JSONObject, form: Map<String, String>): String {
    val out = JSONObject(base.toString())
    form.forEach { (k, v) ->
        val old = base.opt(k)
        val value: Any = when (old) {
            is Int -> v.trim().toIntOrNull() ?: v
            is Long -> v.trim().toLongOrNull() ?: v
            is Double -> v.trim().toDoubleOrNull() ?: v
            is Boolean -> v.trim().lowercase().let { if (it == "true") true else if (it == "false") false else v }
            else -> v
        }
        out.put(k, value)
    }
    return out.toString(2)
}

@Composable
internal fun ProfileEditor(
    initial: ProfileEditorState,
    onSave: (String) -> Unit,
    onClose: () -> Unit,
) {
    val kediMode = initial.kind == "kediray"
    var tab by remember { mutableStateOf(0) }
    var jsonText by remember { mutableStateOf(initial.json) }
    var error by remember { mutableStateOf<String?>(null) }
    var link by remember { mutableStateOf("") }
    var hideJson by remember { mutableStateOf(true) }
    var shown by remember { mutableStateOf(setOf<String>()) }

    // Form durumu, JSON metninden türetilir; sekme değişince yeniden okunur.
    var base by remember { mutableStateOf(toForm(initial.json, kediMode)?.first ?: JSONObject()) }
    var form by remember { mutableStateOf(toForm(initial.json, kediMode)?.second ?: linkedMapOf()) }

    fun switchTo(t: Int) {
        if (t == tab) return
        if (t == 1) {
            // Form -> JSON
            jsonText = toJson(base, form)
            error = null
            tab = 1
        } else {
            // JSON -> Form
            val parsed = toForm(jsonText, kediMode)
            if (parsed == null) {
                error = "JSON geçersiz; önce düzelt"
            } else {
                base = parsed.first
                form = parsed.second
                error = null
                tab = 0
            }
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .imePadding()
                    .padding(horizontal = 16.dp),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        when {
                            initial.kind == "kediray" -> if (initial.id == null) "Yeni KediRay düğümü" else "KediRay düğümünü düzenle"
                            initial.id == null -> "Yeni profil"
                            else -> "Profili düzenle"
                        },
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Row {
                        TextButton(onClick = onClose) { Text("Vazgeç") }
                        Button(onClick = {
                            val finalJson = if (tab == 0) toJson(base, form) else jsonText
                            try {
                                JSONObject(finalJson)
                                error = null
                                onSave(finalJson)
                            } catch (e: Exception) {
                                error = "JSON geçersiz: ${e.message}"
                            }
                        }) { Text("Kaydet") }
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = link,
                        onValueChange = { link = it },
                        label = { Text("Link yapıştır (vless:// vmess:// trojan:// ss://)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        enabled = link.isNotBlank(),
                        onClick = {
                            val parsed = LinkParser.parse(link)
                            val kedi = initial.kind == "kediray"
                            val p = if (kedi) parsed?.let { LinkParser.toKedi(it) } else parsed
                            if (p == null) {
                                error = "Link tanınamadı"
                            } else {
                                // Düzenleme modunda profil kimliğini koru.
                                if (!kedi) initial.id?.let { p.put("id", it) }
                                jsonText = p.toString(2)
                                toForm(jsonText, kediMode)?.let { base = it.first; form = it.second }
                                link = ""
                                error = null
                            }
                        },
                    ) { Text("Çevir") }
                }
                TabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { switchTo(0) }, text = { Text("Alanlar") })
                    Tab(selected = tab == 1, onClick = { switchTo(1) }, text = { Text("JSON") })
                }
                error?.let {
                    Text(
                        it,
                        color = Bad,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                if (tab == 0) {
                    Column(
                        Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        form.keys.toList().forEach { k ->
                            val sensitive = k.lowercase() in setOf("address", "uuid", "password", "pass")
                            val visible = k in shown
                            val hint = if (kediMode) FIELD_HINTS[k] else null
                            OutlinedTextField(
                                value = form[k].orEmpty(),
                                onValueChange = { nv -> form = LinkedHashMap(form).also { it[k] = nv } },
                                label = { Text(k) },
                                enabled = k != "id",
                                singleLine = true,
                                supportingText = if (hint != null) {
                                    { Text(hint) }
                                } else null,
                                visualTransformation = if (sensitive && !visible) PasswordVisualTransformation() else VisualTransformation.None,
                                trailingIcon = if (sensitive) {
                                    {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            androidx.compose.material3.Checkbox(
                                                checked = !visible,
                                                onCheckedChange = { hide -> shown = if (hide) shown - k else shown + k },
                                            )
                                            Text("Gizle", style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                } else null,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Text(
                            "Adres, UUID ve şifre alanları gizli gelir; kutuyu kaldırınca görünür. Yalnızca düz değerler burada görünür; iç içe alanlar JSON sekmesinden düzenlenir (JSON sekmesinde de aynı değerler gizli gelir).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.Checkbox(checked = hideJson, onCheckedChange = { hideJson = it })
                        Text("Adres, UUID ve şifre değerlerini gizle", style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedTextField(
                        value = jsonText,
                        onValueChange = { jsonText = it },
                        visualTransformation = if (hideJson) JsonSecretMask else VisualTransformation.None,
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                    )
                }
            }
        }
    }
}
