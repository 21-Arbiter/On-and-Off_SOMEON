package com.drivemate.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.Log
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.drivemate.core.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.tan

data class Tile(val z: Int, val x: Int, val y: Int)

private fun worldX(lon: Double) = (lon + 180) / 360
private fun worldY(lat: Double) = Math.toRadians(lat).let { (1 - ln(tan(it) + 1 / cos(it)) / PI) / 2 }

/**
 * What a map canvas shows, in Web Mercator ("world" coordinates 0..1 on both axes):
 * [scale] pixels per world, the canvas's top left at [left], [top], tiles from [zoom].
 */
data class MapView(val zoom: Int, val scale: Double, val left: Double, val top: Double, val width: Int, val height: Int) {
    fun at(g: GeoPoint) = Offset(((worldX(g.lon) - left) * scale).toFloat(), ((worldY(g.lat) - top) * scale).toFloat())

    /** The tiles that cover the canvas. */
    val tiles: List<Tile>
        get() {
            val n = 1 shl zoom
            fun range(from: Double, pixels: Int) = floor(from * n).toInt().coerceIn(0, n - 1)..floor((from + pixels / scale) * n).toInt().coerceIn(0, n - 1)
            return range(top, height).flatMap { y -> range(left, width).map { x -> Tile(zoom, x, y) } }
        }

    /** Where [tile] goes on the canvas; neighbours share their edges, so no seams. */
    fun place(tile: Tile): Pair<IntOffset, IntSize> {
        val n = (1 shl zoom).toDouble()
        fun px(world: Double, origin: Double) = ((world - origin) * scale).roundToInt()
        val x0 = px(tile.x / n, left)
        val y0 = px(tile.y / n, top)
        return IntOffset(x0, y0) to IntSize(px((tile.x + 1) / n, left) - x0, px((tile.y + 1) / n, top) - y0)
    }

    companion object {
        /**
         * The view that fits [points] with [padX], [padY] pixels around them, its tiles drawn
         * about [tilePx] pixels wide (so the map's own labels stay readable), never closer than [maxZoom].
         */
        fun fit(points: List<GeoPoint>, width: Int, height: Int, padX: Float, padY: Float, tilePx: Float, maxZoom: Int = 15): MapView {
            val xs = points.map { worldX(it.lon) }
            val ys = points.map { worldY(it.lat) }
            val spanX = (xs.max() - xs.min()).coerceAtLeast(1e-9)
            val spanY = (ys.max() - ys.min()).coerceAtLeast(1e-9)
            val scale = minOf((width - 2 * padX) / spanX, (height - 2 * padY) / spanY, tilePx * 2.0.pow(maxZoom))
            val zoom = log2(scale / tilePx).roundToInt().coerceIn(2, 18)
            val cx = (xs.min() + xs.max()) / 2
            val cy = (ys.min() + ys.max()) / 2
            return MapView(zoom, scale, cx - width / 2 / scale, cy - height / 2 / scale, width, height)
        }
    }
}

/**
 * OpenStreetMap's standard tiles. As its tile policy asks: the app names itself, downloads
 * two at a time, keeps tiles on disk for a week, and every map credits OpenStreetMap.
 * Tiles the app carries (assets/tiles: the tablet build has the demo's) count as kept. Offline,
 * or when a tile never comes, a coarser one kept for the same ground stands in, blown up:
 * a blurred map beats a blank one.
 */
object OsmTiles {
    private const val TAG = "OsmTiles"
    private const val USER_AGENT = "SOMEON/0.1 (Pleos Connect car app; com.drivemate.app)"
    private const val KEEP_MS = 7L * 24 * 60 * 60 * 1000
    /** After a download fails for want of a network, the next ones wait this long, so the stand-ins come at once. */
    private const val OFFLINE_MS = 30_000L
    /** How many zoom levels up a stand-in may come from (one: a quarter of the coarser tile). */
    private const val STAND_IN_LEVELS = 4
    private val memory = LruCache<Tile, ImageBitmap>(48)
    private val downloads = Semaphore(2)
    @Volatile private var offlineUntil = 0L

    suspend fun load(context: Context, tile: Tile): ImageBitmap? {
        memory[tile]?.let { return it }
        return withContext(Dispatchers.IO) {
            val file = file(context, tile)
            val bytes = file.takeIf { it.exists() && System.currentTimeMillis() - it.lastModified() < KEEP_MS }?.readBytes()
                ?: carried(context, tile)
                ?: downloads.withPermit { if (online()) download(tile) else null }?.also { file.parentFile?.mkdirs(); file.writeBytes(it) }
                ?: file.takeIf { it.exists() }?.readBytes() // offline, an old tile beats none
            bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }?.also { memory.put(tile, it) }
                ?: standIn(context, tile) // not kept in memory: the real tile may come next time
        }
    }

    private fun file(context: Context, tile: Tile) = File(context.cacheDir, "tiles/${tile.z}/${tile.x}/${tile.y}.png")

    private fun carried(context: Context, tile: Tile): ByteArray? =
        runCatching { context.assets.open("tiles/${tile.z}/${tile.x}/${tile.y}.png").use { it.readBytes() } }.getOrNull()

    /** The part of a coarser tile (kept on disk or carried) that covers [tile], scaled up to a tile's size. */
    private fun standIn(context: Context, tile: Tile): ImageBitmap? {
        for (up in 1..minOf(STAND_IN_LEVELS, tile.z)) {
            val parent = Tile(tile.z - up, tile.x shr up, tile.y shr up)
            val bytes = file(context, parent).takeIf { it.exists() }?.readBytes() ?: carried(context, parent) ?: continue
            val image = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: continue
            val part = image.width shr up
            val left = (tile.x - (parent.x shl up)) * part
            val top = (tile.y - (parent.y shl up)) * part
            return Bitmap.createScaledBitmap(Bitmap.createBitmap(image, left, top, part, part), image.width, image.height, true).asImageBitmap()
        }
        return null
    }

    private fun online() = SystemClock.elapsedRealtime() >= offlineUntil

    private fun download(tile: Tile): ByteArray? = try {
        val connection = URL("https://tile.openstreetmap.org/${tile.z}/${tile.x}/${tile.y}.png").openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        try {
            if (connection.responseCode == 200) connection.inputStream.use { it.readBytes() } else null
        } finally {
            connection.disconnect()
        }
    } catch (e: IOException) {
        Log.w(TAG, "Tile $tile failed: $e")
        offlineUntil = SystemClock.elapsedRealtime() + OFFLINE_MS
        null
    }
}

/** The tiles of [view] loaded so far; the map fills in as they arrive. */
@Composable
fun rememberMapTiles(view: MapView): Map<Tile, ImageBitmap> {
    val context = LocalContext.current.applicationContext
    val loaded = remember { mutableStateMapOf<Tile, ImageBitmap>() }
    LaunchedEffect(view) {
        view.tiles.filter { it !in loaded }.forEach { tile -> launch { OsmTiles.load(context, tile)?.let { loaded[tile] = it } } }
    }
    return loaded
}

/** By day the map at 70% saturation and washed a little toward white, so the two drives stand out. */
val DayMapFilter = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            0.6722f, 0.1888f, 0.0190f, 0f, 30.6f,
            0.0562f, 0.8048f, 0.0190f, 0f, 30.6f,
            0.0562f, 0.1888f, 0.6350f, 0f, 30.6f,
            0f, 0f, 0f, 1f, 0f,
        ),
    ),
)

/** At night inverted with its hues turned back (invert, then rotate 180°), less saturated, dimmed and a little blue. */
val NightMapFilter = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            0.0835f, -0.8208f, -0.0827f, 0f, 215.1f,
            -0.2445f, -0.4928f, -0.0827f, 0f, 217.1f,
            -0.2445f, -0.8208f, 0.2453f, 0f, 225.1f,
            0f, 0f, 0f, 1f, 0f,
        ),
    ),
)
