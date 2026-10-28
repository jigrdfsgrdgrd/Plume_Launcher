package dev.plume

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.widget.TextClock

/**
 * Custom TextClock supporting both standard Android text rendering (for fallback presets)
 * and authentic Apple Lock Screen typography with diffuse ambient elevation shadow,
 * subtle specular edge rim, and luminous gradient fill that adapts to the wallpaper.
 */
class GlassClock @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : TextClock(ctx, attrs) {

    /** When false, delegates entirely to super.onDraw() (pure standard TextClock). */
    var isAppleLockScreen = false

    /** Luminous frosted gradient colors (top to bottom). */
    var washTop = 0xFFFFFFFF.toInt()
    var washBottom = 0xFFFFF0E6.toInt()

    /** Specular rim highlight width in px (0 = disabled) and top color. */
    var rimWidth = 0f
    var rimTop = 0x99FFFFFF.toInt()

    /** Ambient elevation drop shadow under glyphs. */
    var glassShadowRadius = 0f
    var glassShadowColor = 0x48000000
    var glassShadowDy = 0f

    override fun onDraw(canvas: Canvas) {
        if (!isAppleLockScreen) {
            super.onDraw(canvas)
            return
        }

        val lay = layout ?: run {
            super.onDraw(canvas)
            return
        }

        val p = paint
        val keepShader = p.shader
        val keepFilter = p.colorFilter
        val keepStyle = p.style
        val keepStroke = p.strokeWidth
        val keepJoin = p.strokeJoin
        val keepMask = p.maskFilter
        val keepColor = p.color
        p.clearShadowLayer()

        canvas.save()
        canvas.translate(compoundPaddingLeft.toFloat(), extendedPaddingTop.toFloat())
        val textTop = lay.getLineTop(0).toFloat()
        val textBottom = lay.getLineBottom(lay.lineCount - 1).toFloat()

        // 1. Soft diffuse drop shadow (elevation off background)
        if (glassShadowRadius > 0f) {
            p.shader = null
            p.colorFilter = null
            p.style = Paint.Style.FILL
            p.color = glassShadowColor
            p.maskFilter = BlurMaskFilter(glassShadowRadius, BlurMaskFilter.Blur.NORMAL)
            canvas.save()
            canvas.translate(0f, glassShadowDy)
            lay.draw(canvas)
            canvas.restore()
            p.maskFilter = null
        }

        // 2. Subtle specular top edge rim
        if (rimWidth > 0f) {
            p.colorFilter = null
            p.style = Paint.Style.STROKE
            p.strokeJoin = Paint.Join.ROUND
            p.strokeWidth = rimWidth * 2f
            p.color = 0xFFFFFFFF.toInt()
            p.shader = LinearGradient(0f, textTop, 0f, textBottom, rimTop, 0x1AFFFFFF, Shader.TileMode.CLAMP)
            lay.draw(canvas)
        }

        // 3. Apple Lock Screen luminous frosted gradient fill
        p.style = Paint.Style.FILL
        p.color = 0xFFFFFFFF.toInt()
        p.shader = LinearGradient(0f, textTop, 0f, textBottom, washTop, washBottom, Shader.TileMode.CLAMP)
        lay.draw(canvas)

        canvas.restore()

        p.shader = keepShader
        p.colorFilter = keepFilter
        p.style = keepStyle
        p.strokeWidth = keepStroke
        p.strokeJoin = keepJoin
        p.maskFilter = keepMask
        p.color = keepColor
    }
}
