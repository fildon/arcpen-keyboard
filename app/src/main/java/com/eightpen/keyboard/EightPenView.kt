package com.eightpen.keyboard

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

enum class ShiftState { OFF, ONCE, LOCKED }

/**
 * The 8pen keyboard surface.
 *
 * The circle fills the whole view. The four utility actions live in the corners
 * of the bounding rectangle, in the space outside the circle:
 *
 *   ⇧ ───────────────────── ⌫
 *   │                         │
 *   │      circular area      │
 *   │                         │
 *   sp ──────────────────── ↵
 *
 * Gesture rules:
 *   • Touch-down in the centre disc → starts gesture
 *   • Sweep into a quadrant, arc through more quadrants, return to centre → commit
 *   • Tap centre (no arc) → space
 *   • Long-press centre → backspace
 *   • Tap any corner → corner action (⇧ cycles shift, ⌫ backspace, space, ↵ enter)
 */
class EightPenView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // ── Callback ──────────────────────────────────────────────────────────────
    interface KeyListener {
        fun onCharacter(ch: Char)
        fun onBackspace()
        fun onSpace()
        fun onEnter()
    }

    var keyListener: KeyListener? = null
    var shiftState = ShiftState.OFF

    // ── Geometry ──────────────────────────────────────────────────────────────
    private var cx = 0f
    private var cy = 0f
    private var outerR = 0f
    private var centerR = 0f
    private var cornerW = 0f
    private var cornerH = 0f

    // ── Gesture state ─────────────────────────────────────────────────────────
    private val tracker = GestureTracker()

    private var touchDownX = 0f
    private var touchDownY = 0f
    private var longPressFired = false
    private var touchedCorner = -1

    private val TAP_MAX_DIST = 30f
    private val LONG_PRESS_MS = 500L

    private companion object {
        const val CORNER_TL = 0  // ⇧  shift
        const val CORNER_TR = 1  // ⌫  backspace
        const val CORNER_BL = 2  // space
        const val CORNER_BR = 3  // ↵  enter
    }

    // ── Paints ────────────────────────────────────────────────────────────────
    private val paintBg = Paint().apply {
        color = Color.parseColor("#1A1A2E")
        style = Paint.Style.FILL
    }
    private val paintCircle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#16213E")
        style = Paint.Style.FILL
    }
    private val paintSectorLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0F3460")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val paintSectorHighlight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1C1C44")
        style = Paint.Style.FILL
    }
    private val paintCenter = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E94560")
        style = Paint.Style.FILL
    }
    private val paintCenterActive = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF6B8A")
        style = Paint.Style.FILL
    }
    private val paintCenterRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E94560")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val paintCharNormal = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CCCCCC")
        style = Paint.Style.FILL
        textAlign = Paint.Align.CENTER
    }
    private val paintCharTarget = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E94560")
        style = Paint.Style.FILL
        textAlign = Paint.Align.CENTER
    }
    private val paintTrail = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E94560")
        style = Paint.Style.STROKE
        strokeWidth = 5f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        alpha = 180
    }
    private val paintPreview = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E94560")
        style = Paint.Style.FILL
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    // Corner label paints — LEFT and RIGHT aligned versions
    private val paintCornerL = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#888888")
        style = Paint.Style.FILL
        textAlign = Paint.Align.LEFT
    }
    private val paintCornerR = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#888888")
        style = Paint.Style.FILL
        textAlign = Paint.Align.RIGHT
    }
    private val paintShiftOnceL = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        textAlign = Paint.Align.LEFT
    }
    private val paintShiftLockedL = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E94560")
        style = Paint.Style.FILL
        textAlign = Paint.Align.LEFT
        isFakeBoldText = true
    }

    // ── Reusable draw objects ─────────────────────────────────────────────────
    private val trailPath = Path()
    private val sectorPath = Path()
    private val oval = RectF()

    // ── Long-press runnable ───────────────────────────────────────────────────
    private val longPressRunnable = Runnable {
        if (!longPressFired) {
            longPressFired = true
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            keyListener?.onBackspace()
            tracker.reset()
            invalidate()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, (w * 0.85f).toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        cx = w / 2f
        cy = h / 2f
        outerR = minOf(w.toFloat(), h.toFloat()) / 2f * 0.88f
        centerR = outerR * 0.22f   // larger centre zone for easier targeting

        cornerW = w * 0.22f
        cornerH = h * 0.22f

        val cornerTextSize = minOf(cornerW, cornerH) * 0.38f
        paintCornerL.textSize  = cornerTextSize
        paintCornerR.textSize  = cornerTextSize
        paintShiftOnceL.textSize   = cornerTextSize
        paintShiftLockedL.textSize = cornerTextSize

        paintCharNormal.textSize = outerR * 0.11f
        paintCharTarget.textSize = outerR * 0.13f
        paintPreview.textSize    = outerR * 0.22f

        tracker.initialize(cx, cy, centerR)
    }

    override fun onDraw(canvas: Canvas) {
        // ── Background ────────────────────────────────────────────────────────
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paintBg)

        // ── Sector highlight ──────────────────────────────────────────────────
        if (tracker.isActive && tracker.currentSector >= 0) {
            drawSectorHighlight(canvas, tracker.currentSector)
        }

        // ── Outer circle ──────────────────────────────────────────────────────
        canvas.drawCircle(cx, cy, outerR, paintCircle)

        // ── Sector dividers ───────────────────────────────────────────────────
        drawSectorLines(canvas)

        // ── Depth rings (subtle) ──────────────────────────────────────────────
        for (fraction in floatArrayOf(0.40f, 0.56f, 0.71f, 0.85f)) {
            paintSectorLine.alpha = 60
            canvas.drawCircle(cx, cy, outerR * fraction, paintSectorLine)
        }
        paintSectorLine.alpha = 255

        // ── Character labels ──────────────────────────────────────────────────
        drawCharacters(canvas)

        // ── Gesture trail ─────────────────────────────────────────────────────
        drawTrail(canvas)

        // ── Centre disc ───────────────────────────────────────────────────────
        val centPaint = if (tracker.isActive && tracker.inCenter) paintCenterActive else paintCenter
        canvas.drawCircle(cx, cy, centerR, centPaint)
        canvas.drawCircle(cx, cy, centerR, paintCenterRing)

        // ── Preview character ─────────────────────────────────────────────────
        tracker.preview?.let { ch ->
            val display = if (shiftState != ShiftState.OFF) ch.uppercaseChar() else ch
            canvas.drawText(display.toString(), cx, cy + paintPreview.textSize * 0.35f, paintPreview)
        }

        // ── Corner buttons ────────────────────────────────────────────────────
        drawCornerButtons(canvas)
    }

    // ── Drawing helpers ───────────────────────────────────────────────────────

    private fun drawSectorLines(canvas: Canvas) {
        val diagonals = floatArrayOf(45f, 135f, 225f, 315f)
        for (angleDeg in diagonals) {
            val rad = Math.toRadians(angleDeg.toDouble())
            val ex = cx + outerR * cos(rad).toFloat()
            val ey = cy + outerR * sin(rad).toFloat()
            canvas.drawLine(cx, cy, ex, ey, paintSectorLine)
        }
    }

    private fun drawSectorHighlight(canvas: Canvas, sector: Int) {
        val startAngles = floatArrayOf(225f, 315f, 45f, 135f)
        sectorPath.reset()
        sectorPath.moveTo(cx, cy)
        oval.set(cx - outerR, cy - outerR, cx + outerR, cy + outerR)
        sectorPath.arcTo(oval, startAngles[sector], 90f, false)
        sectorPath.close()
        canvas.drawPath(sectorPath, paintSectorHighlight)
    }

    private fun drawCharacters(canvas: Canvas) {
        val depthRadii  = floatArrayOf(0.40f, 0.56f, 0.71f, 0.85f)
        val offset      = 20.0
        val sectorAngles = doubleArrayOf(270.0, 0.0, 90.0, 180.0)
        val shifted = shiftState != ShiftState.OFF

        for (sector in 0..3) {
            val centreAngle = sectorAngles[sector]
            val pairs = CharacterLayout.sectorPairs(sector)

            for ((depthIdx, pair) in pairs.withIndex()) {
                val (ccwChar, cwChar) = pair
                val r = outerR * depthRadii[depthIdx]
                val depth = depthIdx + 1

                cwChar?.let {
                    val angle = centreAngle + offset
                    val p = charPaintFor(sector, true, depth)
                    val label = if (shifted) it.uppercaseChar().toString() else it.toString()
                    canvas.drawText(label, charX(angle, r), charY(angle, r, p), p)
                }
                ccwChar?.let {
                    val angle = centreAngle - offset
                    val p = charPaintFor(sector, false, depth)
                    val label = if (shifted) it.uppercaseChar().toString() else it.toString()
                    canvas.drawText(label, charX(angle, r), charY(angle, r, p), p)
                }
            }
        }
    }

    private fun charPaintFor(sector: Int, cw: Boolean, depth: Int): Paint {
        if (!tracker.isActive || tracker.startSector != sector) return paintCharNormal
        if (tracker.depth != depth || depth == 0) return paintCharNormal
        val dir = tracker.clockwise ?: return paintCharNormal
        return if (dir == cw) paintCharTarget else paintCharNormal
    }

    private fun charX(angleDeg: Double, r: Float) =
        (cx + r * cos(Math.toRadians(angleDeg))).toFloat()

    private fun charY(angleDeg: Double, r: Float, paint: Paint) =
        (cy + r * sin(Math.toRadians(angleDeg))).toFloat() + paint.textSize * 0.35f

    private fun drawTrail(canvas: Canvas) {
        val pts = tracker.trail
        if (pts.size < 2) return
        trailPath.reset()
        trailPath.moveTo(pts[0].first, pts[0].second)
        for (i in 1 until pts.size) trailPath.lineTo(pts[i].first, pts[i].second)
        canvas.drawPath(trailPath, paintTrail)
    }

    private fun drawCornerButtons(canvas: Canvas) {
        val pad = minOf(cornerW, cornerH) * 0.18f
        val ts  = paintCornerL.textSize
        val h   = height.toFloat()
        val w   = width.toFloat()

        // Top-left: shift (colour reflects state)
        val shiftPaint = when (shiftState) {
            ShiftState.OFF    -> paintCornerL
            ShiftState.ONCE   -> paintShiftOnceL
            ShiftState.LOCKED -> paintShiftLockedL
        }
        val shiftIcon = if (shiftState == ShiftState.LOCKED) "⇪" else "⇧"
        canvas.drawText(shiftIcon, pad, pad + ts, shiftPaint)

        // Top-right: backspace
        canvas.drawText("⌫", w - pad, pad + ts, paintCornerR)

        // Bottom-left: space
        canvas.drawText("spc", pad, h - pad, paintCornerL)

        // Bottom-right: enter
        canvas.drawText("↵", w - pad, h - pad, paintCornerR)
    }

    // ── Touch handling ────────────────────────────────────────────────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchedCorner = cornerFor(x, y)
                if (touchedCorner >= 0) return true  // corner press — wait for UP

                touchDownX = x
                touchDownY = y
                longPressFired = false
                tracker.onDown(x, y)
                if (tracker.isActive) postDelayed(longPressRunnable, LONG_PRESS_MS)
                invalidate()
            }

            MotionEvent.ACTION_MOVE -> {
                if (touchedCorner >= 0) return true  // ignore drag on corner press
                if (!tracker.isActive) return true
                val committed = tracker.onMove(x, y)
                if (committed != null) {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    keyListener?.onCharacter(applyShift(committed))
                }
                if (tracker.startSector != -1) removeCallbacks(longPressRunnable)
                invalidate()
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // Corner tap
                if (touchedCorner >= 0) {
                    if (event.actionMasked == MotionEvent.ACTION_UP && cornerFor(x, y) == touchedCorner) {
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        handleCorner(touchedCorner)
                    }
                    touchedCorner = -1
                    return true
                }

                removeCallbacks(longPressRunnable)
                if (longPressFired) {
                    tracker.reset()
                    invalidate()
                    return true
                }

                val dist = sqrt((x - touchDownX).pow(2) + (y - touchDownY).pow(2))
                if (dist < TAP_MAX_DIST && tracker.startSector == -1) {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    keyListener?.onSpace()
                    tracker.reset()
                } else {
                    val ch = tracker.onUp(x, y)
                    if (ch != null) {
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        keyListener?.onCharacter(applyShift(ch))
                    }
                }
                invalidate()
            }
        }
        return true
    }

    private fun cornerFor(x: Float, y: Float): Int = when {
        x < cornerW && y < cornerH                         -> CORNER_TL
        x > width - cornerW && y < cornerH                 -> CORNER_TR
        x < cornerW && y > height - cornerH                -> CORNER_BL
        x > width - cornerW && y > height - cornerH        -> CORNER_BR
        else                                               -> -1
    }

    private fun handleCorner(corner: Int) {
        when (corner) {
            CORNER_TL -> cycleShift()
            CORNER_TR -> keyListener?.onBackspace()
            CORNER_BL -> keyListener?.onSpace()
            CORNER_BR -> keyListener?.onEnter()
        }
    }

    private fun cycleShift() {
        shiftState = when (shiftState) {
            ShiftState.OFF    -> ShiftState.ONCE
            ShiftState.ONCE   -> ShiftState.LOCKED
            ShiftState.LOCKED -> ShiftState.OFF
        }
        invalidate()
    }

    private fun applyShift(ch: Char): Char = when (shiftState) {
        ShiftState.OFF    -> ch
        ShiftState.ONCE   -> ch.uppercaseChar().also { shiftState = ShiftState.OFF; invalidate() }
        ShiftState.LOCKED -> ch.uppercaseChar()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(longPressRunnable)
        super.onDetachedFromWindow()
    }
}
