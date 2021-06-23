package com.github.ajalt.colormath

import com.github.ajalt.colormath.internal.requireComponentSize
import com.github.ajalt.colormath.internal.withValidCIndex

/**
 * An ANSI-16 color code
 *
 * ## Valid codes
 *
 * | Color  | Foreground | Background | Bright FG | Bright BG |
 * | ------ | ---------- | ---------- | --------- | --------- |
 * | black  | 30         | 40         | 90        | 100       |
 * | red    | 31         | 41         | 91        | 101       |
 * | green  | 32         | 42         | 92        | 102       |
 * | yellow | 33         | 43         | 93        | 103       |
 * | blue   | 34         | 44         | 94        | 104       |
 * | purple | 35         | 45         | 95        | 105       |
 * | cyan   | 36         | 46         | 96        | 106       |
 * | white  | 37         | 47         | 97        | 107       |
 */
data class Ansi16(val code: Int) : Color {
    init {
        require(code in 30..37 || code in 40..47 ||
                code in 90..97 || code in 100..107) {
            "code not valid: $code"
        }
    }

    companion object;

    override val alpha: Float get() = 1f

    override fun toRGB(): RGB {
        val color = code % 10

        // grayscale
        if (color == 0 || color == 7) {
            val c: Double =
                if (code > 50) color + 3.5
                else color.toDouble()

            val v = c / 10.5
            return RGB(v, v, v)
        }

        // color
        val mul = if (code > 50) 1f else 0.5f
        val r = ((color % 2) * mul)
        val g = (((color / 2) % 2) * mul)
        val b = (((color / 4) % 2) * mul)

        return RGB(r, g, b)
    }

    override fun toAnsi256() = when {
        code >= 90 -> Ansi256(code - 90 + 8)
        else -> Ansi256(code - 30)
    }

    override fun toAnsi16() = this

    override fun convertToThis(other: Color): Ansi16 = other.toAnsi16()
    override fun componentCount(): Int = 2
    override fun components(): FloatArray = floatArrayOf(code.toFloat(), alpha)
    override fun componentIsPolar(i: Int): Boolean = withValidCIndex(i) { false }
    override fun fromComponents(components: FloatArray): Ansi16 {
        requireComponentSize(components)
        return Ansi16(components[0].toInt())
    }
}
