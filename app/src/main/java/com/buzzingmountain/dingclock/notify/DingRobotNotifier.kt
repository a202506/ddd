package com.buzzingmountain.dingclock.notify

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import timber.log.Timber
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import android.util.Base64

/**
 * Sends markdown messages to a DingTalk custom robot. Honors the optional HMAC-SHA256
 * "加签" mechanism: timestamp + secret → signature appended as query params.
 */
class DingRobotNotifier(
    private val webhookUrl: String,
    private val secret: String? = null,
    private val client: OkHttpClient = defaultClient,
) : Notifier {

    override suspend fun send(title: String, markdown: String): Boolean = withContext(Dispatchers.IO) {
        if (webhookUrl.isBlank()) return@withContext false
        runCatching {
            val finalUrl = if (secret.isNullOrBlank()) webhookUrl else signedUrl(webhookUrl, secret)
            val payload = JSONObject().apply {
                put("msgtype", "markdown")
                put(
                    "markdown",
                    JSONObject().apply {
                        put("title", title)
                        put("text", markdown)
                    },
                )
            }
            val body = payload.toString().toRequestBody(JSON)
            val req = Request.Builder().url(finalUrl).post(body).build()
            client.newCall(req).execute().use { resp ->
                val ok = resp.isSuccessful
                Timber.i("Robot webhook resp=%d ok=%s", resp.code, ok)
                ok
            }
        }.getOrElse {
            Timber.e(it, "Robot webhook send failed")
            false
        }
    }

    private fun signedUrl(base: String, secret: String): String {
        val ts = System.currentTimeMillis().toString()
        val toSign = "$ts\n$secret"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val raw = mac.doFinal(toSign.toByteArray(Charsets.UTF_8))
        val signB64 = Base64.encodeToString(raw, Base64.NO_WRAP)
        val signEnc = URLEncoder.encode(signB64, "UTF-8")
        val joiner = if (base.contains('?')) "&" else "?"
        return "$base${joiner}timestamp=$ts&sign=$signEnc"
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val defaultClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
