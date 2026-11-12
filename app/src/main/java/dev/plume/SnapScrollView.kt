package dev.plume

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.Gravity
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.HorizontalScrollView

/**
 * Authentic tvOS Smooth Centered ScrollView:
 * 1. When an item gains focus, it smoothly centers in the visible TV viewport.
 * 2. When the row fits within the screen, it is centered cleanly.
 * 3. When the row exceeds the screen, layout starts at 0 (Gravity.START) and scrolls smoothly
 *    so items on the right never disappear or get clipped off-screen.
 */
class SnapScrollView(context: Context, attrs: AttributeSet?) : HorizontalScrollView(context, attrs) {
    init {
        outlineProvider = ViewOutlineProvider.BOUNDS
        clipToOutline = false
        clipChildren = false
        clipToPadding = false
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val child = if (childCount > 0) getChildAt(0) else null
        if (child != null) {
            val avail = (r - l) - paddingLeft - paddingRight
            val lp = child.layoutParams as? FrameLayout.LayoutParams
            if (child.measuredWidth > avail) {
                if (lp != null && lp.gravity != (Gravity.START or Gravity.TOP)) {
                    lp.gravity = Gravity.START or Gravity.TOP
                    child.layoutParams = lp
                }
            } else {
                if (lp != null && lp.gravity != (Gravity.CENTER_HORIZONTAL or Gravity.TOP)) {
                    lp.gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
                    child.layoutParams = lp
                }
            }
        }
        super.onLayout(changed, l, t, r, b)
    }

    override fun computeScrollDeltaToGetChildRectOnScreen(rect: Rect): Int {
        if (childCount == 0 || width == 0) return 0
        val child = getChildAt(0) ?: return 0

        val availWidth = width - paddingLeft - paddingRight
        if (availWidth <= 0) return 0

        if (child.width <= availWidth) {
            return -scrollX
        }

        val viewportCenter = paddingLeft + availWidth / 2
        val childCenter = (rect.left + rect.right) / 2
        val targetScrollX = childCenter - viewportCenter

        val maxScroll = maxOf(0, child.width - availWidth)
        val clampedTarget = targetScrollX.coerceIn(0, maxScroll)
        return clampedTarget - scrollX
    }
}
