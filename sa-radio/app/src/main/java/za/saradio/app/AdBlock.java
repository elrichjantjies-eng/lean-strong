package za.saradio.app;

import android.webkit.WebResourceResponse;
import java.io.ByteArrayInputStream;
import java.util.Locale;

/**
 * Used when a story is shown as the publisher's original page: requests to ad networks and
 * trackers are refused, and leftover ad slots are hidden. (The reader view needs none of this; it
 * never loads the page's scripts in the first place.)
 */
final class AdBlock {
    private AdBlock() {}

    /** Ad exchanges, ad servers, recommendation widgets and trackers seen on South African news sites. */
    private static final String[] HOSTS = {
        // Google ads and tags
        "doubleclick.net", "googlesyndication.com", "googletagservices.com", "googleadservices.com",
        "adservice.google.com", "adservice.google.co.za", "imasdk.googleapis.com", "googletagmanager.com",
        "google-analytics.com", "analytics.google.com",
        // Exchanges and ad servers
        "amazon-adsystem.com", "adnxs.com", "adnxs-simple.com", "pubmatic.com", "rubiconproject.com", "openx.net",
        "criteo.com", "criteo.net", "casalemedia.com", "3lift.com", "sharethrough.com", "indexww.com", "yieldmo.com",
        "adform.net", "smartadserver.com", "adsrvr.org", "bidswitch.net", "contextweb.com", "lijit.com", "sovrn.com",
        "33across.com", "gumgum.com", "onetag-sys.com", "seedtag.com", "richaudience.com", "smaato.net", "inmobi.com",
        "media.net", "teads.tv", "connatix.com", "primis.tech", "vidazoo.com", "spotxchange.com", "springserve.com",
        "aniview.com", "ad.gt", "adition.com", "weborama.fr", "adsafeprotected.com", "moatads.com", "doubleverify.com",
        "id5-sync.com", "permutive.com", "permutive.app", "prebid.org", "adkernel.com", "e-planning.net", "mgid.com",
        "revcontent.com", "zemanta.com", "admanmedia.com", "yieldlove.com", "adpushup.com", "ezoic.net",
        // "Recommended for you" widgets
        "taboola.com", "outbrain.com", "outbrainimg.com", "taboolasyndication.com",
        // Trackers and heatmaps
        "scorecardresearch.com", "quantserve.com", "quantcount.com", "chartbeat.com", "chartbeat.net", "hotjar.com",
        "clarity.ms", "facebook.net", "connect.facebook.net", "analytics.tiktok.com",
        "bat.bing.com", "ads.linkedin.com", "snap.licdn.com", "ads-twitter.com", "static.ads-twitter.com",
        "parsely.com", "cxense.com", "newrelic.com", "nr-data.net", "effectivemeasure.net", "onesignal.com",
    };

    /** Hides ad slots that some pages reserve space for even when the ad never arrives. */
    static final String HIDE_CSS =
            "ins.adsbygoogle,[id^='div-gpt-ad'],[id^='google_ads_iframe'],iframe[id^='google_ads'],[data-google-query-id],"
                    + "[data-ad-slot],[class*='taboola'],[id*='taboola'],[class*='OUTBRAIN'],[class*='outbrain'],.ad-container,"
                    + ".ad-slot,.ad-wrapper,.ad-unit,.advert,.advertisement,.adsbox,.sticky-ad,.leaderboard-ad,[id^='ad-slot'],"
                    + "[class^='ad-'],[id^='ad_'],[aria-label='Advertisement']{display:none!important;height:0!important}";

    static String injectScript() {
        return "(function(){if(document.getElementById('sar-adblock'))return;var s=document.createElement('style');"
                + "s.id='sar-adblock';s.textContent=" + org.json.JSONObject.quote(HIDE_CSS) + ";"
                + "(document.head||document.documentElement).appendChild(s);})();";
    }

    static boolean blocked(String host) {
        if (host == null) return false;
        String h = host.toLowerCase(Locale.ROOT);
        for (String b : HOSTS) {
            if (h.equals(b) || h.endsWith("." + b)) return true;
        }
        return false;
    }

    /** An empty answer for a refused request. */
    static WebResourceResponse empty() {
        return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(new byte[0]));
    }
}
