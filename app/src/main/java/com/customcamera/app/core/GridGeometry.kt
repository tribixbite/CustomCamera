package com.customcamera.app.core

/**
 * Line positions for composition grids, as fractions (0..1) of the frame.
 * Kept separate from the View so the geometry is unit-testable.
 */
object GridGeometry {

    /** 1/φ² ≈ 0.382 — golden-ratio ("phi grid") divisions. */
    private const val PHI_MINOR = 0.381966f

    data class Lines(val vertical: List<Float>, val horizontal: List<Float>)

    /**
     * @param width frame width (any unit) — used for SQUARE so cells are square.
     * @param height frame height.
     */
    fun lines(option: GridOption, width: Float, height: Float): Lines = when (option) {
        GridOption.OFF -> Lines(emptyList(), emptyList())
        GridOption.THIRDS -> Lines(listOf(1f / 3, 2f / 3), listOf(1f / 3, 2f / 3))
        GridOption.GOLDEN -> Lines(listOf(PHI_MINOR, 1 - PHI_MINOR), listOf(PHI_MINOR, 1 - PHI_MINOR))
        GridOption.SQUARE -> squareLines(width, height)
    }

    /** Four columns of square cells, rows centred vertically. */
    private fun squareLines(width: Float, height: Float): Lines {
        if (width <= 0f || height <= 0f) return Lines(emptyList(), emptyList())
        val columns = 4
        val cell = width / columns
        val vertical = (1 until columns).map { it.toFloat() / columns }
        val rows = (height / cell).toInt()
        val offset = (height - rows * cell) / 2
        val horizontal = (0..rows).map { (offset + it * cell) / height }.filter { it > 0.001f && it < 0.999f }
        return Lines(vertical, horizontal)
    }
}
