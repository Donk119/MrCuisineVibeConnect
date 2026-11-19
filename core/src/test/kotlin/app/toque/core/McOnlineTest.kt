package app.toque.core

import app.toque.core.recipes.McOnline
import app.toque.core.recipes.RecipeCodec
import app.toque.core.recipes.StepProgram
import app.toque.core.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Conversion des recettes de l'API monsieur-cuisine.com (données d'essai inventées, même structure). */
class McOnlineTest {
    private fun fixture(name: String) = javaClass.getResourceAsStream("/fixtures/$name")!!.bufferedReader().use { it.readText() }

    private fun pageData(
        total: Int = 2,
        pageSize: Int = 100,
        currentPage: Int = 1,
        totalPages: Int = 1,
        rows: List<Any?> = listOf(mapOf("id" to 1, "name" to "First"), mapOf("id" to 2, "name" to "Second")),
    ): MutableMap<String, Any?> = linkedMapOf(
        "total" to total, "pageSize" to pageSize, "currentPage" to currentPage,
        "totalPage" to totalPages, "resultNo" to rows.size, "recipes" to rows,
    )

    private fun pageJson(data: Map<String, Any?> = pageData(), code: Any? = 0) =
        Json.write(mapOf("code" to code, "data" to data))

    private fun rejectedPage(json: String) {
        try {
            McOnline.parsePage(json)
            fail("Invalid catalogue page was accepted: $json")
        } catch (_: Exception) {
            // Malformed JSON and invalid response metadata both abort before pruning.
        }
    }

    @Test fun page() {
        val p = McOnline.parsePage(fixture("mc-online-page.json"))
        assertEquals(3, p.total)
        assertEquals(1, p.totalPages)
        assertEquals(listOf(900001L, 900002L, 900003L), p.entries.map { it.apiId })
        assertEquals("2026-04-19 09:00:02", p.entries[0].updated)
        assertEquals("", p.entries[2].updated)
        // Auteur et note, aussi dans la liste (pour garder les notes à jour sans tout retélécharger).
        assertEquals("Chef d'essai", p.entries[0].author)
        assertEquals(4.4, p.entries[0].rating, 0.001)
        assertEquals(500, p.entries[0].ratingCount)
        assertEquals(3.5, p.entries[1].rating, 0.001)
        assertEquals(0, p.entries[2].ratingCount)
        assertEquals("2026-04-19 09:00:02|4.40|500|Chef d'essai", p.entries[0].stamp)
        assertEquals("2026-04-19 09:00:02", McOnline.updatedOf(p.entries[0].stamp))
        assertEquals("2025-01-02 10:00:00", McOnline.updatedOf("2025-01-02 10:00:00"))
    }

    @Test fun selectedCataloguesKeepCommunityOptInAndOfficialFirst() {
        assertEquals(listOf(McOnline.CatalogueSource.OFFICIAL), McOnline.sources(false))
        assertEquals(listOf(McOnline.CatalogueSource.OFFICIAL, McOnline.CatalogueSource.MEMBER), McOnline.sources(true))
        // Selecting both must not change a later official-only synchronization.
        assertEquals(listOf(McOnline.CatalogueSource.OFFICIAL), McOnline.sources(false))
        val base = "${McOnline.API}/api/v2/recipes/page/2/size/100?filters%5BdeviceTypes%5D%5B%5D=MC2.0&filters%5Bsource%5D%5B%5D="
        assertEquals(base + "official", McOnline.listUrl(2))
        assertEquals(base + "official", McOnline.listUrl(2, McOnline.CatalogueSource.OFFICIAL))
        assertEquals(base + "member", McOnline.listUrl(2, McOnline.CatalogueSource.MEMBER))
    }

    @Test fun completePagesAndExplicitlyEmptyCataloguesAreAccepted() {
        val first = McOnline.parsePage(pageJson(pageData(total = 3, pageSize = 2, totalPages = 2)))
        assertEquals(3, first.total)
        assertEquals(2, first.totalPages)
        assertEquals(1, first.currentPage)
        val last = McOnline.parsePage(pageJson(pageData(total = 3, pageSize = 2, currentPage = 2,
            totalPages = 2, rows = listOf(mapOf("id" to "3", "name" to "Last")))))
        assertEquals(listOf(3L), last.entries.map { it.apiId })
        assertEquals(2, last.currentPage)
        for (pages in listOf(0, 1)) {
            val empty = McOnline.parsePage(pageJson(pageData(total = 0, totalPages = pages, rows = emptyList())))
            assertEquals(0, empty.total)
            assertEquals(1, empty.totalPages)
            assertTrue(empty.entries.isEmpty())
        }
    }

    @Test fun apiErrorsAndMissingListsCannotBecomeEmptyCatalogues() {
        for (json in listOf("{}", "[]", "null", "{", pageJson(code = 1), pageJson(code = null),
            """{"code":0,"data":null}""", """{"code":0,"data":[]}""",
            pageJson(pageData().apply { remove("recipes") }),
            pageJson(pageData().apply { this["recipes"] = null }),
            pageJson(pageData().apply { this["recipes"] = mapOf("id" to 1) }))) rejectedPage(json)
    }

    @Test fun incompleteOrInconsistentPagesCannotTriggerPruning() {
        val invalid = listOf(
            pageData(total = 3), // Three advertised recipes, only two supplied.
            pageData(total = 3, pageSize = 2, currentPage = 2, totalPages = 2), // Final page must have one row.
            pageData().apply { this["resultNo"] = 1 },
            pageData().apply { this["total"] = 2.5 },
            pageData().apply { remove("total") },
            pageData().apply { remove("currentPage") },
            pageData().apply { remove("totalPage") },
            pageData().apply { remove("pageSize") },
            pageData(total = -1), pageData(pageSize = 0), pageData(pageSize = 101),
            pageData(currentPage = 0), pageData(currentPage = 2), pageData(totalPages = 2),
            pageData(total = 100001, totalPages = 1001),
            pageData(total = 0, rows = emptyList()).apply { this["totalPage"] = 2 },
        )
        for (data in invalid) rejectedPage(pageJson(data))
    }

    @Test fun malformedOrDuplicateEntriesCannotBeSilentlySkipped() {
        val invalid = listOf(
            null, "not an object", emptyMap<String, Any?>(),
            mapOf("id" to 0, "name" to "Zero"), mapOf("id" to -1, "name" to "Negative"),
            mapOf("id" to 1.5, "name" to "Fraction"), mapOf("id" to "not an ID", "name" to "Invalid"),
            mapOf("id" to 1, "name" to "   "), mapOf("id" to 1, "name" to 123),
        )
        for (row in invalid) rejectedPage(pageJson(pageData(total = 1, rows = listOf(row))))
        rejectedPage(pageJson(pageData(rows = listOf(mapOf("id" to 1, "name" to "First"), mapOf("id" to "1", "name" to "Duplicate")))))
    }

    @Test fun recipe() {
        val r = McOnline.parseRecipe(fixture("mc-online-recipe.json"))!!
        assertEquals("web-900001", r.id)
        assertEquals("Soupe d'essai", r.title)
        assertEquals("Soupes", r.category)
        assertEquals(listOf("Végétarien"), r.tags)
        assertEquals(4, r.servings)
        assertEquals(15, r.prepMinutes)
        assertEquals(40, r.totalMinutes)
        assertEquals(2, r.difficulty)
        assertEquals("https://cdn.example.test/uploads/2021/11/123essai.jpg", r.image)
        assertEquals("Astuce : servir chaud.", r.notes)
        assertEquals("Chef d'essai", r.author)
        assertEquals(4.4, r.rating, 0.001)
        assertEquals(500, r.ratingCount)
        // Le format de stockage garde l'auteur et la note.
        val back = RecipeCodec.decode(RecipeCodec.encode(r))!!
        assertEquals(r.author, back.author)
        assertEquals(r.rating, back.rating, 0.001)
        assertEquals(r.ratingCount, back.ratingCount)

        // Ingrédients : sans groupe d'abord (dans l'ordre), puis les groupes ; sans nom = ignoré.
        assertEquals(listOf("", "POUR LA GARNITURE"), r.ingredients.map { it.title })
        assertEquals(listOf("1 oignon", "200 g carottes"), r.ingredients[0].items.map { it.display })
        assertEquals("10 g persil", r.ingredients[1].items.single().display)

        val s = r.steps
        assertEquals(9, s.size)
        // Le réglage « Mode » est rattaché à l'étape qui décrit le geste.
        assertEquals("Mettre l'oignon dans le bol mixeur.", s[0].text)
        assertEquals(StepProgram.MANUAL, s[0].machine!!.program)
        assertEquals(5, s[0].machine!!.seconds)
        assertEquals(5, s[0].machine!!.speed)
        assertTrue(s[0].needsReview)
        // Balance.
        assertEquals(200, s[1].weigh!!.grams)
        assertNull(s[1].machine)
        // Réglage après une pesée : étape à part, avec chauffe et sens inverse.
        with(s[2].machine!!) {
            assertEquals(100, tempC); assertEquals(1, speed); assertEquals(1200, seconds); assertTrue(reverse)
        }
        assertEquals(StepProgram.TURBO, s[3].machine!!.program)
        assertEquals(2, s[3].machine!!.pulses)
        assertEquals(StepProgram.STEAM, s[4].machine!!.program)
        assertEquals(600, s[4].machine!!.seconds)
        assertEquals(StepProgram.TOAST, s[5].machine!!.program)
        assertEquals(StepProgram.KNEAD, s[6].machine!!.program)
        // Programme absent du Monsieur Cuisine connect : étape manuelle, signalée.
        assertNull(s[7].machine)
        assertTrue(s[7].text.startsWith("Cuire sous vide.\n"))
        assertEquals("Servir.", s[8].text)

        // Tous les réglages importés passent les règles de sécurité.
        for (step in s) step.machine?.let { assertTrue("${it.summary} : ${it.violations()}", it.valid) }
    }

    @Test fun reviews() {
        val p = McOnline.parseReviews(fixture("mc-online-reviews.json"))
        assertEquals(13, p.total)
        assertEquals(5, p.totalPages)
        // Les avis signalés ne sont pas affichés.
        assertEquals(2, p.reviews.size)
        with(p.reviews[0]) {
            assertEquals("Cuisinier d'essai", author); assertEquals(5, rating); assertEquals("2025-12-02", date)
            assertEquals("Très bonne recette, toute la famille a aimé.", message); assertEquals("", reply)
        }
        assertEquals("Bonjour,\nMerci, nous corrigeons la recette.\n\nMeilleures salutations", p.reviews[1].reply)
        assertEquals(900001L, McOnline.apiIdOf("web-900001"))
        assertNull(McOnline.apiIdOf("mc-26946"))
        assertTrue(McOnline.reviewsUrl(900001, 2).endsWith("/api/v1/recipe-reviews/recipes/900001/reviews?page=2&size=10"))
        assertEquals(36, McOnline.reviewHeaders("fr-FR")["X-Request-Id"]!!.length)
        val updated = McOnline.withScore(McOnline.parseRecipe(fixture("mc-online-recipe.json"))!!, McOnline.Entry(900001, "", "", "", 4.6, 510))
        assertEquals(4.6, updated.rating, 0.001); assertEquals(510, updated.ratingCount); assertEquals("Chef d'essai", updated.author)
    }

    @Test fun unusable() {
        assertNull(McOnline.parseRecipe("""{"data":{"recipe":{"id":1,"name":"  "}}}"""))
        assertNull(McOnline.parseRecipe("""{"code":404,"message":"Recipe not found"}"""))
        assertNotNull(McOnline.parseRecipe("""{"data":{"recipe":{"id":"2","name":"Vide"}}}"""))
    }

    @Test fun headersAndUrls() {
        assertEquals("web", McOnline.headers("fr-FR")["device-type"])
        assertEquals("fr-FR", McOnline.languageFor("fr"))
        assertEquals("en-US", McOnline.languageFor("en"))
        assertEquals("de-DE", McOnline.languageFor("de"))
        assertTrue(McOnline.listUrl(2).contains("/page/2/size/100?"))
        assertTrue(McOnline.listUrl(1).contains("MC2.0"))
    }
}
