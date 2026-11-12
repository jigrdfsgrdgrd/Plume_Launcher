package dev.plume

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.media.MediaPlayer
import android.service.dreams.DreamService
import android.view.Gravity
import android.view.KeyEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors

/**
 * Public-domain museum scans, not bundled: downloaded on demand from the repo's `gallery-1` release (the
 * files live in screensaver/) into [paintingsDir], so the APK stays under 1 MB for people who never use it. only paintings whose canvas is (within ~2-4%) 16:9, cropped
 * to exactly 1920×1080 so they fill the screen with nothing cut off and no bars. Bright ones for light mode,
 * dusk and storm ones for dark (wide night paintings are rare).
 */
private class Painting(val file: String, val title: String, val artist: String)

/** One picture in the show: a painting with its caption, or one of your photos (no caption). */
private class Picture(val title: String, val artist: String, val load: () -> Bitmap?)

private val LIGHT = listOf(
    Painting("andes", "The Heart of the Andes", "Frederic Edwin Church, 1859"),
    Painting("icebergs", "The Icebergs", "Frederic Edwin Church, 1861"),
    Painting("rye", "Rye", "Ivan Shishkin, 1878"),
    Painting("chimborazo", "Chimborazo", "Frederic Edwin Church, 1864"),
    Painting("hudson", "Autumn on the Hudson River", "Jasper Francis Cropsey, 1860"),
)
private val DARK = listOf(
    Painting("cotopaxi", "Cotopaxi", "Frederic Edwin Church, 1862"),
    Painting("thunderstorm", "Thunder Storm on Narragansett Bay", "Martin Johnson Heade, 1868"),
)

internal const val PAINTINGS_URL = "https://github.com/aradhyas8/Plume_Launcher/releases/download/gallery-1/"
internal const val PAINTINGS_MB = "4.7 MB"
internal val PAINTING_FILES = (LIGHT + DARK).map { "${it.file}.jpg" }
internal fun paintingsDir(c: Context) = File(c.filesDir, "paintings")
internal fun paintingsReady(c: Context) = PAINTING_FILES.all { File(paintingsDir(c), it).exists() }

/**
 * The Gallery screensaver. Picked in launcher settings, which also restore the previous one when it's off.
 * Shows the paintings, or a folder of your photos when one is chosen there ("ss_folder").
 * ◀ ▶ step through the pictures; any other key leaves to the launcher's home screen.
 */
class GalleryDream : DreamService() {
    private var show: GalleryShow? = null
    private var aerialShow: AerialDreamShow? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = true
        isFullscreen = true
        val videos = findAllAerialVideos(this)
        if (videos.isNotEmpty()) {
            aerialShow = AerialDreamShow(this, videos).also { setContentView(it.root) }
        } else {
            show = GalleryShow(this).also { setContentView(it.view) }
        }
    }

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        if (e.keyCode == KeyEvent.KEYCODE_DPAD_LEFT || e.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (e.action == KeyEvent.ACTION_DOWN) {
                val dir = if (e.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) 1 else -1
                aerialShow?.step(dir) ?: show?.step(dir)
            }
            return true
        }
        if (e.action == KeyEvent.ACTION_UP) {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
        }
        return true
    }

    override fun onDetachedFromWindow() {
        aerialShow?.release()
        show?.release()
        super.onDetachedFromWindow()
    }
}

private fun findAllAerialVideos(c: Context): List<File> {
    val dirs = listOfNotNull(
        c.getExternalFilesDir(null)?.let { File(it, "aerials_4k") },
        File("/sdcard/Movies/aerials_4k"),
        c.getExternalFilesDir(null)?.let { File(it, "aerials") },
        File("/sdcard/Movies/aerials"),
        File("/sdcard/Android/data/dev.plume/files/aerials_4k"),
        File("/sdcard/Android/data/dev.plume/files/aerials")
    )
    val seenKeys = HashSet<String>()
    val out = ArrayList<File>()
    for (dir in dirs) {
        val files = dir.listFiles { f -> f.isFile && f.canRead() && f.name.endsWith(".mov", ignoreCase = true) } ?: continue
        for (f in files.sortedBy { it.name }) {
            val k = when {
                f.name.contains("dubai", ignoreCase = true) || f.name.contains("DB_D008", ignoreCase = true) -> "dubai"
                f.name.contains("china", ignoreCase = true) || f.name.contains("CH_C002", ignoreCase = true) -> "china"
                f.name.contains("canyon", ignoreCase = true) || f.name.contains("G008", ignoreCase = true) || f.name.contains("greenland.mov", ignoreCase = true) -> "canyon"
                f.name.contains("greenland_4k", ignoreCase = true) || f.name.contains("GL_G004", ignoreCase = true) -> "greenland_ice"
                f.name.contains("hongkong", ignoreCase = true) || f.name.contains("HK_H004", ignoreCase = true) -> "hongkong"
                f.name.contains("space", ignoreCase = true) || f.name.contains("A006_C003", ignoreCase = true) -> "space"
                else -> f.name
            }
            if (seenKeys.add(k)) {
                out.add(f)
            }
        }
    }
    return out
}

private class AerialDreamShow(private val context: Context, private val videos: List<File>) : SurfaceHolder.Callback {
    private var idx = 0
    private var player: MediaPlayer? = null
    private var holderRef: SurfaceHolder? = null
    private val d = context.resources.displayMetrics.density

    private val titleView = TextView(context).apply {
        textSize = 17f
        setTextColor(0xF5FFFFFF.toInt())
        setShadowLayer(8f * d, 0f, 2f * d, 0xCC000000.toInt())
    }
    private val subView = TextView(context).apply {
        textSize = 12.5f
        setTextColor(0xB8E6F0FF.toInt())
        setShadowLayer(6f * d, 0f, 1f * d, 0xCC000000.toInt())
    }
    private val captionBox = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        addView(titleView)
        addView(subView)
    }
    private val surfaceView = SurfaceView(context).also { it.holder.addCallback(this) }

    val root = FrameLayout(context).apply {
        setBackgroundColor(Color.BLACK)
        addView(surfaceView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        addView(
            captionBox,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.START).apply {
                marginStart = (42 * d).toInt()
                bottomMargin = (34 * d).toInt()
            }
        )
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        holderRef = holder
        playCurrent()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        holderRef = null
        releasePlayer()
    }

    fun step(by: Int) {
        if (videos.isEmpty()) return
        idx = (idx + by + videos.size) % videos.size
        playCurrent()
    }

    private fun playCurrent() {
        val h = holderRef ?: return
        val file = videos[idx]
        releasePlayer()
        val (t, s) = captionFor(file.name)
        titleView.text = t
        subView.text = s
        captionBox.animate().cancel()
        captionBox.alpha = 0f
        captionBox.animate().alpha(1f).setDuration(450).withEndAction {
            captionBox.animate().alpha(0f).setStartDelay(5000).setDuration(800).start()
        }.start()

        try {
            player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setDisplay(h)
                setVolume(0f, 0f)
                isLooping = videos.size == 1
                setOnPreparedListener { mp -> mp.start() }
                setOnCompletionListener { step(1) }
                setOnErrorListener { _, _, _ ->
                    if (videos.size > 1) step(1)
                    true
                }
                prepareAsync()
            }
        } catch (_: Exception) {}
    }

    private fun captionFor(name: String): Pair<String, String> = when {
        name.contains("dubai", ignoreCase = true) || name.contains("DB_D008", ignoreCase = true) ->
            "Dubai · Vereinigte Arabische Emirate" to "Sheikh Zayed Road · Apple TV 4K Aerial"
        name.contains("china", ignoreCase = true) || name.contains("CH_C002", ignoreCase = true) ->
            "Longji · China" to "Reisterrassen im Morgennebel · Apple TV 4K Aerial"
        name.contains("canyon", ignoreCase = true) || name.contains("G008", ignoreCase = true) || name.contains("greenland.mov", ignoreCase = true) ->
            "Grand Canyon · USA" to "Colorado River · Apple TV 4K Aerial"
        name.contains("greenland_4k", ignoreCase = true) || name.contains("GL_G004", ignoreCase = true) ->
            "Ilulissat · Grönland" to "Eisberge im Nordpolarmeer · Apple TV 4K Aerial"
        name.contains("hongkong", ignoreCase = true) || name.contains("HK_H004", ignoreCase = true) ->
            "Victoria Harbour · Hongkong" to "Skyline bei Nacht · Apple TV 4K Aerial"
        name.contains("space", ignoreCase = true) || name.contains("A006_C003", ignoreCase = true) ->
            "Erde aus dem Orbit · ISS" to "Karibik & Atmosphäre · Apple TV 4K Aerial"
        else -> "Apple TV Aerial" to "4K UHD Live-Video"
    }

    private fun releasePlayer() {
        try { player?.stop() } catch (_: Exception) {}
        try { player?.release() } catch (_: Exception) {}
        player = null
    }

    fun release() {
        captionBox.animate().cancel()
        releasePlayer()
    }
}

/**
 * Paints edge to edge on a SurfaceView at the UI's 1080p. Not 4K: this TV's display hardware shows a 4K app
 * surface as black (screenshots still show it, since they composite on the GPU).
 * Each painting fills the screen exactly, holds, then cross-fades to the next. The title shows for a few seconds per painting.
 * About 6 MB per decoded painting; one in memory, two during a cross-fade, plus the surface's buffers.
 * Outgoing paintings are recycled as soon as they're off screen, and everything when the dream ends.
 */
private class GalleryShow(private val context: Context) : SurfaceHolder.Callback {
    private val paintings = pictures()
    private val io = Executors.newSingleThreadExecutor()
    val view = SurfaceView(context).also { it.holder.addCallback(this) }

    /** A folder's photos in shuffled order if one is chosen and still has photos, else the theme's paintings. */
    private fun pictures(): List<Picture> {
        val prefs = context.getSharedPreferences("home", Context.MODE_PRIVATE)
        val photos = try {
            prefs.getString("ss_folder", null)?.let { folderPhotos(context.contentResolver, it) }.orEmpty()
        } catch (e: Exception) { // photo access taken away
            emptyList()
        }
        if (photos.isNotEmpty()) return photos.shuffled().map { id ->
            val uri = ContentUris.withAppendedId(PHOTOS, id)
            Picture("", "") { fitWallpaper { context.contentResolver.openInputStream(uri) ?: throw IOException("gone: $uri") } }
        }
        return (if (prefersDark(prefs)) DARK else LIGHT).map { p ->
            Picture(p.title, p.artist) { File(paintingsDir(context), "${p.file}.jpg").inputStream().use { BitmapFactory.decodeStream(it) } }
        }
    }

    private class Slide(val bmp: Bitmap, val painting: Picture, val start: Long, val fadeMs: Long)

    // Shared between the loader and the render thread; swaps and draws hold [lock], so a bitmap is
    // never recycled mid-draw.
    private val lock = Any()
    @Volatile private var current: Slide? = null
    @Volatile private var previous: Slide? = null
    @Volatile private var running = false
    private var index = paintings.indices.random()
    @Volatile private var advanceAt = 0L
    private var render: Thread? = null

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; setShadowLayer(12f, 0f, 2f, 0x99000000.toInt()) }
    private val artist = Paint(title)

    override fun surfaceCreated(holder: SurfaceHolder) {
        running = true
        load(index)
        render = Thread(::loop, "gallery").apply { start() }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        running = false
        render?.join(500)
    }

    fun step(by: Int) {
        index = (index + by + paintings.size) % paintings.size
        load(index)
    }

    fun release() {
        io.shutdownNow()
        synchronized(lock) {
            running = false
            current?.bmp?.recycle()
            previous?.bmp?.recycle()
            current = null
            previous = null
        }
    }

    private fun load(i: Int) {
        val p = paintings[i]
        io.execute {
            val bmp = try {
                p.load()
            } catch (e: Exception) {
                null
            } ?: return@execute
            synchronized(lock) {
                if (!running) { // dream ended while decoding
                    bmp.recycle()
                    return@execute
                }
                previous?.bmp?.recycle() // stepped again mid-fade: that one won't be drawn any more
                // The first painting fades up from black: keep that short or the screen just looks off.
                val fade = if (current == null) FIRST_FADE_MS else FADE_MS
                previous = current
                current = Slide(bmp, p, System.currentTimeMillis(), fade)
                advanceAt = System.currentTimeMillis() + HOLD_MS
            }
        }
    }

    private fun loop() {
        while (running) {
            val now = System.currentTimeMillis()
            if (advanceAt in 1..now) {
                advanceAt = 0
                step(1)
            }
            val holder = view.holder
            val canvas = try {
                holder.lockHardwareCanvas()
            } catch (e: Exception) {
                null
            } ?: return
            try {
                draw(canvas, now)
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }
            Thread.sleep(FRAME_MS) // 30 fps is plenty for motion this slow, and spares the GPU
        }
    }

    private fun draw(c: Canvas, now: Long) = synchronized(lock) {
        c.drawColor(Color.BLACK)
        if (!running) return
        val cur = current ?: return
        val fade = ((now - cur.start).toFloat() / cur.fadeMs).coerceIn(0f, 1f)
        previous?.let {
            if (fade < 1f) drawSlide(c, it, ((1f - fade) * 255).toInt()) else {
                it.bmp.recycle() // faded out: free it now rather than whenever GC gets to it
                previous = null
            }
        }
        drawSlide(c, cur, (fade * 255).toInt())

        // Title for the first seconds of each painting, fading in and out.
        val t = now - cur.start
        val a = when {
            t < FADE_MS -> t.toFloat() / FADE_MS
            t < CAPTION_MS -> 1f
            t < CAPTION_MS + FADE_MS -> 1f - (t - CAPTION_MS).toFloat() / FADE_MS
            else -> 0f
        }
        if (a > 0f && cur.painting.title.isNotEmpty()) {
            val u = c.height / 1080f // design in 1080p units, draw at panel resolution
            title.textSize = 30f * u
            artist.textSize = 22f * u
            title.alpha = (a * 235).toInt()
            artist.alpha = (a * 190).toInt()
            c.drawText(cur.painting.title, 96f * u, c.height - 96f * u, title)
            c.drawText(cur.painting.artist, 96f * u, c.height - 60f * u, artist)
        }
    }

    /** The paintings are already the screen's shape, so each one simply covers the whole surface. */
    private fun drawSlide(c: Canvas, s: Slide, alpha: Int) {
        paint.alpha = alpha
        c.drawBitmap(s.bmp, null, RectF(0f, 0f, c.width.toFloat(), c.height.toFloat()), paint)
    }

    private companion object {
        const val HOLD_MS = 2 * 60_000L
        const val FADE_MS = 2_500L
        const val FIRST_FADE_MS = 600L
        const val CAPTION_MS = 8_000L
        const val FRAME_MS = 33L
    }
}
