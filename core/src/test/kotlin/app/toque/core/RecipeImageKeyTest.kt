package app.toque.core

import app.toque.core.recipes.RecipeImageKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeImageKeyTest {
    @Test fun differentUrlsWithTheSameBasenameHaveDifferentCacheFiles() {
        val first = RecipeImageKey.fileName("https://example.test/one/image_thumbnail.jpg")
        val second = RecipeImageKey.fileName("https://example.test/two/image_thumbnail.jpg")
        assertEquals("url-1e722303166d6d74c2f9c41e13cda0229b06cc37547743e0348f59f97bf7f097.jpg", first)
        assertNotEquals(first, second)
        assertTrue(Regex("url-[0-9a-f]{64}\\.jpg").matches(first))
    }

    @Test fun unicodeAndQueryAreHashedAsTheExactUtf8Url() {
        val url = "https://example.test/rezepte/käse/image_thumbnail.jpg?größe=800&quelle=küche"
        val expected = "url-6e0c3e41d3aef159f70c30616b6512d1229538c70732127c63a3129a6c0f7018.jpg"
        assertEquals(expected, RecipeImageKey.fileName(url))
        assertNotEquals(expected, RecipeImageKey.fileName(url.replace("käse", "k%C3%A4se")))
        assertNotEquals(RecipeImageKey.fileName("https://example.test/image_thumbnail.jpg?size=small"),
            RecipeImageKey.fileName("https://example.test/image_thumbnail.jpg?size=large"))
    }

    @Test fun onlyTheOriginalOfficialCdnCanReuseBasenameImages() {
        assertTrue(RecipeImageKey.allowsLegacyFallback("https://mc-web-content-cdn.tecpal.com/path/image.jpg"))
        assertTrue(RecipeImageKey.allowsLegacyFallback("HTTP://MC-WEB-CONTENT-CDN.TECPAL.COM/path/image.jpg"))
        for (url in listOf(
            "https://mc-web-user-content-cdn.tecpal.com/path/image_thumbnail.jpg",
            "https://example.test/path/image.jpg",
            "https://mc-web-content-cdn.tecpal.com.evil.test/image.jpg",
            "https://mc-web-content-cdn.tecpal.com@evil.test/image.jpg",
            "ftp://mc-web-content-cdn.tecpal.com/image.jpg",
            "image.jpg", "https://invalid host/image.jpg",
        )) assertFalse(url, RecipeImageKey.allowsLegacyFallback(url))
    }
}
