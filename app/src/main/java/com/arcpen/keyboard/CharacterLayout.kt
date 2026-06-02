package com.arcpen.keyboard

/**
 * Maps (sector, clockwise, depth) triples to characters.
 *
 * Sectors: 0=North (top), 1=East (right), 2=South (bottom), 3=West (left).
 * Direction: true=clockwise, false=counterclockwise.
 * Depth 1–4: how many quadrants the gesture passes through before returning to centre.
 *
 * Placement is frequency-ordered across letters AND punctuation so the most
 * common characters always require the shallowest arc.
 *
 * Approximate frequencies used (letters: standard corpus; punctuation: per-1000-word
 * counts converted to character-level % by dividing by ~5000):
 *
 *   Depth 1 – e(13%) t(9%) a(8%) o(7.5%) i(7%) n(6.7%) s(6.3%) h(6.1%)
 *   Depth 2 – r(6%) d(4.3%) l(4%) c(2.8%) u(2.8%) m(2.4%) w(2.4%) f(2.2%)
 *   Depth 3 – y(2%) g(2%) p(1.9%) b(1.5%) .(1.3%) ,(1.2%) v(0.98%) k(0.77%)
 *   Depth 4 – '(0.49%) -(0.31%) j(0.15%) x(0.15%) ?(0.11%) q(0.10%) !(0.07%) z(0.07%)
 *
 * The West sector doubles as the punctuation sector at depths 3–4:
 *   West depth 3: , .   West depth 4: - '
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
        GestureKey(1, false, 4) to 'j',
        GestureKey(1, true,  1) to 'o',
        GestureKey(1, true,  2) to 'c',
        GestureKey(1, true,  3) to 'b',
        GestureKey(1, true,  4) to 'x',
        // ── South sector ──────────────────────────────────────
        GestureKey(2, false, 1) to 'i',
        GestureKey(2, false, 2) to 'u',
        GestureKey(2, false, 3) to 'v',
        GestureKey(2, false, 4) to '!',
        GestureKey(2, true,  1) to 'n',
        GestureKey(2, true,  2) to 'm',
        GestureKey(2, true,  3) to 'k',
        GestureKey(2, true,  4) to '?',
        // ── West sector (punctuation sector at depths 3–4) ────
        GestureKey(3, false, 1) to 's',
        GestureKey(3, false, 2) to 'f',
        GestureKey(3, false, 3) to ',',
        GestureKey(3, false, 4) to '-',
        GestureKey(3, true,  1) to 'h',
        GestureKey(3, true,  2) to 'w',
        GestureKey(3, true,  3) to '.',
        GestureKey(3, true,  4) to '\'',
    )

    fun getCharacter(sector: Int, clockwise: Boolean, depth: Int): Char? =
        map[GestureKey(sector, clockwise, depth)]

    /** Returns [(ccwChar, cwChar)] for depths 1–4 in the given sector. */
    fun sectorPairs(sector: Int): List<Pair<Char?, Char?>> =
        (1..4).map { d -> Pair(getCharacter(sector, false, d), getCharacter(sector, true, d)) }
}
