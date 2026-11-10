package pt.comandoprojetor.net

import android.content.Context
import java.io.BufferedInputStream
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import javax.net.ssl.SSLSocket
import kotlin.concurrent.thread

/**
 * Ligação de comando (porta 6466). Fica aberta enquanto a app está em uso,
 * por isso cada tecla é só uma escrita pequena no socket — sem atrasos de ligação.
 */
class RemoteConnection(
    context: Context,
    private val host: String,
    private val onPronto: () -> Unit,
    private val onFechado: (estavaPronto: Boolean) -> Unit,
) {
    private val ks = CertManager.loadOrCreate(context)
    private val envio = Executors.newSingleThreadExecutor()
    @Volatile private var socket: SSLSocket? = null
    @Volatile private var pronto = false
    @Volatile private var fechadoPorNos = false

    /** Bloqueante: chamar fora da thread principal. */
    fun connect() {
        val s = CertManager.sslContext(ks).socketFactory.createSocket() as SSLSocket
        s.tcpNoDelay = true // envia cada tecla de imediato
        s.keepAlive = true
        s.connect(InetSocketAddress(host, PORTA), 5000)
        s.startHandshake()
        socket = s
        thread(name = "comando-leitura", isDaemon = true) { cicloLeitura(s) }
    }

    fun sendKey(keyCode: Int, direcao: Int = CURTO) {
        enviar(ProtoWriter().message(10, ProtoWriter().int(1, keyCode).int(2, direcao)))
    }

    fun close() {
        fechadoPorNos = true
        runCatching { socket?.close() }
        envio.shutdown()
    }

    private fun cicloLeitura(s: SSLSocket) {
        try {
            val input = BufferedInputStream(s.inputStream)
            while (true) {
                tratar(ProtoMessage.parse(Framing.read(input)))
            }
        } catch (_: Exception) {
        } finally {
            val estava = pronto
            pronto = false
            runCatching { s.close() }
            if (!fechadoPorNos) onFechado(estava)
        }
    }

    private fun tratar(m: ProtoMessage) {
        when {
            // remote_configure: o projetor apresenta-se, respondemos com os nossos dados
            m.has(1) -> enviar(
                ProtoWriter().message(
                    1, ProtoWriter()
                        .int(1, FUNCIONALIDADES)
                        .message(
                            2, ProtoWriter()
                                .string(1, "Comando Projetor")
                                .string(2, "ComandoProjetor")
                                .int(3, 1)
                                .string(4, "1")
                                .string(5, "pt.comandoprojetor")
                                .string(6, "1.0")
                        )
                )
            )
            // remote_set_active
            m.has(2) -> {
                enviar(ProtoWriter().message(2, ProtoWriter().int(1, FUNCIONALIDADES)))
                if (!pronto) {
                    pronto = true
                    onPronto()
                }
            }
            // remote_ping_request -> remote_ping_response (senão o projetor fecha a ligação)
            m.has(8) -> {
                val v = m.message(8)?.int(1) ?: 0
                enviar(ProtoWriter().message(9, ProtoWriter().int(1, v)))
            }
        }
    }

    private fun enviar(w: ProtoWriter) {
        val bytes = w.toByteArray()
        if (envio.isShutdown) return
        runCatching {
            envio.execute {
                val s = socket ?: return@execute
                runCatching { Framing.write(s.outputStream, bytes) }
                    .onFailure { runCatching { s.close() } }
            }
        }
    }

    companion object {
        const val PORTA = 6466
        const val CURTO = 3
        private const val FUNCIONALIDADES = 622
    }
}
