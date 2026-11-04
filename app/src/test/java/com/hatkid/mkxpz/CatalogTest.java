package com.hatkid.mkxpz;

import org.junit.Test;
import static org.junit.Assert.*;

public class CatalogTest
{
    @Test public void oldCachedOrOnlineCatalogCannotReplaceBundledMedia() throws Exception {
        assertNull(Catalog.parseCurrent("{\"version\":1,\"games\":[{\"title\":\"Old catalog\"}]}"));
        assertNull(Catalog.parseCurrent("{\"version\":2,\"games\":[{\"title\":\"Previous media catalog\"}]}"));
        assertEquals("New catalog", Catalog.parseCurrent("{\"version\":3,\"games\":[{\"title\":\"New catalog\"}]}")
            .get(0).title);
    }

    @Test public void oldEntriesStillParseWithoutCommunityFields() throws Exception {
        Catalog.Game game = Catalog.parse("{\"games\":[{\"title\":\"Legacy game\",\"lang\":[\"es\"]}]}").get(0);
        assertTrue(game.spanish());
        assertEquals(Catalog.UNTESTED, game.status);
        assertEquals("", game.logo);
        assertEquals("", game.sourcePage);
        assertTrue(game.screens.isEmpty());
    }

    @Test public void sourceIdentityComesFromTheExactHttpsHost() {
        assertSame(Catalog.SOURCES[0], Catalog.sourceFor("https://whackahack.com/juegos/pokemon-z/"));
        assertSame(Catalog.SOURCES[1], Catalog.sourceFor("https://www.eeveeexpo.com/threads/9100/"));
        assertNull(Catalog.sourceFor("https://whackahack.com.example.com/juegos/"));
        assertNull(Catalog.sourceFor("https://whackahack.com@other.example/game"));
        assertNull(Catalog.sourceFor("https://other.example/whackahack.com"));
        assertNull(Catalog.sourceFor("http://whackahack.com/juegos/"));
        assertNull(Catalog.sourceFor("https://eeveeexpo.com:8080/threads/9100/"));
    }

    @Test public void externalLinksCannotOpenLocalFilesOrAppIntents() {
        for (String link : new String[] {"", "not a url", "file:///sdcard/save", "content://photos/1",
                "javascript:alert(1)", "intent://game", "https:///missing-host", "https://user:secret@example.com/a"}) {
            assertEquals(link, "", Catalog.webUrl(link));
        }
        assertEquals("https://www.youtube.com/watch?v=5K16xLX06jQ",
            Catalog.webUrl(" https://www.youtube.com/watch?v=5K16xLX06jQ "));
    }

    @Test public void mediaAreOptionalAndInvalidUrlsAreDiscarded() throws Exception {
        String json = "{\"games\":[{\"title\":\"Example\","
            + "\"source_page\":\"https://eeveeexpo.com/threads/9100/\","
            + "\"logo\":\"https://images.example/logo.png\",\"cover\":\"file:///sdcard/secret\","
            + "\"trailer\":\"javascript:bad\",\"screens\":[\"https://images.example/one.png\",\"intent://bad\"]}]}";
        Catalog.Game game = Catalog.parse(json).get(0);
        assertSame(Catalog.SOURCES[1], Catalog.sourceFor(game.sourcePage));
        assertEquals("https://images.example/logo.png", game.logo);
        assertEquals("", game.cover);
        assertEquals("", game.trailer);
        assertEquals(1, game.screens.size());
    }

    @Test public void unknownSourcesAreNotPresentedAsCommunityListings() throws Exception {
        Catalog.Game game = Catalog.parse("{\"games\":[{\"title\":\"Example\","
            + "\"source_page\":\"https://unrelated.example/\"}]}").get(0);
        assertEquals("", game.sourcePage);
    }

    @Test public void datedCommunityPopularityDoesNotChangeCompatibility() throws Exception {
        org.json.JSONObject game = new org.json.JSONObject();
        game.put("title", "Community favourite");
        game.put("source_page", "https://eeveeexpo.com/infinity/");
        game.put("community_rank", 1);
        game.put("popularity_page", Catalog.POPULARITY_URL);
        game.put("popularity_checked", "2026-11-04");
        Catalog.Game parsed = Catalog.parse("{\"games\":[" + game + "]}").get(0);
        assertEquals(1, parsed.communityRank);
        assertEquals("2026-11-04", parsed.popularityChecked);
        assertFalse(parsed.tested());
        assertEquals(Catalog.UNTESTED, parsed.status);
        game.put("popularity_page", "https://unrelated.example/ranking");
        assertEquals(0, Catalog.parse("{\"games\":[" + game + "]}").get(0).communityRank);
        game.put("popularity_page", Catalog.POPULARITY_URL);
        game.put("source_page", "https://whackahack.com/juegos/example/");
        assertEquals(0, Catalog.parse("{\"games\":[" + game + "]}").get(0).communityRank);
    }

    @Test public void creatorMediaSourcesUseWebLinksWithoutBecomingCommunities() throws Exception {
        Catalog.Game game = Catalog.parse("{\"games\":[{\"title\":\"Creator game\","
            + "\"media_page\":\"https://creator.example/gallery\"}]}").get(0);
        assertEquals("https://creator.example/gallery", game.mediaPage);
        assertEquals("", game.sourcePage);
        assertEquals(0, game.communityRank);
        game = Catalog.parse("{\"games\":[{\"title\":\"Unsafe\",\"media_page\":\"intent://bad\"}]}").get(0);
        assertEquals("", game.mediaPage);
    }
}
