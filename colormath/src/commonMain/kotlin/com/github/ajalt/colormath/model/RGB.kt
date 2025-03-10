package com.github.ajalt.colormath.model

import com.github.ajalt.colormath.Color
import com.github.ajalt.colormath.RenderCondition
import com.github.ajalt.colormath.RenderCondition.AUTO
import com.github.ajalt.colormath.WhitePointColorSpace
import com.github.ajalt.colormath.internal.*
import com.github.ajalt.colormath.model.RGBColorSpaces.BT2020
import kotlin.math.roundToInt


interface RGBColorSpace : WhitePointColorSpace<RGB> {
    operator fun invoke(r: Float, g: Float, b: Float, alpha: Float = 1f): RGB
    operator fun invoke(r: Number, g: Number, b: Number, alpha: Number = 1f): RGB =
        invoke(r.toFloat(), g.toFloat(), b.toFloat(), alpha.toFloat())

    /**
     * Construct an RGB instance from Int values in the range `[0, 255]`.
     *
     * @property r The red channel, an integer in the range `[0, 255]`
     * @property g The green channel, an integer in the range `[0, 255]`
     * @property b The blue channel, an integer in the range `[0, 255]`
     * @property alpha The alpha channel, an integer in the range `[0, 255]`
     */
    fun from255(r: Int, g: Int, b: Int, alpha: Int = 255) = invoke(
        r = (r / 255f),
        g = (g / 255f),
        b = (b / 255f),
        alpha = (alpha / 255f)
    )

    /**
     * Construct an RGB instance from a hex string with optional alpha channel.
     *
     * [hex] may optionally start with a `#`. The remaining characters should be one of the following forms:
     *
     * - `ddeeff`: The RGB values specified in pairs of hex digits
     * - `ddeeffaa`: Like the 6 digit form, but with an extra pair of hex digits for specifying the alpha channel
     * - `def`: A shorter version of the 6 digit form. Each digit is repeated, so `def` is equivalent to `ddeeff`
     * - `defa`: A shorter version of the 8 digit for.Each digit is repeated, so `defa` is equivalent to `ddeeffaa`
     */
    operator fun invoke(hex: String) = from255(
        r = hex.validateHex().parseHex(0),
        g = hex.parseHex(1),
        b = hex.parseHex(2),
        alpha = if (hex.hexLength.let { it == 4 || it == 8 }) hex.parseHex(3) else 255
    )

    /** Construct an RGB instance with a grey color from a fraction of white in range `[0, 1]` */
    fun grey(amount: Number, alpha: Number = 1f): RGB = invoke(amount, amount, amount, alpha)

    /** The [TransferFunctions] for this color space */
    val transferFunctions: TransferFunctions

    /**
     * A 3×3 matrix stored in row-major order that transforms linear-light values in this space to [XYZ] tristimulus values.
     */
    val matrixToXyz: FloatArray

    /**
     * A 3×3 matrix stored in row-major order that transforms [XYZ] tristimulus values to linear-light values in this space.
     */
    val matrixFromXyz: FloatArray

    /**
     * Color Component Transfer Functions (CCTFs) for encoding and decoding components of [RGB] color spaces.
     */
    interface TransferFunctions {
        /**
         * The Electro-Optical Transfer Function (EOTF / EOCF)
         *
         * This function decodes non-linear signal values into linear-light values.
         */
        fun eotf(x: Float): Float

        /**
         * The Opto-Electronic Transfer Function (OETF / OECF)
         *
         * This function encodes linear scene light into non-linear signal values.
         */
        fun oetf(x: Float): Float
    }

    /**
     * A transfer function and its inverse defined with a pure gamma exponent.
     *
     * ### OETF
     * ```
     * Y = Xᵞ
     * ```
     *
     * ### EOTF
     * ```
     * Y = X¹ᐟᵞ
     * ```
     */
    data class GammaTransferFunctions(
        private val gamma: Double,
    ) : TransferFunctions {
        override fun eotf(x: Float): Float = x.spow(gamma).toFloat()
        override fun oetf(x: Float): Float = x.spow(1.0 / gamma).toFloat()
    }

    /**
     * A set of identity functions that leave values unchanged.
     *
     * ### OETF
     * ```
     * Y = X
     * ```
     *
     * ### EOTF
     * ```
     * Y = X
     * ```
     */
    object LinearTransferFunctions : TransferFunctions {
        override fun eotf(x: Float): Float = x
        override fun oetf(x: Float): Float = x
    }
}


/**
 * The RGB color model, using the [sRGB][SRGB] color space by default.
 *
 * There are several ways to construct sRGB instances. All of the following are equivalent:
 *
 * ```kotlin
 * RGB(0.2, 0.4, 0.6)
 * SRGB(0.2, 0.4, 0.6)
 * RGB.from255(51, 102, 153)
 * RGB("#369")
 * RGB("#336699")
 * ```
 *
 * You also can construct instances of [other RGB color spaces][RGBColorSpaces]:
 *
 * ```kotlin
 * import com.github.ajalt.colormath.model.RGBColorSpaces.LINEAR_SRGB
 * LinearSRGB(0.1, 0.2, 0.3)
 * ```
 *
 * | Component  | Description | Range    |
 * | ---------- | ----------- | -------- |
 * | [r]        | red         | `[0, 1]` |
 * | [g]        | green       | `[0, 1]` |
 * | [b]        | blue        | `[0, 1]` |
 */
data class RGB internal constructor(
    val r: Float,
    val g: Float,
    val b: Float,
    override val alpha: Float,
    override val space: RGBColorSpace,
) : Color {
    /** Default constructors for the [RGB] color model: the [SRGB] space. */
    companion object : RGBColorSpace by RGBColorSpaces.SRGB

    /** The red channel scaled to [0, 255]. */
    val redInt: Int get() = (r * 255).roundToInt()

    /** The green channel scaled to [0, 255]. */
    val greenInt: Int get() = (g * 255).roundToInt()

    /** The blue channel scaled to [0, 255]. HDR colors may exceed this range. */
    val blueInt: Int get() = (b * 255).roundToInt()

    /** The alpha channel scaled to [0, 255]. If the alpha is undefined, 255 will be returned. */
    val alphaInt: Int get() = (alpha.nanToOne() * 255).roundToInt()

    /**
     * Return this color as a packed `ARGB` integer.
     *
     * All components will be clamped to `[0, 255]`.
     */
    fun toRGBInt() = toSRGB { RGBInt(r, g, b, alpha.nanToOne()) }

    /**
     * Convert this color to an RGB hex string.
     *
     * If [renderAlpha] is `ALWAYS`, the [alpha] value will be added e.g. the `aa` in `#ffffffaa`.
     * If it's `NEVER`, the [alpha] will be omitted. If it's `AUTO`, then the [alpha] will be added
     * if it's less than 1.
     *
     * @return A string in the form `"#ffffff"` if [withNumberSign] is true,
     *     or in the form `"ffffff"` otherwise.
     */
    fun toHex(withNumberSign: Boolean = true, renderAlpha: RenderCondition = AUTO): String {
        return toRGBInt().toHex(withNumberSign, renderAlpha)
    }

    /**
     * Convert this color to another RGB [color space][space].
     *
     * This conversion uses [CIEXYZ][XYZ] as the Profile Connection Space (PCS).
     */
    fun convertTo(space: RGBColorSpace): RGB {
        val f = SRGB.transferFunctions
        return when {
            this.space == space -> this
            this.space == SRGB && space == RGBColorSpaces.LinearSRGB -> space(f.eotf(r), f.eotf(g), f.eotf(b), alpha)
            this.space == RGBColorSpaces.LinearSRGB && space == SRGB -> space(f.oetf(r), f.oetf(g), f.oetf(b), alpha)
            else -> toXYZ().toRGB(space)
        }
    }

    /**
     * Return a copy of this color with all component values in the range `[0, 1]`.
     *
     * No gamut mapping is performed: out-of-gamut values are truncated.
     */
    fun clamp(): RGB = copy(
        r = r.coerceIn(0f, 1f),
        g = g.coerceIn(0f, 1f),
        b = b.coerceIn(0f, 1f),
        alpha = alpha.coerceIn(0f, 1f)
    )

    override fun toHSL(): HSL {
        return srgbHueMinMaxChroma { h, min, max, chroma ->
            val l = (min + max) / 2.0
            val s = when {
                max == min -> 0.0
                l <= 0.5 -> chroma / (max + min)
                else -> chroma / (2 - max - min)
            }
            HSL(h.toFloat(), s.toFloat(), l.toFloat(), alpha)
        }
    }

    override fun toHSV(): HSV {
        return srgbHueMinMaxChroma { h, _, max, chroma ->
            val s = when (max) {
                0.0 -> 0.0
                else -> (chroma / max)
            }
            HSV(h.toFloat(), s.toFloat(), max.toFloat(), alpha)
        }
    }

    override fun toXYZ(): XYZ {
        val f = space.transferFunctions
        return Matrix(space.matrixToXyz).dot(f.eotf(r), f.eotf(g), f.eotf(b)) { x, y, z ->
            XYZColorSpace(space.whitePoint)(x, y, z, alpha)
        }
    }

    override fun toCMYK(): CMYK = toSRGB {
        val k = 1 - maxOf(r, b, g)
        if (k == 1f) return CMYK(0f, 0f, 0f, k, alpha)
        val c = (1 - r - k) / (1 - k)
        val m = (1 - g - k) / (1 - k)
        val y = (1 - b - k) / (1 - k)
        return CMYK(c, m, y, k, alpha)
    }

    override fun toHWB(): HWB {
        // https://www.w3.org/TR/css-color-4/#rgb-to-hwb
        return srgbHueMinMaxChroma { hue, min, max, _ ->
            HWB(
                h = hue.toFloat(),
                w = min.toFloat(),
                b = (1.0 - max).toFloat(),
                alpha = alpha
            )
        }
    }

    // https://bottosson.github.io/posts/oklab/#converting-from-linear-srgb-to-oklab
    override fun toOklab(): Oklab {
        if (space != RGBColorSpaces.SRGB) return toXYZ().toOklab()
        val r = space.transferFunctions.eotf(r)
        val g = space.transferFunctions.eotf(g)
        val b = space.transferFunctions.eotf(b)
        val l = 0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b
        val m = 0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b
        val s = 0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b

        val ll = cbrt(l)
        val mm = cbrt(m)
        val ss = cbrt(s)

        return Oklab(
            l = 0.2104542553f * ll + 0.7936177850f * mm - 0.0040720468f * ss,
            a = 1.9779984951f * ll - 2.4285922050f * mm + 0.4505937099f * ss,
            b = 0.0259040371f * ll + 0.7827717662f * mm - 0.8086757660f * ss,
            alpha = alpha
        )
    }

    override fun toICtCp(): ICtCp {
        if (space == BT2020) return convertBT2020ToICtCp(this)
        return toXYZ().toICtCp()
    }

    override fun toAnsi16(): Ansi16 = toSRGB {
        val value = (toHSV().v * 100).roundToInt()
        if (value == 30) return Ansi16(30)
        val v = value / 50

        val ansi = 30 + ((b.roundToInt() * 4) or (g.roundToInt() * 2) or r.roundToInt())
        return Ansi16(if (v == 2) ansi + 60 else ansi)
    }

    override fun toAnsi256(): Ansi256 = toSRGB {
        val ri = redInt
        val gi = greenInt
        val bi = blueInt
        // grayscale
        val code = if (ri == gi && gi == bi) {
            when {
                ri < 8 -> 16
                ri > 248 -> 231
                else -> (((ri - 8) / 247.0) * 24.0).roundToInt() + 232
            }
        } else {
            16 + (36 * (r * 5).roundToInt()) +
                    (6 * (g * 5).roundToInt()) +
                    (b * 5).roundToInt()
        }
        return Ansi256(code)
    }

    override fun toSRGB() = convertTo(RGBColorSpaces.SRGB)
    override fun toArray(): FloatArray = floatArrayOf(r, g, b, alpha)

    /**
     * Call [block] with the hue, min of color channels, max of color channels, and the
     * delta between min and max.
     *
     * Min and max are scaled to [0, 1]
     */
    private inline fun <T> srgbHueMinMaxChroma(
        block: (hue: Double, min: Double, max: Double, chroma: Double) -> T,
    ): T = toSRGB {
        val r = r.toDouble()
        val g = g.toDouble()
        val b = b.toDouble()
        val min = minOf(r, g, b)
        val max = maxOf(r, g, b)
        val chroma = max - min

        val h = when {
            chroma < 1e-7 -> Double.NaN
            r == max -> (g - b) / chroma
            g == max -> 2 + (b - r) / chroma
            b == max -> 4 + (r - g) / chroma
            else -> 0.0
        } * 60

        return block(h.normalizeDeg(), min, max, chroma)
    }

    private inline fun <T : Color> toSRGB(block: RGB.() -> T): T = convertTo(SRGB).block()
}


private fun String.validateHex() = apply {
    require(hexLength.let { it == 3 || it == 4 || it == 6 || it == 8 }) {
        "Hex string must be in the format \"#ffffff\" or \"ffffff\""
    }
}

private fun String.parseHex(startIndex: Int): Int {
    return if (hexLength > 4) {
        val i = if (this[0] == '#') startIndex * 2 + 1 else startIndex * 2
        slice(i..i + 1).toInt(16)
    } else {
        val i = if (this[0] == '#') startIndex + 1 else startIndex
        get(i).let { "$it$it" }.toInt(16)
    }
}

private val String.hexLength get() = if (startsWith("#")) length - 1 else length
