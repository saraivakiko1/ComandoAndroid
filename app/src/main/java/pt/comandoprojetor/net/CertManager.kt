package pt.comandoprojetor.net

import android.content.Context
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Date
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * O Android TV identifica o comando pelo certificado TLS do cliente.
 * Geramos um uma vez e guardamo-lo; é ele que fica "emparelhado".
 */
object CertManager {
    private const val FICHEIRO = "cliente.p12"
    private const val ALIAS = "cliente"
    private val PASSE = "comando-projetor".toCharArray()

    @Synchronized
    fun loadOrCreate(context: Context): KeyStore {
        val file = File(context.filesDir, FICHEIRO)
        val ks = KeyStore.getInstance("PKCS12")
        if (file.exists()) {
            try {
                file.inputStream().use { ks.load(it, PASSE) }
                if (ks.containsAlias(ALIAS)) return ks
            } catch (_: Exception) {
                file.delete()
            }
        }

        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048, SecureRandom())
        val kp = kpg.generateKeyPair()

        val nome = X500Name("CN=atvremote, O=ComandoProjetor, C=PT")
        val agora = Date()
        val ate = Date(agora.time + 10L * 365 * 24 * 3600 * 1000)
        val builder = JcaX509v3CertificateBuilder(
            nome, BigInteger.valueOf(agora.time), agora, ate, nome, kp.public
        )
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(kp.private)
        val cert = JcaX509CertificateConverter().getCertificate(builder.build(signer))

        ks.load(null, null)
        ks.setKeyEntry(ALIAS, kp.private, PASSE, arrayOf(cert))
        file.outputStream().use { ks.store(it, PASSE) }
        return ks
    }

    fun clientCert(ks: KeyStore): X509Certificate = ks.getCertificate(ALIAS) as X509Certificate

    fun sslContext(ks: KeyStore): SSLContext {
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(ks, PASSE)
        // O projetor usa um certificado autoassinado; a confiança vem do código de emparelhamento.
        val aceitaTudo = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
        return SSLContext.getInstance("TLS").apply {
            init(kmf.keyManagers, arrayOf(aceitaTudo), SecureRandom())
        }
    }
}
