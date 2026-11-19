package app.toque.core.recipes

import java.security.MessageDigest
import java.net.URI

/** Cache identity for the exact image URL, including its path and query. */
object RecipeImageKey {
    fun fileName(url: String): String = "url-" + MessageDigest.getInstance("SHA-256")
        .digest(url.toByteArray(Charsets.UTF_8))
        .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') } + ".jpg"

    /** Only the original official catalogue's CDN used the shared basename cache. */
    fun allowsLegacyFallback(url: String): Boolean = try {
        val uri = URI(url)
        (uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", ignoreCase = true)) &&
            uri.host.equals("mc-web-content-cdn.tecpal.com", ignoreCase = true)
    } catch (_: Exception) {
        false
    }
}
