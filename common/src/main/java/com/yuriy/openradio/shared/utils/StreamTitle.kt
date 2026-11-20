package com.yuriy.openradio.shared.utils

/**
 * Turns the "now playing" text some stations send as tagged fields into "Artist - Title".
 *
 * iHeart and similar servers put key="value" pairs into the ICY title instead of plain text:
 * - `title="My Hero",artist="FOO FIGHTERS",url="song_spot=\"F\" MediaBaseId=\"123\""`
 * - `FOO FIGHTERS - text="My Hero" song_spot="M" MediaBaseId="123" itunesTrackId="0"`
 *
 * Plain text comes back unchanged.
 */
object StreamTitle {

    /**
     * A value ends at a quote followed by the next key, a comma or the end.
     */
    private fun field(name: String) =
        Regex("""(?:^|[\s,;])$name="(.*?)"(?=\s*$|\s*[,;]|\s+[\w-]+=)""", RegexOption.IGNORE_CASE)

    private val TITLE = field("title")
    private val TEXT = field("text")
    private val ARTIST = field("artist")

    /**
     * "Artist - text=..." : the artist is the plain text before the first field.
     */
    private val LEADING_ARTIST = Regex("""^(.*?)\s*-\s*text=""", RegexOption.IGNORE_CASE)

    fun clean(raw: String): String {
        val value = raw.trim()
        if (!value.contains("=\"")) {
            return value
        }
        val title = (TITLE.find(value) ?: TEXT.find(value))?.groupValues?.get(1)?.trim().orEmpty()
        if (title.isEmpty()) {
            return value
        }
        val artist = (ARTIST.find(value)?.groupValues?.get(1)
            ?: LEADING_ARTIST.find(value)?.groupValues?.get(1))?.trim().orEmpty()
        return if (artist.isEmpty()) title else "$artist - $title"
    }
}
