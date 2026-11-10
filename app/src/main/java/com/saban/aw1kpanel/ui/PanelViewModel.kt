package com.saban.aw1kpanel.ui

import java.io.File
import com.saban.aw1kpanel.data.AppUpdater
import com.saban.aw1kpanel.data.AppRelease
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.saban.aw1kpanel.data.AppSettings
import com.saban.aw1kpanel.data.BandScanState
import com.saban.aw1kpanel.data.BandScanner
import com.saban.aw1kpanel.data.Connection
import com.saban.aw1kpanel.data.CredentialStore
import com.saban.aw1kpanel.data.Device
import com.saban.aw1kpanel.data.KediState
import com.saban.aw1kpanel.data.KediNode
import com.saban.aw1kpanel.data.ModemState
import com.saban.aw1kpanel.data.ModemdataGroup
import com.saban.aw1kpanel.data.PanelRepository
import com.saban.aw1kpanel.data.PingResult
import com.saban.aw1kpanel.data.Router
import com.saban.aw1kpanel.data.SettingsStore
import com.saban.aw1kpanel.data.SystemState
import com.saban.aw1kpanel.data.UbusClient
import com.saban.aw1kpanel.data.UbusException
import com.saban.aw1kpanel.data.UsageData
import com.saban.aw1kpanel.data.VpnProfile
import com.saban.aw1kpanel.data.VpnState
import com.saban.aw1kpanel.data.WifiDetail
import com.saban.aw1kpanel.data.WifiNetwork
import com.saban.aw1kpanel.data.LinkParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.IOException

/** [kind]: "xwrt" (xDivine-Ray profili) ya da "kediray" (uci düğümü; [id] bölüm adıdır). */
data class ProfileEditorState(val id: String?, val json: String, val kind: String = "xwrt")

data class UpdateState(
    val busy: Boolean = false,
    val fileName: String = "",
    val steps: Int = 0,
    val label: String = "",
    val log: String = "",
    val finished: Boolean = false,
    val ok: Boolean = false,
    val checking: Boolean = false,
    val checkMsg: String = "",
    val newer: Boolean = false,
)

/** Uygulamanın kendi güncelleme durumu (GitHub Releases). */
data class AppUpdateUi(
    val checking: Boolean = false,
    val checked: Boolean = false,
    val release: AppRelease? = null,
    val newer: Boolean = false,
    val message: String = "",
    val downloading: Boolean = false,
    val progress: Float = 0f,
    /** İndirilmiş APK'nın yolu (kuruluma hazır). */
    val ready: String? = null,
)

data class UiState(
    val checkingSaved: Boolean = true,
    /** Kayıtlı modeme ulaşılamadı (telefon modemin ağında değil): "Router'a bağlı değil" ekranı gösterilir. */
    val offline: Boolean = false,
    val retrying: Boolean = false,
    val loggedIn: Boolean = false,
    val loginBusy: Boolean = false,
    val loginError: String? = null,
    val routers: List<Router> = emptyList(),
    val activeRouterId: String? = null,
    val modem: ModemState = ModemState(),
    val modemError: String? = null,
    val linkAction: String? = null,
    val system: SystemState? = null,
    val modemdata: List<ModemdataGroup> = emptyList(),
    val modemdataError: String? = null,
    val lockband: Map<String, com.saban.aw1kpanel.data.BandGroup>? = null,
    val lockbandError: String? = null,
    val bandUnlocked: Boolean = false,
    val bandScan: BandScanState = BandScanState(),
    val wifi: List<WifiNetwork> = emptyList(),
    val guest: com.saban.aw1kpanel.data.GuestWifi? = null,
    val wifiClients: List<com.saban.aw1kpanel.data.WifiClient> = emptyList(),
    /** Wi-Fi istemcisi başına anlık hız (bayt/sn): MAC (büyük harf) -> (indirme, yükleme). */
    val deviceRates: Map<String, Pair<Float, Float>> = emptyMap(),
    /** KediRay sayaçlarından (kablolu cihazlar dahil) IP başına anlık hız (bayt/sn): IP -> (indirme, yükleme). */
    val ipRates: Map<String, Pair<Float, Float>> = emptyMap(),
    val appUpdate: AppUpdateUi = AppUpdateUi(),
    val devices: List<Device> = emptyList(),
    val shares: com.saban.aw1kpanel.data.DeviceShares? = null,
    val trafficDays: List<com.saban.aw1kpanel.data.TrafficDay> = emptyList(),
    val blocked: Set<String> = emptySet(),
    val vpn: VpnState? = null,
    val kediray: KediState? = null,
    val vpnProfileId: String? = null,
    val profiles: List<VpnProfile> = emptyList(),
    val activeProfileId: String? = null,
    val usage: UsageData? = null,
    val editor: ProfileEditorState? = null,
    val wifiEditor: WifiDetail? = null,
    val pings: Map<String, PingResult> = emptyMap(),
    val pinging: Set<String> = emptySet(),
    /** KediRay düğümlerinin son ping sonucu (düğüm bölümü -> sonuç); [kediPinging] ölçülenler, "all" tümünü ölçerken. */
    val kediPings: Map<String, com.saban.aw1kpanel.data.KediPing> = emptyMap(),
    val kediPinging: Set<String> = emptySet(),
    /** KediRay ayarlar ekranı: açık mı, okunan değerler, kaydediliyor mu. */
    val netDetails: com.saban.aw1kpanel.data.NetDetails? = null,
    val sysDetails: com.saban.aw1kpanel.data.SysDetails? = null,
    val kediSettingsOpen: Boolean = false,
    val kediSettings: com.saban.aw1kpanel.data.KediSettingsData? = null,
    val kediSettingsError: String? = null,
    val kediSettingsSaving: Boolean = false,
    val update: UpdateState = UpdateState(),
    /** Modemde kurulum bittikten sonra bir kez gösterilen "güncelleme yüklendi" bilgisi. */
    val whatsNew: com.saban.aw1kpanel.data.WhatsNew? = null,
    val logs: com.saban.aw1kpanel.data.LogData? = null,
    val logsError: String? = null,
    val sms: List<com.saban.aw1kpanel.data.SmsMessage>? = null,
    val smsError: String? = null,
    val smsBusy: Boolean = false,
    val smsFilter: com.saban.aw1kpanel.data.SmsFilterState? = null,
    val smsRun: com.saban.aw1kpanel.data.SmsRunResult? = null,
    val message: String? = null,
)

class PanelViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = PanelRepository(UbusClient())
    private val store = CredentialStore(app)
    private val settingsStore = SettingsStore(app)
    private val scanner = BandScanner(app, repo)
    private var scanJob: Job? = null

    private val _settings = MutableStateFlow(settingsStore.load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _state = MutableStateFlow(
        UiState(routers = store.routers(), activeRouterId = store.activeId()),
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    val lastHost: String get() = store.lastHost()
    val lastUser: String get() = store.lastUser()

    init {
        viewModelScope.launch {
            val saved = store.load()
            if (saved != null) {
                try {
                    repo.login(saved)
                    _state.update { it.copy(loggedIn = true) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Parola hatası değilse modeme ulaşılamıyordur: giriş formu yerine "bağlı değil" ekranı.
                    val unreachable = e is IOException
                    _state.update { it.copy(loginError = friendly(e), offline = unreachable) }
                }
            }
            _state.update { it.copy(checkingSaved = false) }
        }
    }

    /** SMS sayfası: SIM'deki mesajları çeker. */
    suspend fun loadSms() {
        try {
            val l = repo.smsInbox()
            _state.update { it.copy(sms = l, smsError = null) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(smsError = friendly(e)) }
        }
    }

    fun refreshSms() {
        viewModelScope.launch { loadSms() }
    }

    suspend fun loadSmsFilter() {
        try {
            val f = repo.smsFilter()
            _state.update { it.copy(smsFilter = f) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Eski KediRay (filtre yok): sessizce geç, filtre kartı "KediRay'i güncelle" der
        }
    }

    /** Kuralları hemen ekranda güncelle, modeme yaz; olmazsa eski hâline döndür. */
    fun saveSmsRules(rules: com.saban.aw1kpanel.data.SmsRules) {
        viewModelScope.launch {
            val before = _state.value.smsFilter
            _state.update { st -> st.copy(smsFilter = (st.smsFilter ?: com.saban.aw1kpanel.data.SmsFilterState(rules, emptyList(), true)).copy(rules = rules)) }
            try {
                repo.smsSaveRules(rules)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(smsFilter = before, message = "Kural kaydedilemedi: " + friendly(e)) }
            }
        }
    }

    fun runSmsFilter(apply: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(smsBusy = true) }
            try {
                val r = repo.smsFilterRun(apply)
                val msg = when {
                    !r.ok -> "Filtre hatası: " + (r.error ?: "bilinmiyor")
                    r.skipped != null -> "Filtre kapalı; önce açmalısın"
                    apply -> "${r.deleted} mesaj silindi" + if (r.blocked.isNotEmpty()) ", ${r.blocked.size} gönderen engellendi" else ""
                    else -> if (r.matched.isEmpty()) "Silinecek mesaj yok" else "${r.matched.size} mesaj silinecek (deneme, silinmedi)"
                }
                _state.update { it.copy(smsRun = if (apply) null else r, message = msg) }
                if (apply) {
                    loadSms()
                    loadSmsFilter()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = "Filtre çalışmadı: " + friendly(e)) }
            } finally {
                _state.update { it.copy(smsBusy = false) }
            }
        }
    }

    fun dismissSmsRun() {
        _state.update { it.copy(smsRun = null) }
    }

    /** Göndericiyi engelli listeye ekler ve bu mesajı siler. */
    fun blockSmsSender(sender: String, indexes: List<Int>) {
        val cur = _state.value.smsFilter?.rules ?: com.saban.aw1kpanel.data.SmsRules()
        val s = com.saban.aw1kpanel.data.SmsMatcher.fold(sender)
        if (cur.blocked.none { com.saban.aw1kpanel.data.SmsMatcher.fold(it) == s }) {
            saveSmsRules(cur.copy(blocked = cur.blocked + sender))
        }
        deleteSms(indexes)
    }

    fun sendSms(number: String, text: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            _state.update { it.copy(smsBusy = true) }
            try {
                repo.smsSend(number, text)
                _state.update { it.copy(message = "SMS gönderildi", smsBusy = false) }
                onDone()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = "SMS gönderilemedi: " + friendly(e), smsBusy = false) }
            }
        }
    }

    fun deleteSms(indexes: List<Int>) {
        viewModelScope.launch {
            _state.update { it.copy(smsBusy = true) }
            try {
                repo.smsDelete(indexes)
                loadSms()
                // Modem "silindi" dese de mesaj SIM'de duruyorsa gerçeği söyle
                val still = _state.value.sms.orEmpty().any { m -> m.indexes.any { it in indexes } }
                _state.update {
                    it.copy(message = if (still) "Modem silindi dedi ama mesaj SIM'de duruyor (silme çalışmadı)" else "SMS silindi")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = "Silinemedi: " + friendly(e)) }
            } finally {
                _state.update { it.copy(smsBusy = false) }
            }
        }
    }

    /** Günlük sayfası: modemden KediRay / Xray günlüğünü çeker. */
    suspend fun loadLogs() {
        try {
            val d = repo.kediLogs()
            _state.update { it.copy(logs = d, logsError = null) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(logsError = friendly(e)) }
        }
    }

    fun clearLogs(errorsToo: Boolean) {
        viewModelScope.launch {
            try {
                repo.kediLogsClear("clear-stream")
                if (errorsToo) repo.kediLogsClear("clear-errors")
                _state.update { it.copy(logs = null) }
                loadLogs()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(logsError = friendly(e)) }
            }
        }
    }

    /** "Bağlanmayı dene" düğmesi: kayıtlı modeme yeniden giriş dener. */
    fun retryConnect() {
        if (_state.value.retrying) return
        viewModelScope.launch {
            _state.update { it.copy(retrying = true, loginError = null) }
            val saved = store.load()
            if (saved == null) {
                _state.update { it.copy(retrying = false, offline = false) }
                return@launch
            }
            try {
                repo.login(saved)
                _state.update { it.copy(loggedIn = true, offline = false, retrying = false, loginError = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(retrying = false, loginError = friendly(e), offline = e is IOException) }
            }
        }
    }

    /** Bağlı değil ekranından giriş / modem seçme formuna geç. */
    fun showLoginForm() {
        _state.update { it.copy(offline = false) }
    }

    // ---- Giriş / modem yönetimi ---------------------------------------------

    private fun cleanHost(h: String): String =
        h.trim().removePrefix("http://").removePrefix("https://").trimEnd('/')

    private fun resetSession(): UiState =
        UiState(
            checkingSaved = false,
            routers = store.routers(),
            activeRouterId = store.activeId(),
        )

    private fun refreshRouters() {
        _state.update { it.copy(routers = store.routers(), activeRouterId = store.activeId()) }
    }

    fun login(host: String, user: String, pass: String) {
        viewModelScope.launch {
            _state.update { it.copy(loginBusy = true, loginError = null) }
            try {
                val h = cleanHost(host)
                val c = Connection(h, user.trim(), pass)
                repo.login(c)
                val existing = store.routers().firstOrNull { it.host == h && it.user == c.user }
                val router = Router(
                    id = existing?.id ?: store.newId(),
                    name = existing?.name ?: if (store.routers().isEmpty()) "Modem" else h,
                    host = h,
                    user = c.user,
                    password = pass,
                )
                store.upsert(router)
                store.setActive(router.id)
                store.setAutoLogin(true)
                _state.value = resetSession().copy(loggedIn = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loginBusy = false, loginError = friendly(e)) }
            }
        }
    }

    /** Kayıtlı bir modeme geç (giriş ekranından ya da Ayarlar'dan). */
    fun connectRouter(id: String) {
        val r = store.routers().firstOrNull { it.id == id } ?: return
        viewModelScope.launch {
            _state.update { it.copy(loginBusy = true, loginError = null) }
            try {
                repo.login(Connection(r.host, r.user, r.password))
                store.setActive(r.id)
                store.setAutoLogin(true)
                _state.value = resetSession().copy(loggedIn = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(loginBusy = false, loginError = "${r.name}: ${friendly(e)}", message = "${r.name}: ${friendly(e)}")
                }
            }
        }
    }

    fun saveRouter(id: String?, name: String, host: String, user: String, pass: String) {
        val old = id?.let { i -> store.routers().firstOrNull { it.id == i } }
        val h = cleanHost(host)
        if (h.isEmpty()) {
            showMessage("Adres boş olamaz")
            return
        }
        val r = Router(
            id = old?.id ?: store.newId(),
            name = name.trim().ifEmpty { h },
            host = h,
            user = user.trim().ifEmpty { "root" },
            password = if (pass.isEmpty()) old?.password.orEmpty() else pass,
        )
        store.upsert(r)
        refreshRouters()
        showMessage(if (old == null) "Modem eklendi" else "Modem güncellendi")
    }

    fun deleteRouter(id: String) {
        if (id == store.activeId() && _state.value.loggedIn) {
            showMessage("Bağlı olduğun modemi silemezsin; önce başka modeme geç ya da çıkış yap")
            return
        }
        store.delete(id)
        if (store.activeId() == id) store.setActive(null)
        refreshRouters()
        showMessage("Modem silindi")
    }

    fun logout() {
        store.setAutoLogin(false)
        repo.logout()
        _state.value = resetSession()
    }

    // ---- Yenileme ------------------------------------------------------------

    /** Her 3 saniyede çağrılır: sinyal, hız, bağlantı durumu. */
    suspend fun refreshModem() {
        try {
            val m = repo.modemState()
            _state.update { it.copy(modem = m, modemError = null) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(modemError = friendly(e)) }
        }
    }

    /** CPU, bellek, çalışma süresi (ana sayfa). */
    suspend fun refreshSystem() {
        attempt { repo.systemState() }?.let { s -> _state.update { it.copy(system = s) } }
    }

    /** Şifre ya da ana anahtar doğruysa Band kilidi sayfasını açar. */
    fun unlockBand(pw: String): Boolean {
        val ok = settingsStore.checkBandPassword(pw.trim())
        if (ok) _state.update { it.copy(bandUnlocked = true) }
        return ok
    }

    /** "Şifremi unuttum": kayıtlı şifreyi modemdeki dosyaya yazar; SSH ile okunur. */
    fun forgotBandPassword() {
        viewModelScope.launch {
            val pw = store.bandPasswordPlain
            if (pw == null) {
                _state.update { it.copy(message = "Kayıtlı şifre yok (eski sürümde belirlenmiş). Ana anahtarla gir, sonra yeni şifre belirle.") }
                return@launch
            }
            try {
                repo.writeBandPassword(pw)
                _state.update { it.copy(message = "Şifre modeme yazıldı: SSH ile  cat /root/band-kilidi-sifre.txt") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = friendly(e)) }
            }
        }
    }

    fun relockBand() {
        _state.update { it.copy(bandUnlocked = false) }
    }

    /** current: mevcut şifre ya da ana anahtar (şifre tanımlıysa zorunlu). */
    fun setBandPassword(current: String, new: String): String? {
        if (_settings.value.bandLockProtected && !settingsStore.checkBandPassword(current.trim())) return "Mevcut şifre yanlış"
        if (new.trim().length < 4) return "Şifre en az 4 karakter olmalı"
        settingsStore.setBandPassword(new.trim())
        store.bandPasswordPlain = new.trim()
        _settings.update { it.copy(bandLockProtected = true) }
        return null
    }

    fun removeBandPassword(current: String): String? {
        if (!settingsStore.checkBandPassword(current.trim())) return "Şifre yanlış"
        settingsStore.clearBandPassword()
        store.bandPasswordPlain = null
        _settings.update { it.copy(bandLockProtected = false) }
        return null
    }

    suspend fun refreshLockband() {
        try {
            val g = repo.lockBands()
            _state.update { it.copy(lockband = g, lockbandError = null) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(lockbandError = friendly(e)) }
        }
    }

    fun setLockBand(bandClass: String, ids: List<String>) {
        viewModelScope.launch {
            try {
                repo.setLockBand(bandClass, ids)
                _state.update { it.copy(message = "Band kilidi gönderildi; modem yeniden bağlanabilir") }
                delay(5000)
                refreshLockband()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = friendly(e)) }
            }
        }
    }

    /** Akıllı band seçimi: seçili bantları tek tek kilitleyip ölçer; sonunda en iyiyi uygular ya da eski kilidi geri yükler. */
    fun startBandScan(bandClass: String, ids: List<String>, waitSec: Int, useSpeed: Boolean, autoApply: Boolean) {
        if (scanJob?.isActive == true) return
        val group = _state.value.lockband?.get(bandClass) ?: return
        val bands = group.available.filter { it.first in ids }
        if (bands.isEmpty()) return
        val allIds = group.available.map { it.first }
        scanJob = viewModelScope.launch {
            try {
                scanner.run(
                    cls = bandClass, bands = bands, original = group.locked, allIds = allIds,
                    waitSec = waitSec, useSpeed = useSpeed, autoApply = autoApply,
                ) { s -> _state.update { it.copy(bandScan = s) } }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(message = friendly(e), bandScan = it.bandScan.copy(running = false, finished = true))
                }
            } finally {
                withContext(NonCancellable) {
                    delay(3000)
                    refreshLockband()
                }
            }
        }
    }

    fun cancelBandScan() {
        scanJob?.cancel()
    }

    fun clearBandScan() {
        if (scanJob?.isActive != true) _state.update { it.copy(bandScan = BandScanState()) }
    }

    /** Modemdata sayfası; AT komutlarıyla uğraştığı için seyrek çağrılır. */
    suspend fun refreshModemdata() {
        try {
            val g = repo.modemdata()
            _state.update { it.copy(modemdata = g, modemdataError = null) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(modemdataError = friendly(e)) }
        }
    }

    // ---- Uygulama güncellemesi (GitHub Releases) ----

    /** En son yayını denetler. [manual] false ise (açılışta) hata ve "güncelsin" mesajı gösterilmez. */
    fun checkAppUpdate(manual: Boolean = true) {
        val cur = _state.value.appUpdate
        if (cur.checking || cur.downloading) return
        viewModelScope.launch {
            _state.update { it.copy(appUpdate = it.appUpdate.copy(checking = true, message = if (manual) "Denetleniyor…" else "")) }
            try {
                val r = AppUpdater.latest()
                val newer = r != null && AppUpdater.hasUpdate(getApplication<Application>(), r, APP_VERSION)
                _state.update {
                    it.copy(
                        appUpdate = it.appUpdate.copy(
                            checking = false,
                            checked = true,
                            release = r,
                            newer = newer,
                            message = when {
                                r == null -> if (manual) "Henüz yayınlanmış sürüm yok" else ""
                                newer -> if (AppUpdater.isNewer(r.version, APP_VERSION)) "Yeni sürüm var: v${r.version.removePrefix("v")}" else "Aynı sürüm numarasıyla yeni derleme var"
                                else -> if (manual) "Güncelsin (v$APP_VERSION)" else ""
                            },
                        ),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(appUpdate = it.appUpdate.copy(checking = false, message = if (manual) "Denetlenemedi: ${e.message ?: "bağlantı hatası"}" else ""))
                }
            }
        }
    }

    /** APK'yı indirir; bitince kurulum ekranını açar. */
    fun downloadAppUpdate() {
        val rel = _state.value.appUpdate.release ?: return
        if (_state.value.appUpdate.downloading) return
        viewModelScope.launch {
            _state.update { it.copy(appUpdate = it.appUpdate.copy(downloading = true, progress = 0f, ready = null, message = "İndiriliyor…")) }
            try {
                val f = AppUpdater.download(getApplication<Application>(), rel) { p ->
                    _state.update { it.copy(appUpdate = it.appUpdate.copy(progress = p)) }
                }
                _state.update { it.copy(appUpdate = it.appUpdate.copy(downloading = false, progress = 1f, ready = f.absolutePath, message = "İndirildi")) }
                installAppUpdate()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(appUpdate = it.appUpdate.copy(downloading = false, message = "İndirilemedi: ${e.message ?: "bağlantı hatası"}")) }
            }
        }
    }

    /** İndirilmiş APK için Android'in kurulum ekranını açar; izin yoksa önce izin ekranını açar. */
    fun installAppUpdate() {
        val path = _state.value.appUpdate.ready ?: return
        val app = getApplication<Application>()
        try {
            if (!AppUpdater.canInstall(app)) {
                AppUpdater.openInstallSettings(app)
                _state.update { it.copy(appUpdate = it.appUpdate.copy(message = "Bu uygulamaya \"bilinmeyen uygulamaları yükle\" izni ver, sonra Kurulumu aç'a dokun")) }
            } else {
                AppUpdater.install(app, File(path))
                _state.update { it.copy(appUpdate = it.appUpdate.copy(message = "Kurulum ekranı açıldı")) }
            }
        } catch (e: Exception) {
            _state.update { it.copy(appUpdate = it.appUpdate.copy(message = "Kurulum açılamadı: ${e.message ?: "bilinmeyen hata"}")) }
        }
    }

    private val rateSamples = mutableMapOf<String, Triple<Long, Long, Long>>()
    private val ipSamples = mutableMapOf<String, Triple<Long, Long, Long>>()

    /** Cihazlar sayfası açıkken birkaç saniyede bir çağrılır: istemci sayaçlarının farkından anlık hızı hesaplar. */
    suspend fun refreshWifiClients() {
        val c = attempt { repo.wifiClients(_state.value.wifi) } ?: return
        val now = System.currentTimeMillis()
        val rates = mutableMapOf<String, Pair<Float, Float>>()
        c.forEach { cl ->
            val key = cl.mac.uppercase()
            val prev = rateSamples[key]
            if (prev != null) {
                val dt = (now - prev.third) / 1000f
                if (dt >= 0.5f) {
                    val down = (cl.txBytes - prev.first).coerceAtLeast(0L) / dt
                    val up = (cl.rxBytes - prev.second).coerceAtLeast(0L) / dt
                    rates[key] = down to up
                }
            }
            rateSamples[key] = Triple(cl.txBytes, cl.rxBytes, now)
        }
        rateSamples.keys.retainAll(c.map { it.mac.uppercase() }.toSet())
        _state.update { it.copy(wifiClients = c, deviceRates = rates) }

        // KediRay'in cihaz başına sayaçları kablolu cihazları da kapsar: bugünkü toplamların farkından hız.
        val k = attempt { repo.kediStats() } ?: return
        val t = System.currentTimeMillis()
        val ipr = mutableMapOf<String, Pair<Float, Float>>()
        k.shares.devices.forEach { dv ->
            if (dv.ip.isEmpty()) return@forEach
            val prev = ipSamples[dv.ip]
            if (prev != null) {
                val dt = (t - prev.third) / 1000f
                if (dt >= 1f) ipr[dv.ip] = (dv.rx - prev.first).coerceAtLeast(0L) / dt to (dv.tx - prev.second).coerceAtLeast(0L) / dt
            }
            ipSamples[dv.ip] = Triple(dv.rx, dv.tx, t)
        }
        ipSamples.keys.retainAll(k.shares.devices.map { it.ip }.toSet())
        _state.update { it.copy(shares = k.shares, trafficDays = k.days, ipRates = ipr) }
    }

    /** Her 10 saniyede çağrılır: Wi-Fi, cihazlar, VPN. Hatalar sessizce geçilir. */
    @Volatile private var whatsNewChecked = false

    suspend fun refreshSlow() {
        if (!whatsNewChecked) {
            // Uygulama açılınca (ve her güncellemeden sonra) bir kez: kurulum bittiyse sürüm + değişiklikler gösterilir
            try {
                val wn = repo.kediWhatsNew()
                _state.update { it.copy(whatsNew = wn) }
                whatsNewChecked = true
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
        }
        attempt { repo.wifiNetworks() }?.let { w -> _state.update { it.copy(wifi = w) } }
        attempt { repo.guestWifi() }?.let { g -> _state.update { it.copy(guest = g) } }
        attempt { repo.wifiClients(_state.value.wifi) }?.let { c -> _state.update { it.copy(wifiClients = c) } }
        attempt { repo.devices() }?.let { d -> _state.update { it.copy(devices = d) } }
        attempt { repo.kediStats() }?.let { k -> _state.update { it.copy(shares = k.shares, trafficDays = k.days) } }
        attempt { repo.blockedMacs() }?.let { b -> _state.update { it.copy(blocked = b) } }
        attempt { repo.vpnProfiles() }?.let { (active, list) ->
            _state.update { it.copy(profiles = list, activeProfileId = active) }
        }
        val usage = attempt { repo.usage() }
        _state.update { it.copy(usage = usage) }
        attempt { repo.netDetails() }?.let { n -> _state.update { it.copy(netDetails = n) } }
        attempt { repo.sysDetails() }?.let { d -> _state.update { it.copy(sysDetails = d) } }
        refreshVpn()
    }

    /** VPN durumu ve canlı hızı: ana sayfa ve VPN sekmesi için 2 sn'de bir çağrılır. */
    suspend fun refreshVpn() {
        val vpn = attempt { repo.vpnState() }
        val kedi = repo.kedi
        if (vpn != null && vpn.connected) {
            store.saveBackend(if (vpn.provider == "KediRay") "kediray" else "xwrt")
            if (vpn.provider != "KediRay" && vpn.profileId.isNotEmpty()) store.saveVpnProfile(vpn.profileId)
        }
        val last = store.lastVpnProfile()
        val kediBackend = kedi != null && (vpn?.provider == "KediRay" || store.lastBackend() == "kediray")
        _state.update {
            it.copy(
                vpn = vpn,
                kediray = kedi,
                vpnProfileId = if (kediBackend) "kediray" else it.activeProfileId ?: last,
            )
        }
    }

    // ---- Eylemler ------------------------------------------------------------

    fun setVpn(on: Boolean) {
        viewModelScope.launch {
            try {
                // Uygulamadan yapılan açma/kapama, kopma uyarısını tetiklememeli.
                settingsStore.vpnManualOff = !on
                val kediNow = _state.value.vpn?.provider == "KediRay"
                if (kediNow || (on && store.lastBackend() == "kediray" && _state.value.kediray != null)) {
                    repo.kedirayControl(if (on) "start" else "stop")
                } else if (on) {
                    val id = _state.value.activeProfileId ?: store.lastVpnProfile()
                        ?: throw UbusException("VPN profili bilinmiyor; VPN sekmesinden bir profile bağlan")
                    repo.vpnConnect(id)
                } else {
                    repo.vpnDisconnect()
                }
                _state.update { it.copy(message = if (on) "VPN bağlanıyor…" else "VPN kapatıldı") }
                delay(2500)
                refreshSlow()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = friendly(e)) }
            }
        }
    }

    /** Sertifikayı şimdi sabitler; değiştiyse modem tüneli yeniden başlatır (internet gittiyse işe yarar). */
    fun kediPin() {
        viewModelScope.launch {
            _state.update { it.copy(message = "Sertifika sabitleniyor…") }
            try {
                val msg = repo.kediPin()
                _state.update { it.copy(message = msg) }
                delay(4000)
                refreshSlow()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = friendly(e)) }
            }
        }
    }

    fun kedirayAction(action: String) {
        viewModelScope.launch {
            try {
                settingsStore.vpnManualOff = action == "stop"
                repo.kedirayControl(action)
                _state.update {
                    it.copy(message = when (action) {
                        "start" -> "KediRay başlatılıyor…"
                        "stop" -> "KediRay durduruluyor…"
                        else -> "KediRay yeniden başlatılıyor…"
                    })
                }
                delay(4000)
                refreshSlow()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = friendly(e)) }
            }
        }
    }

    /** Bu telefonun yerel IPv4 adresleri (Wi-Fi'de hangi ağda olduğunu bulmak için). */
    private fun ownIps(): Set<String> = try {
        java.util.Collections.list(java.net.NetworkInterface.getNetworkInterfaces())
            .flatMap { ni -> java.util.Collections.list(ni.inetAddresses) }
            .filter { a -> !a.isLoopbackAddress && a is java.net.Inet4Address }
            .mapNotNull { a -> a.hostAddress }
            .toSet()
    } catch (e: Exception) {
        emptySet()
    }

    /** Kapatılırsa modemle bağlantıyı koparacak ağ için neden metni; sorun yoksa null. */
    private fun wifiOffBlocker(w: WifiNetwork): String? {
        val s = _state.value
        val mine = ownIps()
        val myMac = s.devices.firstOrNull { it.ip in mine }?.mac
        val mySection = myMac?.let { m -> s.wifiClients.firstOrNull { it.mac.equals(m, true) }?.section }
        if (mySection != null && mySection == w.section) {
            return "Şu an \"${w.ssid}\" ağına bağlısın; kapatırsan modemle bağlantın kopar. Başka bir ağa bağlanıp öyle kapat."
        }
        val openCount = s.wifi.count { it.enabled && it.section != "guest_wifi" }
        if (w.section != "guest_wifi" && w.enabled && openCount <= 1) {
            return "Son açık Wi-Fi ağı kapatılamaz; kapatırsan modeme Wi-Fi ile bağlanamazsın."
        }
        return null
    }

    /** Kapatma engelliyse nedenini gösterir ve true döner. */
    fun blockWifiOff(w: WifiNetwork): Boolean {
        val why = wifiOffBlocker(w) ?: return false
        _state.update { it.copy(message = why) }
        return true
    }

    fun setWifi(w: WifiNetwork, enabled: Boolean) {
        if (!enabled && blockWifiOff(w)) return
        viewModelScope.launch {
            _state.update { s ->
                s.copy(wifi = s.wifi.map { if (it.section == w.section) it.copy(enabled = enabled) else it })
            }
            try {
                repo.setWifiEnabled(w.section, enabled)
                _state.update {
                    it.copy(message = if (enabled) "${w.ssid} açıldı" else "${w.ssid} kapatıldı")
                }
                delay(4000)
                refreshSlow()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = friendly(e)) }
                refreshSlow()
            }
        }
    }

    fun openWifiEditor(section: String) {
        viewModelScope.launch {
            try {
                val d = repo.wifiDetail(section)
                _state.update { it.copy(wifiEditor = d) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = friendly(e)) }
            }
        }
    }

    fun closeWifiEditor() {
        _state.update { it.copy(wifiEditor = null) }
    }

    fun saveWifi(d: WifiDetail) {
        viewModelScope.launch {
            try {
                repo.saveWifi(d)
                _state.update { it.copy(wifiEditor = null, message = "Wi-Fi ayarları uygulandı; Wi-Fi birkaç saniye yeniden başlar") }
                delay(6000)
                refreshSlow()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = friendly(e)) }
            }
        }
    }

    /** Linki uygulamada çözüp düzenleyicide açar; tanınmazsa modemin kendi içe aktarmasını dener. */
    fun addKediLink(link: String) {
        viewModelScope.launch {
            try {
                val msg = repo.kediAddLink(link)
                _state.update { it.copy(message = msg) }
                delay(1500)
                refreshSlow()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = friendly(e)) }
            }
        }
    }

    fun importLink(link: String) {
        if (_state.value.vpnProfileId == "kediray") {
            addKediLink(link)
            return
        }
        val parsed = LinkParser.parse(link)
        if (parsed != null) {
            _state.update { it.copy(editor = ProfileEditorState(null, parsed.toString(2))) }
        } else {
            importProfile(link)
        }
    }

    /** Yeniden bağlan/başlat sonrası, bağlantı geri gelene (veya süre dolana) kadar durum yazısını tutar. */
    private fun watchLink(minMs: Long, maxMs: Long) {
        viewModelScope.launch {
            val t0 = android.os.SystemClock.elapsedRealtime()
            var sawDown = false
            while (true) {
                delay(1000)
                val el = android.os.SystemClock.elapsedRealtime() - t0
                val st = _state.value
                val up = st.modem.connected && st.modemError == null
                if (!up) sawDown = true
                if (el >= maxMs || (el >= minMs && up && sawDown) || (el >= maxMs / 3 && up && maxMs <= 60_000)) break
            }
            _state.update { it.copy(linkAction = null) }
        }
    }

    fun redial() {
        viewModelScope.launch {
            _state.update { it.copy(linkAction = "Yeniden bağlanıyor…") }
            watchLink(minMs = 6_000, maxMs = 45_000)
            try {
                repo.redial()
                _state.update { it.copy(message = "Yeniden bağlanma komutu gönderildi") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = friendly(e), linkAction = null) }
            }
        }
    }

    fun rebootRouter() {
        viewModelScope.launch {
            _state.update { it.copy(linkAction = "Modem yeniden başlatılıyor…") }
            watchLink(minMs = 15_000, maxMs = 300_000)
            try {
                repo.reboot()
                _state.update {
                    it.copy(message = "Modem yeniden başlatılıyor; birkaç dakika sonra tekrar bağlanır")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = friendly(e), linkAction = null) }
            }
        }
    }

    // ---- VPN profilleri ------------------------------------------------------

    fun connectProfile(id: String) = act("Profil bağlanıyor…") {
        settingsStore.vpnManualOff = false
        repo.vpnConnect(id)
    }

    fun deleteProfile(id: String) = act("Profil silindi") { repo.vpnDeleteProfile(id) }

    fun importProfile(uri: String) = act("Profil eklendi") { repo.vpnImport(uri.trim()) }

    fun pingProfile(id: String) {
        viewModelScope.launch {
            _state.update { it.copy(pinging = it.pinging + id) }
            val r = try {
                PingResult(repo.vpnPing(id))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                PingResult(null)
            }
            _state.update { it.copy(pings = it.pings + (id to r), pinging = it.pinging - id) }
        }
    }

    fun openProfileEditor(id: String) {
        viewModelScope.launch {
            try {
                val raw = repo.vpnProfileRaw(id)
                _state.update { it.copy(editor = ProfileEditorState(id, raw.toString(2))) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = friendly(e)) }
            }
        }
    }

    fun openNewProfile() {
        val template = JSONObject()
            .put("name", "Yeni profil")
            .put("proto", "vless")
            .put("address", "")
            .put("port", 443)
            .put("uuid", "")
            .put("encryption", "none")
            .put("network", "tcp")
            .put("security", "tls")
            .put("sni", "")
            .put("alpn", "")
            .put("fingerprint", "chrome")
            .put("path", "")
            .put("header_type", "none")
        _state.update { it.copy(editor = ProfileEditorState(null, template.toString(2))) }
    }

    fun closeEditor() {
        _state.update { it.copy(editor = null) }
    }

    /** Düzenleyiciden gelen JSON'u modeme yazar; başarılıysa düzenleyiciyi kapatır. */
    fun saveEditor(json: String) {
        val editing = _state.value.editor ?: return
        viewModelScope.launch {
            try {
                val obj = JSONObject(json)
                if (editing.kind == "kediray") {
                    if (editing.id != null) repo.kediUpdateNode(editing.id, obj) else repo.kediAddNode(obj)
                } else if (editing.id != null) {
                    obj.put("id", editing.id)
                    repo.vpnUpdateProfile(editing.id, obj)
                } else {
                    repo.vpnAddProfile(obj)
                }
                _state.update {
                    it.copy(
                        editor = null,
                        message = when {
                            editing.kind == "kediray" -> "Düğüm kaydedildi"
                            editing.id != null -> "Profil kaydedildi"
                            else -> "Profil eklendi"
                        },
                    )
                }
                delay(1500)
                refreshSlow()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = friendly(e)) }
            }
        }
    }

    // ---- KediRay düğümleri -----------------------------------------------

    fun openKediNode(section: String) {
        viewModelScope.launch {
            try {
                val raw = repo.kediNodeRaw(section)
                _state.update { it.copy(editor = ProfileEditorState(section, raw.toString(2), "kediray")) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = friendly(e)) }
            }
        }
    }

    fun openNewKediNode() {
        val t = JSONObject()
            .put("remarks", "Yeni düğüm")
            .put("protocol", "vless")
            .put("address", "")
            .put("port", "443")
            .put("uuid", "")
            .put("network", "ws")
            .put("path", "/")
            .put("tls", "tls")
            .put("sni", "")
            .put("fp", "chrome")
            .put("insecure", "0")
            .put("enabled", "1")
        _state.update { it.copy(editor = ProfileEditorState(null, t.toString(2), "kediray")) }
    }

    fun useKediNode(section: String, name: String) {
        viewModelScope.launch {
            try {
                repo.kediUseNode(section)
                _state.update { it.copy(message = "\"$name\" sunucusuna geçildi") }
                delay(2500)
                refreshSlow()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = friendly(e)) }
            }
        }
    }

    // ---- KediRay ayarları ---------------------------------------------------

    fun openKediSettings() {
        _state.update { it.copy(kediSettingsOpen = true, kediSettings = null, kediSettingsError = null) }
        viewModelScope.launch {
            try {
                val d = repo.kediSettings()
                _state.update { it.copy(kediSettings = d) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(kediSettingsError = friendly(e)) }
            }
        }
    }

    fun closeKediSettings() {
        _state.update { it.copy(kediSettingsOpen = false, kediSettings = null, kediSettingsError = null) }
    }

    /** Değişen ayarları modeme yazar ve uygular; başarılıysa ekranı kapatır. */
    fun saveKediSettings(changes: Map<String, Any>) {
        if (_state.value.kediSettingsSaving) return
        viewModelScope.launch {
            _state.update { it.copy(kediSettingsSaving = true) }
            try {
                repo.kediSaveSettings(changes)
                (changes["enabled"] as? String)?.let { settingsStore.vpnManualOff = it == "0" }
                _state.update {
                    it.copy(
                        kediSettingsOpen = false,
                        kediSettings = null,
                        message = "Ayarlar kaydedildi; KediRay yeniden başlıyor…",
                    )
                }
                delay(5000)
                refreshSlow()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = friendly(e)) }
            } finally {
                _state.update { it.copy(kediSettingsSaving = false) }
            }
        }
    }

    fun kediBandUnlock() {
        viewModelScope.launch {
            try {
                val msg = repo.kediBandUnlock()
                _state.update { it.copy(message = msg) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = friendly(e)) }
            }
        }
    }

    suspend fun kediDnsTestStart() = repo.kediDnsTestStart()

    suspend fun kediDnsTestRead(): Pair<Boolean, String> = repo.kediDnsTestRead()

    /** KediRay düğümlerini ölçer: [section] null ise hepsini ("Tümünü test et"). */
    fun pingKedi(section: String?) {
        val key = section ?: "all"
        if (key in _state.value.kediPinging || "all" in _state.value.kediPinging) return
        viewModelScope.launch {
            _state.update { it.copy(kediPinging = it.kediPinging + key) }
            try {
                val res = repo.kediPing(section)
                _state.update { it.copy(kediPings = it.kediPings + res) }
                if (section != null && section !in res) {
                    _state.update { it.copy(kediPings = it.kediPings + (section to com.saban.aw1kpanel.data.KediPing(null, null))) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    val failed = if (section != null) it.kediPings + (section to com.saban.aw1kpanel.data.KediPing(null, null)) else it.kediPings
                    it.copy(kediPings = failed, message = friendly(e))
                }
            } finally {
                _state.update { it.copy(kediPinging = it.kediPinging - key) }
            }
        }
    }

    fun setKediNodeEnabled(section: String, on: Boolean) =
        act(if (on) "Düğüm açıldı; yeniden başlatınca geçerli olur" else "Düğüm kapatıldı; yeniden başlatınca geçerli olur") {
            repo.kediSetNodeEnabled(section, on)
        }

    fun moveKediNode(section: String, up: Boolean, steps: Int = 1) = act(if (up) "Yukarı taşındı" else "Aşağı taşındı") {
        repo.kediMoveNode(section, up, steps)
    }

    fun deleteKediNode(section: String) = act("Düğüm silindi") { repo.kediDeleteNode(section) }

    fun blockDevice(d: Device, block: Boolean) {
        viewModelScope.launch {
            try {
                repo.setBlocked(d.mac, block)
                _state.update {
                    val mac = d.mac.uppercase()
                    it.copy(
                        blocked = if (block) it.blocked + mac else it.blocked - mac,
                        message = if (block) "${d.hostname.ifEmpty { "Cihaz" }} engellendi; Wi-Fi yeniden başlıyor" else "Engel kaldırıldı; Wi-Fi yeniden başlıyor",
                    )
                }
                delay(6000)
                refreshSlow()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = friendly(e)) }
            }
        }
    }

    fun saveGuest(enabled: Boolean, ssid: String, key: String) {
        viewModelScope.launch {
            try {
                repo.setGuestWifi(enabled, ssid.trim(), key)
                _state.update {
                    it.copy(
                        guest = com.saban.aw1kpanel.data.GuestWifi(true, enabled, ssid.trim(), key),
                        message = "Misafir Wi-Fi uygulandı; ağ birkaç saniye yeniden başlar",
                    )
                }
                delay(8000)
                refreshSlow()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = friendly(e)) }
            }
        }
    }

    fun blockMac(mac: String, block: Boolean) {
        blockDevice(Device(hostname = _state.value.devices.firstOrNull { it.mac.equals(mac, true) }?.hostname.orEmpty(), ip = "", mac = mac), block)
    }

    fun setDeviceMode(d: Device, mode: String) {
        viewModelScope.launch {
            try {
                repo.setDeviceMode(d.mac, mode, d.hostname.takeIf { it != "Bilinmeyen cihaz" }.orEmpty())
                _state.update { st ->
                    st.copy(
                        devices = st.devices.map { x -> if (x.mac == d.mac) x.copy(mode = mode) else x },
                        message = "Yön güncellendi",
                    )
                }
                delay(2500)
                refreshSlow()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = friendly(e)) }
            }
        }
    }

    fun setHideVpnData(on: Boolean) {
        settingsStore.setHideVpnData(on)
        _settings.update { it.copy(hideVpnData = on) }
    }

    fun setHideDeviceData(on: Boolean) {
        settingsStore.setHideDeviceData(on)
        _settings.update { it.copy(hideDeviceData = on) }
    }

    fun setShowXray(on: Boolean) {
        settingsStore.setShowXray(on)
        _settings.update { it.copy(showXray = on) }
    }

    fun setShowKedi(on: Boolean) {
        settingsStore.setShowKedi(on)
        _settings.update { it.copy(showKedi = on) }
    }


    // ---- KediRay güncelleme ---------------------------------------------------

    fun startKediUpdate(uri: android.net.Uri, name: String) {
        if (_state.value.update.busy) return
        viewModelScope.launch {
            _state.update { it.copy(update = UpdateState(busy = true, fileName = name, label = "Dosya okunuyor…")) }
            try {
                val bytes = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw IOException("Dosya açılamadı")
                }
                _state.update { it.copy(update = it.update.copy(label = "Modeme yükleniyor…")) }
                repo.kediUploadAndStart(bytes)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = if (e is IllegalArgumentException) (e.message ?: "Geçersiz dosya") else friendly(e)
                _state.update { it.copy(update = UpdateState(finished = true, ok = false, fileName = name, label = msg), message = msg) }
                return@launch
            }
            watchKediUpdate()
            delay(3000)
            try { refreshSlow() } catch (e: CancellationException) { throw e } catch (_: Exception) {}
        }
    }

    /** GitHub'dan güncelleme var mı diye modemden sorar. */
    fun checkKediUpdate() {
        if (_state.value.update.busy || _state.value.update.checking) return
        viewModelScope.launch {
            _state.update { it.copy(update = it.update.copy(checking = true, checkMsg = "Denetleniyor…", newer = false)) }
            try {
                val c = repo.kediUpdateCheck()
                val msg = when {
                    !c.ok -> "Denetlenemedi: " + c.error.ifEmpty { "GitHub'a ulaşılamadı" }
                    c.newer -> "Yeni sürüm var: ${c.current} → ${c.latest}"
                    else -> "Güncelsin (${c.current})"
                }
                _state.update { it.copy(update = it.update.copy(checking = false, checkMsg = msg, newer = c.ok && c.newer)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(update = it.update.copy(checking = false, checkMsg = friendly(e), newer = false)) }
            }
        }
    }

    /** GitHub'daki son sürümü modem indirip kurar; ilerleme dosyayla güncellemedeki gibi izlenir. */
    fun startKediGithubUpdate() {
        if (_state.value.update.busy) return
        viewModelScope.launch {
            _state.update { it.copy(update = UpdateState(busy = true, fileName = "GitHub", label = "Modem indiriyor ve doğruluyor…")) }
            try {
                repo.kediUpdateInstall()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = friendly(e)
                _state.update { it.copy(update = UpdateState(finished = true, ok = false, fileName = "GitHub", label = msg), message = msg) }
                return@launch
            }
            watchKediUpdate()
            delay(3000)
            try { refreshSlow() } catch (e: CancellationException) { throw e } catch (_: Exception) {}
        }
    }

    /** Kurulum günlüğünü kurulum bitene kadar izler (dosyayla ve GitHub güncellemesi için ortak). */
    private suspend fun watchKediUpdate() {
        val total = 7
        var fails = 0
        var polls = 0
        var steps = 0
        while (true) {
            delay(2000)
            polls++
            try {
                val l = repo.kediUpdateLog()
                fails = 0
                steps = l.steps
                _state.update { it.copy(update = it.update.copy(steps = l.steps, label = l.label.ifEmpty { "Çalışıyor…" }, log = l.log)) }
                if (l.status.startsWith("DONE")) {
                    if (l.status == "DONE 0") { finishUpdate(true, "Güncelleme tamamlandı ✔"); return }
                    if (l.steps > 0 || polls >= 6) { finishUpdate(false, "Kurulum hata ile bitti (${l.status}). Günlüğe bak."); return }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Kurulumun sonunda rpcd yeniden başlar; bu sırada yanıt gelmeyebilir.
                fails++
                _state.update { it.copy(update = it.update.copy(label = "Panel yanıt vermiyor, bekleniyor… (güncelleme sürüyor)")) }
                if (steps >= total - 1 || fails >= 8) { finishUpdate(true, "Güncelleme tamamlandı ✔ (panel yeniden başladı)"); return }
            }
            if (polls > 400) { finishUpdate(false, "Zaman aşımı; modemde  cat /tmp/kediray-update.log  ile bak."); return }
        }
    }

    fun ackWhatsNew() {
        _state.update { it.copy(whatsNew = null) }
        viewModelScope.launch { attempt { repo.kediWhatsNewAck() } }
    }

    private fun finishUpdate(ok: Boolean, text: String) {
        if (ok) whatsNewChecked = false
        _state.update { it.copy(update = it.update.copy(busy = false, finished = true, ok = ok, label = text, newer = false, checkMsg = ""), message = text) }
    }

    fun clearUpdate() {
        if (!_state.value.update.busy) _state.update { it.copy(update = UpdateState()) }
    }

    private fun act(success: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                _state.update { it.copy(message = success) }
                delay(2000)
                refreshSlow()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = friendly(e)) }
            }
        }
    }

    // ---- Ayarlar -------------------------------------------------------------

    fun setTheme(mode: Int) {
        settingsStore.setTheme(mode)
        _settings.update { it.copy(themeMode = mode, themeId = "") }
    }

    fun setThemeId(id: String) {
        settingsStore.setThemeId(id)
        _settings.update { it.copy(themeId = id) }
    }

    /** Özel tema ekler ya da (aynı kimlik varsa) günceller ve seçer. */
    fun saveCustomTheme(t: com.saban.aw1kpanel.data.ThemeDef) {
        val list = _settings.value.customThemes.filter { it.id != t.id } + t
        settingsStore.saveCustomThemes(list)
        settingsStore.setThemeId(t.id)
        _settings.update { it.copy(customThemes = list, themeId = t.id) }
    }

    fun deleteCustomTheme(id: String) {
        val list = _settings.value.customThemes.filter { it.id != id }
        settingsStore.saveCustomThemes(list)
        if (_settings.value.themeId == id) settingsStore.setThemeId("")
        _settings.update { it.copy(customThemes = list, themeId = if (it.themeId == id) "" else it.themeId) }
    }

    /** Anonim kullanım sayısı izni (ilk giriş sorusu ve Ayarlar anahtarı). Evet ise bugünkü gönderim hemen denenir. */
    fun setStatsConsent(yes: Boolean) {
        settingsStore.saveStatsConsent(yes)
        _settings.update { it.copy(statsConsent = if (yes) 1 else 0) }
        if (yes) pingStatsIfDue()
    }

    /** İzin verilmişse ve bugün gönderilmediyse sayaca haber verir (günde en çok bir kez). */
    fun pingStatsIfDue() {
        viewModelScope.launch {
            com.saban.aw1kpanel.data.UsageCounter.pingIfDue(settingsStore, APP_VERSION)
        }
    }

    fun setSmsAlert(on: Boolean) {
        settingsStore.setSmsAlert(on)
        _settings.update { it.copy(smsAlert = on) }
    }

    fun setVpnAlert(on: Boolean) {
        settingsStore.setVpnAlert(on)
        _settings.update { it.copy(vpnAlert = on) }
    }

    fun showMessage(text: String) {
        _state.update { it.copy(message = text) }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    private suspend fun <T> attempt(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private fun friendly(e: Exception): String = when (e) {
        is UbusException -> e.message ?: "Modem hatası"
        is IOException -> "Modeme ulaşılamıyor (${e.javaClass.simpleName}: ${e.message})"
        else -> e.message ?: "Beklenmeyen hata"
    }
}
