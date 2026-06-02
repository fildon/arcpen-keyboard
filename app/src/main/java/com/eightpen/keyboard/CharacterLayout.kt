package com.eightpen.keyboard

/**
 * Maps (sector, clockwise, depth) triples to characters.
 *
 * Sectors: 0=North (top), 1=East (right), 2=South (bottom), 3=West (left).
 * Direction: true=clockwise, false=counterclockwise.
 * Depth 1–4: how many quadrants the gesture passes through before returning to centre.
 *
 * Character placement is frequency-ordered so common letters require shallower arcs.
 * Depth-1 characters (e, t, a, o, i, n, s, h) cover the eight most-common English letters.
 */
object CharacterLayout {

    data class GestureKey(val sector: Int, val clockwise: Boolean, val depth: Int)

    val map: Map<GestureKey, Char> = mapOf(
        // ── North sector ──────────────────────────────────────
        GestureKey(0, false, 1) to 'e',
        GestureKey(0, false, 2) to 'r',
        GestureKey(0, false, 3) to 'y',
        GestureKey(0, false, 4) to 'q',
        GestureKey(0, true,  1) to 't',
        GestureKey(0, true,  2) to 'd',
        GestureKey(0, true,  3) to 'g',
        GestureKey(0, true,  4) to 'z',
        // ── East sector ───────────────────────────────────────
        GestureKey(1, false, 1) to 'a',
        GestureKey(1, false, 2) to 'l',
        GestureKey(1, false, 3) to 'p',
        GestureKey(1, false, 4) to ',',
        GestureKey(1, true,  1) to 'o',
        GestureKey(1, true,  2) to 'c',
        GestureKey(1, true,  3) to 'b',
        GestureKey(1, true,  4) to '.',
        // ── South sector ──────────────────────────────────────
        GestureKey(2, false, 1) to 'i',
        GestureKey(2, false, 2) to 'u',
        GestureKey(2, false, 3) to 'v',
        GestureKey(2, false, 4) to '!',
        GestureKey(2, true,  1) to 'n',
        GestureKey(2, true,  2) to 'm',
        GestureKey(2, true,  3) to 'k',
        GestureKey(2, true,  4) to '?',
        // ── West sector ───────────────────────────────────────
        GestureKey(3, false, 1) to 's',
        GestureKey(3, false, 2) to 'f',
        GestureKey(3, false, 3) to 'j',
        GestureKey(3, false, 4) to '-',
        GestureKey(3, true,  1) to 'h',
        GestureKey(3, true,  2) to 'w',
        GestureKey(3, true,  3) to 'x',
        GestureKey(3, true,  4) to '/',
    )

    fun getCharacter(sector: Int, clockwise: Boolean, depth: Int): Char? =
        map[GestureKey(sector, clockwise, depth)]

    /** Returns [(ccwChar, cwChar)] for depths 1–4 in the given sector. */
    fun sectorPairs(sector: Int): List<Pair<Char?, Char?>> =
        (1..4).map { d -> Pair(getCharacter(sector, false, d), getCharacter(sector, true, d)) }
}
