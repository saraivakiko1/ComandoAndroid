package pt.comandoprojetor.net

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import java.net.Inet4Address

/** Procura dispositivos Android TV na rede local (mDNS). */
class Discovery(context: Context) {
    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val pendentes = ArrayDeque<NsdServiceInfo>()
    private var aResolver = false
    private var listener: NsdManager.DiscoveryListener? = null

    fun start(onEncontrado: (nome: String, ip: String) -> Unit) {
        stop()
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                synchronized(this@Discovery) { pendentes.addLast(serviceInfo) }
                resolverProximo(onEncontrado)
            }
        }
        listener = l
        runCatching { nsd.discoverServices(TIPO, NsdManager.PROTOCOL_DNS_SD, l) }
    }

    fun stop() {
        listener?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        listener = null
        synchronized(this) { pendentes.clear() }
    }

    // O NsdManager só resolve um serviço de cada vez em versões antigas do Android.
    @Suppress("DEPRECATION")
    private fun resolverProximo(onEncontrado: (String, String) -> Unit) {
        val info = synchronized(this) {
            if (aResolver || pendentes.isEmpty()) return
            aResolver = true
            pendentes.removeFirst()
        }
        nsd.resolveService(info, object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = fim()

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                val host = serviceInfo.host
                val ip = (host as? Inet4Address)?.hostAddress ?: host?.hostAddress
                if (ip != null) onEncontrado(serviceInfo.serviceName, ip)
                fim()
            }

            private fun fim() {
                synchronized(this@Discovery) { aResolver = false }
                resolverProximo(onEncontrado)
            }
        })
    }

    companion object {
        private const val TIPO = "_androidtvremote2._tcp"
    }
}
