package com.buzzingmountain.dingclock.notify

/**
 * Out-of-band channel for telling the operator something went wrong (e.g. SMS verification
 * required). Implementations: DingTalk robot webhook, email, Bark, etc.
 */
interface Notifier {
    /** Returns true if the message was delivered. */
    suspend fun send(title: String, markdown: String): Boolean
}
