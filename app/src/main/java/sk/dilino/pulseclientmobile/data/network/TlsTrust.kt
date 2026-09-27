package sk.dilino.pulseclientmobile.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.X509TrustManager

/**
 * The pulse server verifies nothing about *us*, but we must verify *it*: without that, anyone on
 * the network path could capture the login password. So the default is normal verification
 * (public CAs + hostname check). A self-signed server can instead be *pinned* — the user
 * confirms its SHA-256 fingerprint once, and only that exact certificate is accepted afterwards.
 */
fun buildHttpClient(pinnedSha256: String?): OkHttpClient {
    val builder = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
    if (pinnedSha256 != null) {
        val trust = PinnedTrustManager(pinnedSha256)
        builder.sslSocketFactory(sslContext(trust).socketFactory, trust)
            // The pin identifies the certificate itself, so the host name it was issued for is moot.
            .hostnameVerifier { _, _ -> true }
    }
    return builder.build()
}

private fun sslContext(trust: X509TrustManager): SSLContext =
    SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), SecureRandom()) }

/** `AB:CD:…` — the same shape `openssl x509 -fingerprint -sha256` prints. */
fun sha256Fingerprint(cert: X509Certificate): String =
    MessageDigest.getInstance("SHA-256").digest(cert.encoded)
        .joinToString(":") { "%02X".format(it) }

private class PinnedTrustManager(private val pinned: String) : X509TrustManager {
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) =
        throw CertificateException("client certificates are not used")

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        val leaf = chain?.firstOrNull() ?: throw CertificateException("server sent no certificate")
        if (!sha256Fingerprint(leaf).equals(pinned, ignoreCase = true)) {
            throw CertificateException("Server certificate changed — it no longer matches the one you trusted")
        }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
}

/** True when [this] (or a cause) is a TLS / certificate problem rather than a network one. */
fun Throwable.isTlsFailure(): Boolean =
    generateSequence(this) { it.cause }.any { it is SSLException || it is CertificateException }

/**
 * Reads the certificate a server presents without trusting it, so the user can be shown its
 * fingerprint. Nothing sensitive is ever sent over this connection.
 */
suspend fun probeServerCertificate(baseUrl: String): Result<String> = withContext(Dispatchers.IO) {
    runCatching {
        var seen: X509Certificate? = null
        val recorder = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                seen = chain?.firstOrNull()
            }
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
        val client = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .sslSocketFactory(sslContext(recorder).socketFactory, recorder)
            .hostnameVerifier { _, _ -> true }
            .build()
        val request = Request.Builder().url("${baseUrl.trimEnd('/')}/healthz").get().build()
        client.newCall(request).execute().close()
        sha256Fingerprint(seen ?: error("server presented no certificate"))
    }
}
