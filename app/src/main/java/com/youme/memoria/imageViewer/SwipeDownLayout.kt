package com.youme.memoria.imageViewer

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

class SwipeDownLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var initialX = 0f
    private var initialY = 0f
    private var isDraggingDown = false
    var canSwipeDown: () -> Boolean = { true }

    var onDismiss: (() -> Unit)? = null
    var onDragProgress: ((progress: Float , max : Float) -> Unit)? = null

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (!canSwipeDown()) return false

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialX = ev.rawX
                initialY = ev.rawY
                isDraggingDown = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.rawX - initialX
                val dy = ev.rawY - initialY

                if (dy > touchSlop && dy > abs(dx)) {
                    isDraggingDown = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDraggingDown = false
            }
        }
        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isDraggingDown && !onInterceptTouchEvent(event)) {
            return super.onTouchEvent(event)
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                val dy = (event.rawY - initialY).coerceAtLeast(0f)

                translationY = dy

                val progress = (dy / height).coerceIn(0f, 1f)
                onDragProgress?.invoke(progress,0.25f)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val dismissThreshold = height * 0.25f

                if (translationY > dismissThreshold) {

                    animate()
                        .translationY(height.toFloat())
                        .alpha(0f)
                        .setDuration(200)
                        .setListener(object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator) {
                                onDismiss?.invoke()
                            }
                        })
                } else {
                    animate()
                        .translationY(0f)
                        .setDuration(200)
                        .setListener(null)
                    onDragProgress?.invoke(0f,0f)
                }
                isDraggingDown = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}