package com.streambox.app.utils

import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

object AdBlocker {
    private val AD_HOSTS = setOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "adservice.google.com", "pagead2.googlesyndication.com",
        "facebook.com/tr", "connect.facebook.net",
        "analytics.google.com", "google-analytics.com",
        "googletagmanager.com", "zeronnet.com", "ymtrack.com",
        "propellerads.com", "popads.net", "popcash.net",
        "adcash.com", "exoclick.com", "mexc.com", "1xbet.com",
        "bet365.com", "4rabet.com", "parimatch.com",
        "push.kd2.org", "push4site.com", "onesignal.com",
        "adcolony.com", "applvn.com", "applovin.com",
        "unityads.unity3d.com", "vungle.com", "inmobi.com",
        "tapjoy.com", "startapp.com", "ironrc.com",
        "supersonicads.com", "moatads.com", "integralads.com",
        "adnxs.com", "openx.net", "rubiconproject.com",
        "pubmatic.com", "criteo.com", "outbrain.com",
        "taboola.com", "teads.tv", "zedo.com",
        "bidswitch.net", "bluekai.com", "casalemedia.com",
        "contextweb.com", "creative-serving.com", "demdex.net",
        "districtm.io", "everesttech.net", "gumgum.com",
        "indexexchange.com", "lijit.com", "mathtag.com",
        "media.net", "mnet-ad.net", "myvisualiq.net",
        "nexac.com", "owneriq.net", "quantserve.com",
        "rhythmone.com", "rlcdn.com", "simpli.fi",
        "sitescout.com", "smartadserver.com", "spotxchange.com",
        "technoratimedia.com", "tremorhub.com", "turn.com",
        "videologygroup.com", "w55c.net", "yahoo.com",
        "yandex.ru/ads", "yandex.com/ads", "yandex.net",
        "adroll.com", "adfox.ru", "adriver.ru",
        "betting", "casino", "gambling", "bonus"
    )

    private val AD_KEYWORDS = listOf(
        "/ads/", "/ad/", "/banner/", "/pop/", "/pixel", 
        "tracker", "analytics", "pagead", "doubleclick"
    )

    fun isAd(url: String): Boolean {
        val lowerUrl = url.lowercase()
        val host = java.net.URI(url).host?.lowercase() ?: return false
        
        // Check exact host match or subdomain
        if (AD_HOSTS.any { host.contains(it) }) return true
        
        // Check keywords in URL path
        if (AD_KEYWORDS.any { lowerUrl.contains(it) }) return true
        
        return false
    }

    fun createEmptyResponse(): WebResourceResponse {
        return WebResourceResponse(
            "text/plain",
            "UTF-8",
            ByteArrayInputStream("".toByteArray())
        )
    }
}
