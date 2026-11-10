package pt.comandoprojetor.net

import android.content.Context
import java.io.Closeable
import java.math.BigInteger
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.security.interfaces.RSAPublicKey
import javax.net.ssl.SSLSocket

class PairingException(msg: String) : Exception(msg)

/**
 * Emparelhamento (porta 6467). Funções bloqueantes: chamar fora da thread principal.
 *
 * 1. start(): pede o emparelhamento; o projetor mostra um código de 6 caracteres hex.
 * 2. finish(codigo): prova que vimos o código e conclui.
 */
class Pairing(context: Context, private val host: String) : Closeable {

    private val ks = CertManager.loadOrCreate(context)
    private var socket: SSLSocket? = null

    fun start() {
        val s = CertManager.sslContext(ks).socketFactory.createSocket() as SSLSocket
        socket = s
        s.connect(InetSocketAddress(host, PORTA), 5000)
        s.soTimeout = 20_000
        s.startHandshake()

        // pairing_request
        enviar { it.message(10, ProtoWriter().string(1, "atvremote").string(2, "Comando Projetor")) }
        esperar(11)
        // pairing_option
        enviar { it.message(20, ProtoWriter().message(1, codificacao()).int(3, PAPEL_INPUT)) }
        esperar(20)
        // pairing_configuration -> o código aparece no projetor
        enviar { it.message(30, ProtoWriter().message(1, codificacao()).int(2, PAPEL_INPUT)) }
        esperar(31)
    }

    fun finish(codigo: String) {
        val c = codigo.trim().uppercase()
        if (c.length != 6 || c.any { it !in "0123456789ABCDEF" }) {
            throw PairingException("O código tem de ter 6 caracteres (0-9 e A-F).")
        }
        val s = socket ?: throw IllegalStateException("Emparelhamento não iniciado")

        val cliente = CertManager.clientCert(ks).publicKey as RSAPublicKey
        val servidor = s.session.peerCertificates[0].publicKey as RSAPublicKey

        val md = MessageDigest.getInstance("SHA-256")
        md.update(semSinal(cliente.modulus))
        md.update(semSinal(cliente.publicExponent))
        md.update(semSinal(servidor.modulus))
        md.update(semSinal(servidor.publicExponent))
        md.update(hexParaBytes(c.substring(2)))
        val hash = md.digest()

        // O 1.º byte do código é um controlo: permite detetar um código mal escrito sem o enviar.
        if ((hash[0].toInt() and 0xFF) != c.substring(0, 2).toInt(16)) {
            throw PairingException("Código errado. Confirma o código no projetor e tenta de novo.")
        }

        enviar { it.message(40, ProtoWriter().bytes(1, hash)) }
        esperar(41)
    }

    override fun close() {
        runCatching { socket?.close() }
        socket = null
    }

    private fun codificacao() = ProtoWriter().int(1, CODIFICACAO_HEX).int(2, 6)

    private fun enviar(bloco: (ProtoWriter) -> Unit) {
        val s = socket ?: throw IllegalStateException("Sem ligação")
        val w = ProtoWriter().int(1, 2).int(2, ESTADO_OK) // protocol_version, status
        bloco(w)
        Framing.write(s.outputStream, w.toByteArray())
    }

    private fun esperar(campo: Int) {
        val s = socket ?: throw IllegalStateException("Sem ligação")
        val m = ProtoMessage.parse(Framing.read(s.inputStream))
        val estado = m.int(2) ?: 0
        if (estado != ESTADO_OK) {
            throw PairingException(
                when (estado) {
                    402 -> "O projetor rejeitou o código."
                    401 -> "O projetor rejeitou a configuração de emparelhamento."
                    else -> "O projetor recusou o emparelhamento (estado $estado)."
                }
            )
        }
        if (!m.has(campo)) throw PairingException("Resposta inesperada do projetor.")
    }

    private fun semSinal(n: BigInteger): ByteArray {
        val b = n.toByteArray()
        var i = 0
        while (i < b.size - 1 && b[i] == 0.toByte()) i++
        return b.copyOfRange(i, b.size)
    }

    private fun hexParaBytes(hex: String) =
        ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    companion object {
        const val PORTA = 6467
        private const val ESTADO_OK = 200
        private const val CODIFICACAO_HEX = 3
        private const val PAPEL_INPUT = 1
    }
}
