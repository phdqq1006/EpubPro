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

/**
 * Lớp triển khai DNS tùy chỉnh cho OkHttp với cơ chế phân giải dự phòng DNS-over-HTTPS (DoH).
 *
 * Mục đích: Giải quyết triệt để lỗi `UnknownHostException` trên Android Emulator hoặc thiết bị mạng di động
 * khi truy cập các domain động (như Cloudflare Tunnel, Workers, Render) bị DNS nội bộ chặn hoặc lỗi phân giải IPv6.
 */
@Singleton
class FallbackDns @Inject constructor() : Dns {

    private data class CachedDnsEntry(
        val addresses: List<InetAddress>,
        val timestamp: Long
    )

    private val cache = ConcurrentHashMap<String, CachedDnsEntry>()

    /**
     * Tra cứu địa chỉ IP của một hostname cụ thể.
     *
     * @param hostname Tên miền cần phân giải (ví dụ: `epubbackend.onrender.com`).
     * @return Danh sách các địa chỉ `InetAddress` đã được phân giải, ưu tiên địa chỉ IPv4.
     * @throws UnknownHostException Ném ra khi cả DNS hệ thống và DNS-over-HTTPS đều không tìm thấy địa chỉ IP.
     */
    override fun lookup(hostname: String): List<InetAddress> {
        val now = System.currentTimeMillis()
        val cached = cache[hostname]
        if (cached != null && (now - cached.timestamp) < CACHE_TTL_MS) {
            return cached.addresses
        }

        // 1. Thử phân giải bằng DNS mặc định của hệ thống trước (ưu tiên địa chỉ IPv4)
        try {
            val systemAddresses = Dns.SYSTEM.lookup(hostname)
            if (systemAddresses.isNotEmpty()) {
                val sorted = systemAddresses.sortedBy { if (it is Inet4Address) 0 else 1 }
                cache[hostname] = CachedDnsEntry(sorted, now)
                return sorted
            }
        } catch (_: UnknownHostException) {
            // DNS hệ thống thất bại, chuyển sang cơ chế dự phòng DNS-over-HTTPS
        } catch (_: Exception) {
            // Lỗi mạng hoặc quá thời gian chờ
        }

        // 2. Dự phòng qua DNS-over-HTTPS (Cloudflare 1.1.1.1 & Google DNS)
        val dohAddresses = resolveViaDoh(hostname)
        if (dohAddresses.isNotEmpty()) {
            cache[hostname] = CachedDnsEntry(dohAddresses, now)
            return dohAddresses
        }

        throw UnknownHostException("Không thể phân giải địa chỉ host \"$hostname\": Không tìm thấy IP tương ứng.")
    }

    /**
     * Thực hiện truy vấn DNS-over-HTTPS dạng JSON tới máy chủ Cloudflare và Google.
     *
     * @param hostname Tên miền cần phân giải.
     * @return Danh sách các địa chỉ `InetAddress` IPv4 tìm được.
     */
    private fun resolveViaDoh(hostname: String): List<InetAddress> {
        val dohEndpoints = listOf(
            "https://1.1.1.1/dns-query?name=$hostname&type=A",
            "https://dns.google/resolve?name=$hostname&type=A"
        )

        for (endpoint in dohEndpoints) {
            var connection: HttpURLConnection? = null
            try {
                val url = URL(endpoint)
                connection = (url.openConnection() as HttpURLConnection).apply {
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
                    // Type 1 là DNS bản ghi A (IPv4)
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
                // Thử endpoint DoH tiếp theo
            } finally {
                runCatching { connection?.disconnect() }
            }
        }

        return emptyList()
    }

    companion object {
        /** Thời gian tồn tại của bản ghi DNS trong bộ nhớ đệm (10 phút) */
        private const val CACHE_TTL_MS = 10 * 60 * 1000L
    }
}
