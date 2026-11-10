package pt.comandoprojetor

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pt.comandoprojetor.net.Discovery
import pt.comandoprojetor.net.Pairing
import pt.comandoprojetor.net.PairingException
import pt.comandoprojetor.net.RemoteConnection
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLException

enum class Ecra { CONFIG, CODIGO, COMANDO }

data class Dispositivo(val nome: String, val ip: String)

data class UiState(
    val ecra: Ecra = Ecra.CONFIG,
    val ip: String = "",
    val dispositivos: List<Dispositivo> = emptyList(),
    val aProcurar: Boolean = false,
    val ocupado: Boolean = false,
    val ligado: Boolean = false,
    val mensagem: String? = null,
)

class RemoteViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("comando", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(UiState(ip = prefs.getString("ip", "") ?: ""))
    val state = _state.asStateFlow()

    private val discovery = Discovery(app)
    private var procuraJob: Job? = null
    private var pairing: Pairing? = null
    private var remote: RemoteConnection? = null
    @Volatile private var aLigar = false

    init {
        if (prefs.getBoolean("emparelhado", false) && state.value.ip.isNotBlank()) {
            _state.update { it.copy(ecra = Ecra.COMANDO) }
            ligar()
        }
    }

    // ---------- Configuração ----------

    fun setIp(ip: String) = _state.update { it.copy(ip = ip.trim(), mensagem = null) }

    fun procurar() {
        _state.update { it.copy(aProcurar = true, dispositivos = emptyList(), mensagem = null) }
        discovery.start { nome, ip ->
            _state.update { s ->
                if (s.dispositivos.any { it.ip == ip }) s
                else s.copy(
                    dispositivos = s.dispositivos + Dispositivo(nome, ip),
                    ip = s.ip.ifBlank { ip },
                )
            }
        }
        procuraJob?.cancel()
        procuraJob = viewModelScope.launch {
            delay(10_000)
            pararProcura()
            if (state.value.dispositivos.isEmpty()) {
                mensagem("Não encontrei nenhum projetor. Escreve o IP à mão.")
            }
        }
    }

    fun pararProcura() {
        procuraJob?.cancel()
        discovery.stop()
        _state.update { it.copy(aProcurar = false) }
    }

    // ---------- Emparelhamento ----------

    fun emparelhar() {
        val ip = state.value.ip
        if (ip.isBlank()) return mensagem("Indica o IP do projetor.")
        pararProcura()
        viewModelScope.launch {
            _state.update { it.copy(ocupado = true, mensagem = null) }
            try {
                pairing?.close()
                val p = withContext(Dispatchers.IO) { Pairing(getApplication(), ip).also { it.start() } }
                pairing = p
                _state.update { it.copy(ecra = Ecra.CODIGO) }
            } catch (e: Exception) {
                mensagem(textoErro(e, ip))
            } finally {
                _state.update { it.copy(ocupado = false) }
            }
        }
    }

    fun confirmarCodigo(codigo: String) {
        val p = pairing ?: return
        viewModelScope.launch {
            _state.update { it.copy(ocupado = true, mensagem = null) }
            try {
                withContext(Dispatchers.IO) { p.finish(codigo) }
                p.close()
                pairing = null
                prefs.edit().putString("ip", state.value.ip).putBoolean("emparelhado", true).apply()
                _state.update { it.copy(ecra = Ecra.COMANDO) }
                ligar()
            } catch (e: PairingException) {
                // Código mal escrito: fica no mesmo ecrã para tentar de novo
                mensagem(e.message)
            } catch (e: Exception) {
                p.close()
                pairing = null
                _state.update { it.copy(ecra = Ecra.CONFIG) }
                mensagem("O emparelhamento falhou: ${textoErro(e, state.value.ip)}")
            } finally {
                _state.update { it.copy(ocupado = false) }
            }
        }
    }

    fun cancelarEmparelhamento() {
        pairing?.close()
        pairing = null
        _state.update { it.copy(ecra = Ecra.CONFIG, mensagem = null) }
    }

    // ---------- Comando ----------

    fun ligar() {
        if (aLigar || state.value.ligado) return
        aLigar = true
        val ip = state.value.ip
        viewModelScope.launch {
            try {
                remote?.close()
                remote = null
                val r = RemoteConnection(
                    getApplication(), ip,
                    onPronto = { _state.update { it.copy(ligado = true, mensagem = null) } },
                    onFechado = { estavaPronto ->
                        _state.update {
                            it.copy(
                                ligado = false,
                                mensagem = if (estavaPronto) "Ligação perdida. Toca num botão para religar."
                                else "O projetor fechou a ligação. Se continuar, emparelha de novo (menu ⋮).",
                            )
                        }
                    },
                )
                withContext(Dispatchers.IO) { r.connect() }
                remote = r
            } catch (e: SSLException) {
                mensagem("O projetor recusou a ligação. Talvez precises de emparelhar de novo (menu ⋮).")
            } catch (e: Exception) {
                mensagem(textoErro(e, ip))
            } finally {
                aLigar = false
            }
        }
    }

    fun religar() {
        remote?.close()
        remote = null
        _state.update { it.copy(ligado = false) }
        ligar()
    }

    /** Chamado ao voltar à app: a ligação pode ter caído em segundo plano. */
    fun retomar() {
        if (state.value.ecra == Ecra.COMANDO && !state.value.ligado) ligar()
    }

    fun tecla(keyCode: Int) {
        val r = remote
        if (r != null && state.value.ligado) r.sendKey(keyCode) else ligar()
    }

    fun esquecer() {
        remote?.close()
        remote = null
        prefs.edit().putBoolean("emparelhado", false).apply()
        _state.update { it.copy(ecra = Ecra.CONFIG, ligado = false, mensagem = null) }
    }

    override fun onCleared() {
        discovery.stop()
        pairing?.close()
        remote?.close()
    }

    // ---------- Auxiliares ----------

    private fun mensagem(m: String?) = _state.update { it.copy(mensagem = m) }

    private fun textoErro(e: Exception, ip: String): String = when (e) {
        is ConnectException ->
            "Não consegui ligar a $ip. Confirma o IP e que o telemóvel está na mesma rede. " +
                "Se o IP estiver certo, o projetor pode não ter o serviço de comando do Android TV."
        is NoRouteToHostException -> "O IP $ip não está acessível nesta rede."
        is SocketTimeoutException -> "O projetor não respondeu a tempo. Está ligado e na mesma rede?"
        is PairingException -> e.message ?: "Erro no emparelhamento."
        else -> e.message ?: e.javaClass.simpleName
    }
}
