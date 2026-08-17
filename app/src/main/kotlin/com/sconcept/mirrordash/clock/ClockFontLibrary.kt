package com.sconcept.mirrordash.clock

import android.content.Context
import android.graphics.Typeface
import kotlinx.serialization.Serializable
import java.io.File

const val CLOCK_FONT_SYSTEM_DEFAULT = "system_default"
const val CLOCK_FONT_SYSTEM_SANS = "system_sans"
const val CLOCK_FONT_SYSTEM_SERIF = "system_serif"
const val CLOCK_FONT_SYSTEM_MONO = "system_mono"
const val CLOCK_FONT_SYSTEM_CURSIVE = "system_cursive"
const val CLOCK_FONT_GOOGLE_PREFIX = "google:"

@Serializable
data class GoogleFontCatalogEntry(
    val id: String,
    val family: String,
    val category: String,
    val license: String,
    val subsets: List<String>,
    val styleCount: Int,
    val preferredPath: String,
)

@Serializable
data class DownloadedClockFont(
    val id: String,
    val family: String,
    val category: String,
    val license: String,
    val localPath: String,
    val sourcePath: String,
    val downloadedAtEpochMs: Long,
)

data class ClockFontOption(
    val id: String,
    val label: String,
)

data class BundledClockFontAsset(
    val id: String,
    val family: String,
    val category: String,
    val license: String,
    val assetPath: String,
)

object ClockFontLibrary {
    val starterPack = listOf(
        BundledClockFontAsset("google:worksans", "Work Sans", "SANS_SERIF", "OFL", "clock-fonts/worksans.ttf"),
        BundledClockFontAsset("google:josefinsans", "Josefin Sans", "SANS_SERIF", "OFL", "clock-fonts/josefinsans.ttf"),
        BundledClockFontAsset("google:boogaloo", "Boogaloo", "DISPLAY", "OFL", "clock-fonts/boogaloo.ttf"),
        BundledClockFontAsset("google:indieflower", "Indie Flower", "HANDWRITING", "OFL", "clock-fonts/indieflower.ttf"),
        BundledClockFontAsset("google:macondo", "Macondo", "DISPLAY", "OFL", "clock-fonts/macondo.ttf"),
        BundledClockFontAsset("google:robotomono", "Roboto Mono", "MONOSPACE", "Apache 2", "clock-fonts/robotomono.ttf"),
        BundledClockFontAsset("google:shizuru", "Shizuru", "DISPLAY", "OFL", "clock-fonts/shizuru.ttf"),
        BundledClockFontAsset("google:lexend", "Lexend", "SANS_SERIF", "OFL", "clock-fonts/lexend.ttf"),
        BundledClockFontAsset("google:righteous", "Righteous", "DISPLAY", "OFL", "clock-fonts/righteous.ttf"),
        BundledClockFontAsset("google:sacramento", "Sacramento", "HANDWRITING", "OFL", "clock-fonts/sacramento.ttf"),
        BundledClockFontAsset("google:gaegu", "Gaegu", "HANDWRITING", "OFL", "clock-fonts/gaegu.ttf"),
        BundledClockFontAsset("google:bebasneue", "Bebas Neue", "SANS_SERIF", "OFL", "clock-fonts/bebasneue.ttf"),
        BundledClockFontAsset("google:nunito", "Nunito", "SANS_SERIF", "OFL", "clock-fonts/nunito.ttf"),
        BundledClockFontAsset("google:manrope", "Manrope", "SANS_SERIF", "OFL", "clock-fonts/manrope.ttf"),
        BundledClockFontAsset("google:poiretone", "Poiret One", "DISPLAY", "OFL", "clock-fonts/poiretone.ttf"),
    )

    fun isGoogleFont(id: String): Boolean = id.startsWith(CLOCK_FONT_GOOGLE_PREFIX)

    fun label(id: String, downloadedFonts: List<DownloadedClockFont>): String {
        starterPack.firstOrNull { it.id == id }?.let { return it.family }
        when (id) {
            CLOCK_FONT_SYSTEM_DEFAULT -> return "System default"
            CLOCK_FONT_SYSTEM_SANS -> return "Sans-serif"
            CLOCK_FONT_SYSTEM_SERIF -> return "Serif"
            CLOCK_FONT_SYSTEM_MONO -> return "Monospace"
            CLOCK_FONT_SYSTEM_CURSIVE -> return "Cursive"
        }
        return downloadedFonts.firstOrNull { it.id == id }?.family ?: "Custom font"
    }

    fun typeface(context: Context, id: String, downloadedFonts: List<DownloadedClockFont>): Typeface {
        return when (id) {
            CLOCK_FONT_SYSTEM_SANS -> Typeface.SANS_SERIF
            CLOCK_FONT_SYSTEM_SERIF -> Typeface.SERIF
            CLOCK_FONT_SYSTEM_MONO -> Typeface.MONOSPACE
            CLOCK_FONT_SYSTEM_CURSIVE -> Typeface.create("cursive", Typeface.NORMAL) ?: Typeface.DEFAULT
            CLOCK_FONT_SYSTEM_DEFAULT -> Typeface.DEFAULT
            else -> {
                val localPath = downloadedFonts.firstOrNull { it.id == id }?.localPath
                val file = localPath?.let(::File)
                if (file != null && file.exists()) {
                    runCatching { Typeface.createFromFile(file) }.getOrElse { Typeface.DEFAULT }
                } else {
                    val bundled = starterPack.firstOrNull { it.id == id }
                    if (bundled != null) {
                        runCatching { Typeface.createFromAsset(context.assets, bundled.assetPath) }.getOrElse { Typeface.DEFAULT }
                    } else {
                        Typeface.DEFAULT
                    }
                }
            }
        }
    }
}
