package com.yuriy.openradio.shared.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamTitleTest {

    @Test
    fun plainTextUnchanged() {
        assertEquals("Foo Fighters - My Hero", StreamTitle.clean("Foo Fighters - My Hero"))
        assertEquals("a=b", StreamTitle.clean("a=b"))
    }

    @Test
    fun titleArtistUrl() {
        assertEquals(
            "FOO FIGHTERS - My Hero",
            StreamTitle.clean(
                "title=\"My Hero\",artist=\"FOO FIGHTERS\",url=\"song_spot=\"F\" MediaBaseId=\"1234\" itunesTrackId=\"0\"\""
            )
        )
    }

    @Test
    fun titleArtistEscapedUrl() {
        assertEquals(
            "FOO FIGHTERS - My Hero",
            StreamTitle.clean(
                "title=\"My Hero\",artist=\"FOO FIGHTERS\",url=\"song_spot=\\\"F\\\" MediaBaseId=\\\"1234\\\"\""
            )
        )
    }

    @Test
    fun artistThenText() {
        assertEquals(
            "FOO FIGHTERS - My Hero",
            StreamTitle.clean(
                "FOO FIGHTERS - text=\"My Hero\" song_spot=\"M\" MediaBaseId=\"1234\" itunesTrackId=\"0\""
            )
        )
    }

    @Test
    fun titleWithDashAndQuoteInside() {
        assertEquals(
            "AC/DC - Back In Black - Live",
            StreamTitle.clean("title=\"Back In Black - Live\",artist=\"AC/DC\"")
        )
    }

    @Test
    fun onlyTitle() {
        assertEquals("Station ID", StreamTitle.clean("text=\"Station ID\" song_spot=\"T\""))
    }
}
