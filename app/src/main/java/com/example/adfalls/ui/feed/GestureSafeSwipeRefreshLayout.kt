package com.example.adfalls.ui.feed

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlin.math.abs

class GestureSafeSwipeRefreshLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SwipeRefreshLayout(context, attrs) {
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var horizontalGestureLocked = false

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                horizontalGestureLocked = false
            }

            MotionEvent.ACTION_MOVE -> {
                if (isHorizontalIntent(event)) {
                    horizontalGestureLocked = true
                    return false
                }
                if (horizontalGestureLocked) return false
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> horizontalGestureLocked = false
        }
        return super.onInterceptTouchEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (horizontalGestureLocked) {
            if (event.actionMasked == MotionEvent.ACTION_UP ||
                event.actionMasked == MotionEvent.ACTION_CANCEL
            ) {
                horizontalGestureLocked = false
            }
            return false
        }
        return super.onTouchEvent(event)
    }

    private fun isHorizontalIntent(event: MotionEvent): Boolean {
        val dx = event.x - downX
        val dy = event.y - downY
        return abs(dx) > touchSlop &&
            abs(dx) > abs(dy) * HORIZONTAL_INTENT_RATIO
    }

    private companion object {
        private const val HORIZONTAL_INTENT_RATIO = 1.35f
    }
}
