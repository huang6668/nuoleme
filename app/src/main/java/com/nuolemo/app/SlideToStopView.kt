package com.nuolemo.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import androidx.core.content.ContextCompat
import kotlin.math.abs

class SlideToStopView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {
    private val density = resources.displayMetrics.density
    private val trackBounds = RectF()
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val label = context.getString(R.string.alarm_activity_slide_to_stop)
    private val completionThreshold = 0.9f
    private val thumbInset = 6f * density
    private val touchAllowance = 12f * density

    private var progress = 0f
    private var dragging = false
    private var completed = false
    private var resetAnimator: ValueAnimator? = null
    private var onSlideCompleteListener: (() -> Unit)? = null

    init {
        isClickable = true
        isFocusable = true
        contentDescription = label

        trackPaint.color = ContextCompat.getColor(context, R.color.alarm_slide_track)
        thumbPaint.color = ContextCompat.getColor(context, R.color.alarm_button_bg)
        arrowPaint.apply {
            color = ContextCompat.getColor(context, android.R.color.white)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = 2.5f * density
            style = Paint.Style.STROKE
        }
        textPaint.apply {
            color = ContextCompat.getColor(context, R.color.alarm_text_primary)
            textAlign = Paint.Align.CENTER
            textSize = 16f * resources.displayMetrics.scaledDensity
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
    }

    fun setOnSlideCompleteListener(listener: () -> Unit) {
        onSlideCompleteListener = listener
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = (64f * density).toInt() + paddingTop + paddingBottom
        val measuredHeight = resolveSize(desiredHeight, heightMeasureSpec)
        val measuredWidth = resolveSize(suggestedMinimumWidth, widthMeasureSpec)
        setMeasuredDimension(measuredWidth, measuredHeight)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        trackBounds.set(
            paddingLeft.toFloat(),
            paddingTop.toFloat(),
            (width - paddingRight).toFloat(),
            (height - paddingBottom).toFloat(),
        )
        val trackRadius = trackBounds.height() / 2f
        canvas.drawRoundRect(trackBounds, trackRadius, trackRadius, trackPaint)

        val thumbRadius = (trackBounds.height() / 2f - thumbInset).coerceAtLeast(1f)
        val startCenter =
            if (layoutDirection == LAYOUT_DIRECTION_RTL) {
                trackBounds.right - thumbInset - thumbRadius
            } else {
                trackBounds.left + thumbInset + thumbRadius
            }
        val endCenter =
            if (layoutDirection == LAYOUT_DIRECTION_RTL) {
                trackBounds.left + thumbInset + thumbRadius
            } else {
                trackBounds.right - thumbInset - thumbRadius
            }
        val thumbCenterX = startCenter + (endCenter - startCenter) * progress
        val thumbCenterY = trackBounds.centerY()

        textPaint.alpha = ((1f - progress * 1.25f).coerceIn(0f, 1f) * 255).toInt()
        val textBaseline =
            trackBounds.centerY() - (textPaint.fontMetrics.ascent + textPaint.fontMetrics.descent) / 2f
        canvas.drawText(label, trackBounds.centerX(), textBaseline, textPaint)

        canvas.drawCircle(thumbCenterX, thumbCenterY, thumbRadius, thumbPaint)
        drawArrows(canvas, thumbCenterX, thumbCenterY, thumbRadius)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled || completed) {
            return false
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val thumbCenterX = thumbCenterX()
                val withinThumb =
                    abs(event.x - thumbCenterX) <= thumbRadius() + touchAllowance &&
                        event.y in trackBounds.top..trackBounds.bottom
                if (!withinThumb) {
                    return false
                }

                resetAnimator?.cancel()
                dragging = true
                parent?.requestDisallowInterceptTouchEvent(true)
                isPressed = true
                updateProgress(event.x)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!dragging) {
                    return false
                }
                updateProgress(event.x)
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (!dragging) {
                    return false
                }
                updateProgress(event.x)
                dragging = false
                isPressed = false
                parent?.requestDisallowInterceptTouchEvent(false)
                if (progress >= completionThreshold) {
                    performClick()
                } else {
                    animateBackToStart()
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                dragging = false
                isPressed = false
                parent?.requestDisallowInterceptTouchEvent(false)
                animateBackToStart()
                return true
            }
        }

        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        if (!completed) {
            completed = true
            progress = 1f
            invalidate()
            onSlideCompleteListener?.invoke()
        }
        return true
    }

    override fun getAccessibilityClassName(): CharSequence = Button::class.java.name

    private fun updateProgress(touchX: Float) {
        val start = startCenterX()
        val end = endCenterX()
        progress = ((touchX - start) / (end - start)).coerceIn(0f, 1f)
        invalidate()
    }

    private fun animateBackToStart() {
        resetAnimator?.cancel()
        resetAnimator =
            ValueAnimator.ofFloat(progress, 0f).apply {
                duration = 220L
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    progress = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
    }

    private fun drawArrows(canvas: Canvas, centerX: Float, centerY: Float, radius: Float) {
        val direction = if (layoutDirection == LAYOUT_DIRECTION_RTL) -1f else 1f
        val arrowWidth = radius * 0.28f
        val arrowHeight = radius * 0.36f
        val spacing = radius * 0.26f

        for (index in 0..1) {
            val arrowCenterX = centerX + direction * (index - 0.5f) * spacing
            val tipX = arrowCenterX + direction * arrowWidth / 2f
            val tailX = arrowCenterX - direction * arrowWidth / 2f
            canvas.drawLine(tailX, centerY - arrowHeight, tipX, centerY, arrowPaint)
            canvas.drawLine(tipX, centerY, tailX, centerY + arrowHeight, arrowPaint)
        }
    }

    private fun thumbCenterX(): Float = startCenterX() + (endCenterX() - startCenterX()) * progress

    private fun startCenterX(): Float {
        return if (layoutDirection == LAYOUT_DIRECTION_RTL) {
            trackBounds.right - thumbInset - thumbRadius()
        } else {
            trackBounds.left + thumbInset + thumbRadius()
        }
    }

    private fun endCenterX(): Float {
        return if (layoutDirection == LAYOUT_DIRECTION_RTL) {
            trackBounds.left + thumbInset + thumbRadius()
        } else {
            trackBounds.right - thumbInset - thumbRadius()
        }
    }

    private fun thumbRadius(): Float = (trackBounds.height() / 2f - thumbInset).coerceAtLeast(1f)
}
