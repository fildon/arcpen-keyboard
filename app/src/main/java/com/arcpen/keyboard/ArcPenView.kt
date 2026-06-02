package com.arcpen.keyboard

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

enum class ShiftState { OFF, ONCE, LOCKED }
enum class KeyboardMode { ALPHA, NUMERIC }

/**
 * The ArcPen keyboard surface.
 *
 * ALPHA mode: circular gesture area fills the view. Corner buttons live in the
 * four dead-zones outside the circle.
 *
 *   SHF ─────────────────────── DEL
 *   │                             │
 *   │        circular area        │
 *   │                             │
 *   123 ───────────────────────  ENT
 *
 * NUMERIC mode: a standard dial-pad replaces the circle. Corner buttons remain.
 *
 *   SHF (inactive) ──────────── DEL
 *   │  [ 1 ][ 2 ][ 3 ]           │
 *   │  [ 4 ][ 5 ][ 6 ]           │
 *   │  [ 7 ][ 8 ][ 9 ]           │
 *   │     [ . ][ 0 ]              │
 *   ABC ───────────────────────  ENT
 */
class ArcPenView @JvmOverloads constructor(
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
    var mode = KeyboardMode.ALPHA
        set(value) {
            if (field == value) return
            field = value
            tracker.reset()
            pressedDial = -1
            invalidate()
        }

    // ── Geometry ──────────────────────────────────────────────────────────────
    private var cx = 0f
    private var cy = 0f
    private var outerR = 0f
    private var centerR = 0f
    private var cornerW = 0f
    private var cornerH = 0f

    // Dial-pad button layout (computed in onSizeChanged)
    private val dialRects  = mutableListOf<RectF>()
    private val dialChars  = charArrayOf('1','2','3','4','5','6','7','8','9','0','.')
    private var pressedDial = -1   // index into dialChars, or -1

    // ── Gesture state ─────────────────────────────────────────────────────────
    private val tracker = GestureTracker()

    private var touchDownX = 0f
    private var touchDownY = 0f
    private var longPressFired = false
    private var touchedCorner = -1

    private val TAP_MAX_DIST = 30f
    private val LONG_PRESS_MS = 500L

    private companion object {
        const val CORNER_TL = 0  // SHF  shift
        const val CORNER_TR = 1  // DEL  backspace
        const val CORNER_BL = 2  // 123 / ABC toggle
        const val CORNER_BR = 3  // ENT  enter
    }

    // ── Paints ────────────────────────────────────────────────────────────────
    private val paintBg = Paint().apply {
        color = Color.parseColor("#1A1A2E"); style = Paint.Style.FILL
    }
    private val paintCircle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#16213E"); style = Paint.Style.FILL
    }
    private val paintSectorLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0F3460"); style = Paint.Style.STROKE; strokeWidth = 2f
    }
    private val paintSectorHighlight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1C1C44"); style = Paint.Style.FILL
    }
    private val paintCenter = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E94560"); style = Paint.Style.FILL
    }
    private val paintCenterActive = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF6B8A"); style = Paint.Style.FILL
    }
    private val paintCenterRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E94560"); style = Paint.Style.STROKE; strokeWidth = 2f
    }
    private val paintCharNormal = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CCCCCC"); style = Paint.Style.FILL
        textAlign = Paint.Align.CENTER
    }
    private val paintCharTarget = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E94560"); style = Paint.Style.FILL
        textAlign = Paint.Align.CENTER
    }
    private val paintTrail = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E94560"); style = Paint.Style.STROKE
        strokeWidth = 5f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; alpha = 180
    }
    private val paintPreview = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E94560"); style = Paint.Style.FILL
        textAlign = Paint.Align.CENTER; isFakeBoldText = true
    }
    // Corner labels — all bold so they share the same visual weight
    private val paintCornerL = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#888888"); style = Paint.Style.FILL
        textAlign = Paint.Align.LEFT; isFakeBoldText = true
    }
    private val paintCornerR = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#888888"); style = Paint.Style.FILL
        textAlign = Paint.Align.RIGHT; isFakeBoldText = true
    }
    private val paintShiftOnceL = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; style = Paint.Style.FILL
        textAlign = Paint.Align.LEFT; isFakeBoldText = true
    }
    private val paintShiftLockedL = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E94560"); style = Paint.Style.FILL
        textAlign = Paint.Align.LEFT; isFakeBoldText = true
    }
    // Dial-pad
    private val paintDialBtn = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#16213E"); style = Paint.Style.FILL
    }
    private val paintDialBtnPressed = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2A2A5E"); style = Paint.Style.FILL
    }
    private val paintDialBtnStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0F3460"); style = Paint.Style.STROKE; strokeWidth = 1.5f
    }
    private val paintDialDigit = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; style = Paint.Style.FILL; textAlign = Paint.Align.CENTER
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
        centerR = outerR * 0.22f

        cornerW = w * 0.22f
        cornerH = h * 0.22f

        val cornerTextSize = minOf(cornerW, cornerH) * 0.38f
        paintCornerL.textSize     = cornerTextSize
        paintCornerR.textSize     = cornerTextSize
        paintShiftOnceL.textSize  = cornerTextSize
        paintShiftLockedL.textSize = cornerTextSize

        paintCharNormal.textSize = outerR * 0.11f
        paintCharTarget.textSize = outerR * 0.13f
        paintPreview.textSize    = outerR * 0.22f

        tracker.initialize(cx, cy, centerR)
        computeDialPadLayout(w.toFloat(), h.toFloat())
    }

    private fun computeDialPadLayout(w: Float, h: Float) {
        dialRects.clear()
        val btnW   = outerR * 0.52f
        val btnH   = btnW * 0.72f
        val gap    = btnW * 0.16f
        val gridW  = 3 * btnW + 2 * gap
        val gridH  = 4 * btnH + 3 * gap
        val left   = cx - gridW / 2f
        val top    = cy - gridH / 2f

        // Rows 1–3: three buttons each (digits 1–9)
        for (row in 0..2) {
            val y = top + row * (btnH + gap)
            for (col in 0..2) {
                val x = left + col * (btnW + gap)
                dialRects.add(RectF(x, y, x + btnW, y + btnH))
            }
        }
        // Row 4: "0" double-wide (cols 0–1) + "." single-wide (col 2), flush with grid
        val y4 = top + 3 * (btnH + gap)
        dialRects.add(RectF(left,                    y4, left + 2*btnW + gap, y4 + btnH))  // '0'
        dialRects.add(RectF(left + 2*(btnW + gap),   y4, left + gridW,        y4 + btnH))  // '.'

        paintDialDigit.textSize = btnH * 0.52f
    }

    // ── Draw ──────────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paintBg)
        if (mode == KeyboardMode.ALPHA) drawAlphaMode(canvas) else drawNumericMode(canvas)
        drawCornerButtons(canvas)
    }

    private fun drawAlphaMode(canvas: Canvas) {
        if (tracker.isActive && tracker.currentSector >= 0) {
            drawSectorHighlight(canvas, tracker.currentSector)
        }
        canvas.drawCircle(cx, cy, outerR, paintCircle)
        drawSectorLines(canvas)

        for (fraction in floatArrayOf(0.40f, 0.56f, 0.71f, 0.85f)) {
            paintSectorLine.alpha = 60
            canvas.drawCircle(cx, cy, outerR * fraction, paintSectorLine)
        }
        paintSectorLine.alpha = 255

        drawCharacters(canvas)
        drawTrail(canvas)

        val centPaint = if (tracker.isActive && tracker.inCenter) paintCenterActive else paintCenter
        canvas.drawCircle(cx, cy, centerR, centPaint)
        canvas.drawCircle(cx, cy, centerR, paintCenterRing)

        tracker.preview?.let { ch ->
            val display = if (shiftState != ShiftState.OFF) ch.uppercaseChar() else ch
            canvas.drawText(display.toString(), cx, cy + paintPreview.textSize * 0.35f, paintPreview)
        }
    }

    private fun drawNumericMode(canvas: Canvas) {
        val radius = minOf(cornerW, cornerH) * 0.22f
        dialRects.forEachIndexed { i, rect ->
            val fill = if (i == pressedDial) paintDialBtnPressed else paintDialBtn
            canvas.drawRoundRect(rect, radius, radius, fill)
            canvas.drawRoundRect(rect, radius, radius, paintDialBtnStroke)
            val textY = rect.centerY() + paintDialDigit.textSize * 0.35f
            canvas.drawText(dialChars[i].toString(), rect.centerX(), textY, paintDialDigit)
        }
    }

    // ── Alpha drawing helpers ─────────────────────────────────────────────────

    private fun drawSectorLines(canvas: Canvas) {
        for (angleDeg in floatArrayOf(45f, 135f, 225f, 315f)) {
            val rad = Math.toRadians(angleDeg.toDouble())
            canvas.drawLine(cx, cy,
                cx + outerR * cos(rad).toFloat(),
                cy + outerR * sin(rad).toFloat(),
                paintSectorLine)
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
        val depthRadii   = floatArrayOf(0.40f, 0.56f, 0.71f, 0.85f)
        val offset       = 20.0
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
                    val a = centreAngle + offset
                    val p = charPaintFor(sector, true, depth)
                    canvas.drawText(if (shifted) it.uppercaseChar().toString() else it.toString(),
                        charX(a, r), charY(a, r, p), p)
                }
                ccwChar?.let {
                    val a = centreAngle - offset
                    val p = charPaintFor(sector, false, depth)
                    canvas.drawText(if (shifted) it.uppercaseChar().toString() else it.toString(),
                        charX(a, r), charY(a, r, p), p)
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

        // Top-left: SHF / CAP (dimmed when numeric mode is active)
        val shiftPaint = when {
            mode == KeyboardMode.NUMERIC    -> paintCornerL
            shiftState == ShiftState.ONCE   -> paintShiftOnceL
            shiftState == ShiftState.LOCKED -> paintShiftLockedL
            else                            -> paintCornerL
        }
        val shiftLabel = if (shiftState == ShiftState.LOCKED) "CAP" else "SHF"
        canvas.drawText(shiftLabel, pad, pad + ts, shiftPaint)

        // Top-right: DEL
        canvas.drawText("DEL", w - pad, pad + ts, paintCornerR)

        // Bottom-left: 123 / ABC
        canvas.drawText(if (mode == KeyboardMode.ALPHA) "123" else "ABC", pad, h - pad, paintCornerL)

        // Bottom-right: ENT
        canvas.drawText("ENT", w - pad, h - pad, paintCornerR)
    }

    // ── Touch handling ────────────────────────────────────────────────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        // Corners are always checked first, in both modes.
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchedCorner = cornerFor(x, y)
                if (touchedCorner >= 0) return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (touchedCorner >= 0) {
                    if (event.actionMasked == MotionEvent.ACTION_UP && cornerFor(x, y) == touchedCorner) {
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        handleCorner(touchedCorner)
                    }
                    touchedCorner = -1
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (touchedCorner >= 0) return true
            }
        }

        return if (mode == KeyboardMode.NUMERIC) {
            handleDialPadTouch(event, x, y)
        } else {
            handleAlphaTouch(event, x, y)
        }
    }

    private fun handleAlphaTouch(event: MotionEvent, x: Float, y: Float): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = x
                touchDownY = y
                longPressFired = false
                tracker.onDown(x, y)
                if (tracker.isActive) postDelayed(longPressRunnable, LONG_PRESS_MS)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
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
                removeCallbacks(longPressRunnable)
                if (longPressFired) { tracker.reset(); invalidate(); return true }

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

    private fun handleDialPadTouch(event: MotionEvent, x: Float, y: Float): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressedDial = dialIndexAt(x, y)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                val idx = dialIndexAt(x, y)
                if (idx != pressedDial) { pressedDial = idx; invalidate() }
            }
            MotionEvent.ACTION_UP -> {
                val idx = dialIndexAt(x, y)
                if (idx >= 0 && idx == pressedDial) {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    keyListener?.onCharacter(dialChars[idx])
                }
                pressedDial = -1
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> { pressedDial = -1; invalidate() }
        }
        return true
    }

    private fun dialIndexAt(x: Float, y: Float): Int =
        dialRects.indexOfFirst { it.contains(x, y) }

    // ── Corner / shift helpers ────────────────────────────────────────────────

    private fun cornerFor(x: Float, y: Float): Int = when {
        x < cornerW && y < cornerH                   -> CORNER_TL
        x > width - cornerW && y < cornerH           -> CORNER_TR
        x < cornerW && y > height - cornerH          -> CORNER_BL
        x > width - cornerW && y > height - cornerH  -> CORNER_BR
        else                                         -> -1
    }

    private fun handleCorner(corner: Int) {
        when (corner) {
            CORNER_TL -> if (mode == KeyboardMode.ALPHA) cycleShift()
            CORNER_TR -> keyListener?.onBackspace()
            CORNER_BL -> mode = if (mode == KeyboardMode.ALPHA) KeyboardMode.NUMERIC else KeyboardMode.ALPHA
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
