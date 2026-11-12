package dev.plume

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Google TV's launcher has to stay enabled (disabling it boot-loops the TV), so we take the Home key before
 * the system sees it and open our home directly; Google TV never flashes up. Holding Home still opens the
 * Google TV dashboard. Covering Google TV whenever it shows stays as the fallback for anything else that
 * opens it (e.g. Back out of the last app, or TV waking up from standby).
 */
class HomeOverride : AccessibilityService() {
    private var screenReceiver: BroadcastReceiver? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        running = this
        getSharedPreferences("home", MODE_PRIVATE).edit().remove("home_off").apply()
        applyKeyFilter()
        registerScreenReceiver()
    }

    private fun registerScreenReceiver() {
        if (screenReceiver != null) return
        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_SCREEN_ON) {
                    allowGoogleTv = false
                    handler.postDelayed({
                        val topPkg = rootInActiveWindow?.packageName?.toString()
                        if (topPkg == GOOGLE_TV || topPkg == SETUP_WRAITH) {
                            goHome()
                        }
                    }, 350)
                }
            }
        }
        val filter = IntentFilter(Intent.ACTION_SCREEN_ON)
        registerReceiver(screenReceiver, filter)
    }

    private fun unregisterScreenReceiver() {
        screenReceiver?.let {
            try { unregisterReceiver(it) } catch (_: Throwable) {}
            screenReceiver = null
        }
    }

    /**
     * Seeing keys costs volume its hold on TVs: Android acts on Volume before any service sees it and the key never
     * repeats. So Smooth volume turns key filtering off, unless remote keys need it (set, or one being learned).
     * Home is then caught by the Google TV window watcher below, which shows Google TV for about a second.
     */
    fun applyKeyFilter() {
        val info = serviceInfo ?: return
        val filter = !getSharedPreferences("home", MODE_PRIVATE).getBoolean("smooth_volume", false) ||
            Remap.all(this).isNotEmpty() || Remap.learn != null
        val flag = AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        serviceInfo = info.apply { flags = if (filter) flags or flag else flags and flag.inv() }
    }

    /**
     * Disconnected by system or app update: do NOT set home_off here, as Android unbinds services
     * during app restarts, updates, and sleep cycles. home_off is only set when explicitly toggled off in settings.
     */
    override fun onUnbind(intent: Intent?): Boolean {
        running = null
        unregisterScreenReceiver()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterScreenReceiver()
    }

    override fun onKeyEvent(e: KeyEvent): Boolean {
        if (e.keyCode != KeyEvent.KEYCODE_HOME) return remap(e)
        // Decided on release, by how long it was held: this TV's remote doesn't reliably send key repeats.
        if (e.action == KeyEvent.ACTION_UP && !e.isCanceled) {
            allowGoogleTv = false
            if (e.eventTime - e.downTime < LONG_PRESS_MS) goHome() else performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
        }
        return true
    }

    /** Keys the user changed in Plume settings (see [Remap]); any other key goes on to the system untouched. */
    private fun remap(e: KeyEvent): Boolean {
        if (Remap.kept(e)) return false
        val id = Remap.id(e)
        if (id == Remap.pending) { // the key just learned: its repeats and release never reach anyone
            if (e.action == KeyEvent.ACTION_UP) Remap.pending = null
            return true
        }
        Remap.learn?.let { learn ->
            if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0) {
                Remap.pending = id
                learn(e)
            }
            return true
        }
        val m = Remap.all(this)[id] ?: return false
        // Decided on release, like Home: this TV's remote doesn't reliably send key repeats.
        if (e.action == KeyEvent.ACTION_UP && !e.isCanceled) {
            val action = if (e.eventTime - e.downTime >= LONG_PRESS_MS && m.long.isNotEmpty()) m.long else m.short
            if (action.isNotEmpty()) Remap.run(this, action)
        }
        return true
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        if (e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = e.packageName?.toString().orEmpty()
            if (pkg == GOOGLE_TV || pkg == SETUP_WRAITH) {
                if (allowGoogleTv) return
                val cls = e.className?.toString().orEmpty()
                if (cls.contains("dashboard", ignoreCase = true) || cls.contains("SidePanel", ignoreCase = true)) return
                val isHomeScreen = cls.contains(".home.HomeActivity") ||
                                   cls.contains(".home.VanillaModeHomeActivity") ||
                                   cls.endsWith(".HomeActivity")
                if (isHomeScreen) {
                    goHome()
                }
            }
        }
    }

    // Marked as Home so the setup check can tell it from Plume being opened from the apps list.
    fun goHome() = startActivity(
        Intent(this, MainActivity::class.java)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    )

    override fun onInterrupt() {}

    companion object {
        /** The bound service, so Plume's switch can turn it off without any permission. */
        var running: HomeOverride? = null
            private set
        @Volatile var allowGoogleTv = false
        private const val GOOGLE_TV = "com.google.android.apps.tv.launcherx"
        private const val SETUP_WRAITH = "com.google.android.tungsten.setupwraith"
        private const val LONG_PRESS_MS = 500L
    }
}
