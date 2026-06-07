package com.example.adfalls.ui.common

import android.view.View
import android.view.ViewGroup
import kotlin.math.roundToInt

private const val HORIZONTAL_CONTENT_PERCENT = 0.10f
private const val MIN_HORIZONTAL_DP = 32f
private const val MAX_HORIZONTAL_DP = 56f

fun View.applyResponsiveHorizontalPadding(
    percent: Float = HORIZONTAL_CONTENT_PERCENT,
    minDp: Float = MIN_HORIZONTAL_DP,
    maxDp: Float = MAX_HORIZONTAL_DP
) {
    val horizontal = responsiveHorizontalSpacePx(percent, minDp, maxDp)
    setPaddingRelative(horizontal, paddingTop, horizontal, paddingBottom)
}

fun View.applyResponsiveEndMargin() {
    val margin = responsiveHorizontalSpacePx()
    val params = layoutParams as? ViewGroup.MarginLayoutParams ?: return
    params.marginEnd = margin
    layoutParams = params
}

fun View.applyScreenPercentHorizontalPadding(percent: Float) {
    val horizontal = screenWidthPercentPx(percent)
    setPaddingRelative(horizontal, paddingTop, horizontal, paddingBottom)
}

fun View.screenWidthPercentPx(percent: Float): Int =
    (resources.displayMetrics.widthPixels * percent).roundToInt()

private fun View.responsiveHorizontalSpacePx(
    percent: Float = HORIZONTAL_CONTENT_PERCENT,
    minDp: Float = MIN_HORIZONTAL_DP,
    maxDp: Float = MAX_HORIZONTAL_DP
): Int {
    val density = resources.displayMetrics.density
    val min = (minDp * density).roundToInt()
    val max = (maxDp * density).roundToInt()
    return (resources.displayMetrics.widthPixels * percent)
        .roundToInt()
        .coerceIn(min, max)
}
