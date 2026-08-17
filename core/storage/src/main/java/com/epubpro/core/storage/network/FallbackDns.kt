package com.epubpro.core.storage.network

import okhttp3.Dns
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.URL
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FallbackDns @Inject constructor() : Dns {

    private val cache = ConcurrentHashMap<String, List<InetAddress>>()

    override fun lookup(hostname: String): List<InetAddress> {
        // Return cached addresses if available
        cache[hostname]?.let { return it }

        // 1. Try standard system DNS first (prefer IPv4 for Android emulator compatibility)
        try {
            val systemAddresses = Dns.SYSTEM.lookup(hostname)
            if (systemAddresses.isNotEmpty()) {
                val sorted = systemAddresses.sortedBy { if (it is Inet4Address) 0 else 1 }
                cache[hostname] = sorted
                return sorted
            }
        } catch (_: UnknownHostException) {
            // System DNS failed, fallback to DNS-over-HTTPS
        } catch (_: Exception) {
            // Network glitch or timeout
        }

        // 2. Fallback via DNS-over-HTTPS (Google DNS & Cloudflare DNS)
        val dohAddresses = resolveViaDoh(hostname)
        if (dohAddresses.isNotEmpty()) {
            cache[hostname] = dohAddresses
            return dohAddresses
        }

        throw UnknownHostException("Unable to resolve host \"$hostname\": No address associated with hostname")
    }

    private fun resolveViaDoh(hostname: String): List<InetAddress> {
        val dohEndpoints = listOf(
            "https://1.1.1.1/dns-query?name=$hostname&type=A",
            "https://dns.google/resolve?name=$hostname&type=A"
        )

        for (endpoint in dohEndpoints) {
            try {
                val url = URL(endpoint)
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    setRequestProperty("Accept", "application/dns-json")
                    connectTimeout = 4000
                    readTimeout = 4000
                }

                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val answers = json.optJSONArray("Answer") ?: continue
                val addresses = mutableListOf<InetAddress>()

                for (i in 0 until answers.length()) {
                    val answerObj = answers.getJSONObject(i)
                    val ip = answerObj.optString("data")
                    // Type 1 is DNS A record (IPv4)
                    if (answerObj.optInt("type") == 1 && ip.isNotBlank()) {
                        runCatching {
                            addresses.add(InetAddress.getByName(ip))
                        }
                    }
                }

                if (addresses.isNotEmpty()) {
                    return addresses
                }
            } catch (_: Exception) {
                // Try next DoH endpoint
            }
        }

        return emptyList()
    }
}
