package com.arcpen.keyboard

import kotlin.math.*

/**
 * Tracks a single ArcPen gesture.
 *
 * Gesture model:
 *   1. Touch centre → isActive
 *   2. Sweep into a sector → startSector set, depth = 0, nothing targeted yet
 *   3. Cross first sector boundary → clockwise locked, depth = 1, character selected
 *   4. Each further same-direction crossing → depth++  (max 4)
 *   5. Return to centre → mid-drag commit (returned from onMove)
 *   6. Lift finger → final commit (returned from onUp)
 *
 * A depth-0 lift or tap (no crossing ever made) returns null → caller treats as space/tap.
 */
class GestureTracker {

    private var cx = 0f
    private var cy = 0f
    private var centerRadius = 0f

    var isActive = false
        private set
    var startSector = -1
        private set
    var clockwise: Boolean? = null   // null until first sector crossing
        private set
    var depth = 0                    // 0 = in start sector, no crossing yet
        private set
    var currentSector = -1
        private set
    var inCenter = false
        private set

    val trail = mutableListOf<Pair<Float, Float>>()

    var preview: Char? = null
        private set

    fun initialize(cx: Float, cy: Float, centerRadius: Float) {
        this.cx = cx
        this.cy = cy
        this.centerRadius = centerRadius
    }

    fun reset() {
        isActive = false
        startSector = -1
        clockwise = null
        depth = 0
        currentSector = -1
        inCenter = false
        trail.clear()
        preview = null
    }

    // Keeps isActive/inCenter; clears gesture so next outward sweep starts fresh.
    private fun softReset() {
        startSector = -1
        clockwise = null
        depth = 0
        currentSector = -1
        trail.clear()
        preview = null
    }

    fun onDown(x: Float, y: Float) {
        if (inCenterZone(x, y)) {
            reset()
            isActive = true
            inCenter = true
            trail.add(x to y)
        }
    }

    /**
     * Returns a committed [Char] when the finger returns to centre mid-drag, null otherwise.
     */
    fun onMove(x: Float, y: Float): Char? {
        if (!isActive) return null
        trail.add(x to y)

        if (inCenterZone(x, y)) {
            if (!inCenter && depth > 0) {
                // Crossing back into centre with a live gesture → commit it.
                val committed = CharacterLayout.getCharacter(startSector, clockwise!!, depth)
                softReset()
                inCenter = true
                return committed
            }
            inCenter = true
            preview = null
            return null
        }

        val sector = sectorOfAngle(angleOf(x, y))

        if (inCenter) {
            // Centre → first sector entry: record where we started, but depth stays 0.
            inCenter = false
            startSector = sector
            depth = 0
            currentSector = sector
        } else if (sector != currentSector) {
            val cw = isClockwiseTransition(currentSector, sector)
            when {
                clockwise == null -> {
                    // First boundary crossed: lock direction and move to depth 1.
                    clockwise = cw
                    depth = 1
                }
                clockwise == cw -> {
                    // Continuing in the same direction.
                    depth = (depth + 1).coerceAtMost(4)
                }
                else -> {
                    // Reversal: backtrack one step. If we unwind all the way to the
                    // starting sector (depth 0) also unlock the direction so the user
                    // can freely choose CW or CCW again from there.
                    depth--
                    if (depth == 0) clockwise = null
                }
            }
            currentSector = sector
        }

        preview = if (depth > 0) CharacterLayout.getCharacter(startSector, clockwise!!, depth) else null
        return null
    }

    fun onUp(x: Float, y: Float): Char? {
        if (!isActive) return null
        val result = if (depth > 0 && clockwise != null) {
            CharacterLayout.getCharacter(startSector, clockwise!!, depth)
        } else null
        reset()
        return result
    }

    // ── Geometry ──────────────────────────────────────────────────────────────

    private fun inCenterZone(x: Float, y: Float): Boolean {
        val dx = x - cx; val dy = y - cy
        return sqrt(dx * dx + dy * dy) < centerRadius
    }

    fun angleOf(x: Float, y: Float): Double {
        val dx = (x - cx).toDouble()
        val dy = (y - cy).toDouble()
        return (Math.toDegrees(atan2(dy, dx)) + 360) % 360
    }

    fun sectorOf(x: Float, y: Float) = sectorOfAngle(angleOf(x, y))

    private fun sectorOfAngle(a: Double) = when {
        a >= 225 && a < 315 -> 0   // North
        a >= 315 || a < 45  -> 1   // East
        a >= 45  && a < 135 -> 2   // South
        else                -> 3   // West
    }

    // CW on screen: North(0) → East(1) → South(2) → West(3) → North(0)
    private fun isClockwiseTransition(from: Int, to: Int) = (to - from + 4) % 4 == 1
}
