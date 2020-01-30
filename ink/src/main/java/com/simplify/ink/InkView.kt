/*
 * Copyright (c) 2016 Mastercard
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.simplify.ink

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import java.util.*
import kotlin.math.*

class InkView : View {
    // settings
    private var mFlags = 0
    private var mMaxStrokeWidth = 0f
    private var mMinStrokeWidth = 0f
    private var mSmoothingRatio = 0f
    // points
    private var pointQueue = ArrayList<InkPoint>()
    private var pointRecycle = ArrayList<InkPoint>()
    //--------------------------------------
// Util
//--------------------------------------
    // misc
    var density = 0f
    private var mBitmap: Bitmap? = null
    private var canvas: Canvas? = null
    private lateinit var paint: Paint
    private lateinit var dirty: RectF
    private var listeners = ArrayList<InkListener>()
    /**
     * Checks if the view is empty
     *
     * @return True of False
     */
    var isViewEmpty = false
        private set

    @JvmOverloads
    constructor(context: Context?, flags: Int = DEFAULT_FLAGS) : super(context) {
        init(flags)
    }

    @JvmOverloads
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int = 0) : super(context, attrs, defStyleAttr) {
        // get flags from attributes
        val a = getContext().obtainStyledAttributes(attrs, R.styleable.InkView, defStyleAttr, 0)
        val flags = a.getInt(R.styleable.InkView_inkFlags, DEFAULT_FLAGS)
        a.recycle()
        init(flags)
    }

    private fun init(flags: Int) { // init flags
        setFlags(flags)
        // init screen density
        val metrics = resources.displayMetrics
        density = (metrics.xdpi + metrics.ydpi) / 2f
        // init paint
        paint = Paint()
        paint.strokeCap = Paint.Cap.ROUND
        paint.isAntiAlias = true
        // apply default settings
        setColor(DEFAULT_STROKE_COLOR)
        setMaxStrokeWidth(DEFAULT_MAX_STROKE_WIDTH)
        setMinStrokeWidth(DEFAULT_MIN_STROKE_WIDTH)
        setSmoothingRatio(DEFAULT_SMOOTHING_RATIO)
        // init dirty rect
        dirty = RectF()
        isViewEmpty = true
    }

    //--------------------------------------
// Events
//--------------------------------------
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        clear()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val action = e.action
        isViewEmpty = false
        // on down, initialize stroke point
        if (action == MotionEvent.ACTION_DOWN) {
            addPoint(getRecycledPoint(e.x, e.y, e.eventTime))
            // notify listeners of sign
            for (listener in listeners) {
                listener.onInkDraw()
            }
        } else if (action == MotionEvent.ACTION_MOVE) {
            if (!pointQueue[pointQueue.size - 1].equals(e.x, e.y)) {
                addPoint(getRecycledPoint(e.x, e.y, e.eventTime))
            }
        }
        // on up, draw remaining queue
        if (action == MotionEvent.ACTION_UP) { // draw final points
            if (pointQueue.size == 1) {
                draw(pointQueue[0])
            } else if (pointQueue.size == 2) {
                pointQueue[1].findControlPoints(pointQueue[0], null)
                draw(pointQueue[0], pointQueue[1])
            }
            // recycle remaining points
            pointRecycle.addAll(pointQueue)
            pointQueue.clear()
        }
        return true
    }

    override fun onDraw(canvas: Canvas) { // simply paint the bitmap on the canvas
        mBitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        super.onDraw(canvas)
    }
    //--------------------------------------
// Public Methods
//--------------------------------------
    /**
     * Sets the feature flags for the view. This will overwrite any previously set flag
     *
     * @param flags A bit mask of one or more flags (ie. FLAG_INTERPOLATION | FLAG_RESPONSIVE_WIDTH)
     */
    fun setFlags(flags: Int) {
        this.mFlags = flags
    }

    /**
     * Adds the feature flag(s) to the view.
     *
     * @param flags A bit mask of one or more flags (ie. FLAG_INTERPOLATION | FLAG_RESPONSIVE_WIDTH)
     */
    fun addFlags(flags: Int) {
        this.mFlags = this.mFlags or flags
    }

    /**
     * Alias for [addFlags][.addFlags]
     *
     * @param flag A feature flag (ie. FLAG_INTERPOLATION)
     */
    fun addFlag(flag: Int) {
        addFlags(flag)
    }

    /**
     * Removes the feature flag(s) from the view.
     *
     * @param flags A bit mask of one or more flags (ie. FLAG_INTERPOLATION | FLAG_RESPONSIVE_WIDTH)
     */
    fun removeFlags(flags: Int) {
        this.mFlags = this.mFlags and flags.inv()
    }

    /**
     * Alias for [removeFlags][.removeFlags]
     *
     * @param flag A feature flag (ie. FLAG_INTERPOLATION)
     */
    fun removeFlag(flag: Int) {
        removeFlags(flag)
    }

    /**
     * Checks to see if the view has the supplied flag(s)
     *
     * @param flags A bit mask of one or more flags (ie. FLAG_INTERPOLATION | FLAG_RESPONSIVE_WIDTH)
     * @return True or False
     */
    fun hasFlags(flags: Int): Boolean {
        return this.mFlags and flags > 0
    }

    /**
     * Alias for [hasFlags][.hasFlags]
     *
     * @param flag A feature flag (ie. FLAG_INTERPOLATION)
     * @return True or False
     */
    fun hasFlag(flag: Int): Boolean {
        return hasFlags(flag)
    }

    /**
     * Clears all feature flags from the view
     */
    fun clearFlags() {
        mFlags = 0
    }

    /**
     * Adds a listener on the view
     *
     * @param listener The listener
     */
    fun addListener(listener: InkListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    /**
     * Removes the listener from the view
     *
     * @param listener The listener
     */
    fun removeListener(listener: InkListener?) {
        listeners.remove(listener)
    }

    /**
     * Sets the stroke color
     *
     * @param color The color value
     */
    fun setColor(color: Int) {
        paint.color = color
    }

    /**
     * Sets the maximum stroke width
     *
     * @param width The width (in dp)
     */
    fun setMaxStrokeWidth(width: Float) {
        mMaxStrokeWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, width, resources.displayMetrics)
    }

    /**
     * Sets the minimum stroke width
     *
     * @param width The width (in dp)
     */
    fun setMinStrokeWidth(width: Float) {
        mMinStrokeWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, width, resources.displayMetrics)
    }

    /**
     * Returns the smoothing ratio
     *
     * @return The smoothing ratio
     */
    fun getSmoothingRatio(): Float {
        return mSmoothingRatio
    }

    /**
     * Sets the smoothing ratio for calculating control points.
     * This value is ignored when the FLAG_INTERPOLATING is removed
     *
     * @param ratio The smoothing ratio, between 0 and 1
     */
    fun setSmoothingRatio(ratio: Float) {
        mSmoothingRatio = max(min(ratio, 1f), 0f)
    }

    /**
     * Clears the view
     */
    fun clear() {
        mBitmap?.recycle()
        // init bitmap cache
        mBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        mBitmap?.let {
            canvas = Canvas(it)
        }
        // notify listeners
        for (listener in listeners) {
            listener.onInkClear()
        }
        invalidate()
        isViewEmpty = true
    }

    /**
     * Returns the bitmap of the drawing with a transparent background
     *
     * @return The bitmap
     */
    fun getBitmap(): Bitmap {
        return getBitmap(0)
    }

    /**
     * Returns the bitmap of the drawing with the specified background color
     *
     * @param backgroundColor The background color for the bitmap
     * @return The bitmap
     */
    fun getBitmap(backgroundColor: Int): Bitmap { // create new bitmap
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val bitmapCanvas = Canvas(bitmap)
        // draw background if not transparent
        if (backgroundColor != 0) {
            bitmapCanvas.drawColor(backgroundColor)
        }
        // draw bitmap
        this.mBitmap?.let { bitmapCanvas.drawBitmap(it, 0f, 0f, null) }
        return bitmap
    }

    /**
     * Draws a bitmap to the view, with its top left corner at (x,y)
     *
     * @param bitmap The bitmap to draw
     * @param x      The destination x coordinate of the bitmap in relation to the view
     * @param y      The destination y coordinate of the bitmap in relation to the view
     * @param paint  The paint used to draw the bitmap (may be null)
     */
    fun drawBitmap(bitmap: Bitmap?, x: Float, y: Float, paint: Paint?) {
        bitmap?.let { canvas?.drawBitmap(it, x, y, paint) }
        invalidate()
    }
    //--------------------------------------
// Listener Interfaces
//--------------------------------------
    /**
     * Listener for the ink view to notify on actions
     */
    interface InkListener {
        /**
         * Callback method when the ink view has been cleared
         */
        fun onInkClear()

        /**
         * Callback method when the ink view receives a touch event
         * (Will be fired multiple times during a signing)
         */
        fun onInkDraw()
    }

    fun addPoint(p: InkPoint) {
        pointQueue.add(p)
        when (pointQueue.size) {
            1 -> { // compute starting velocity
                val recycleSize = pointRecycle.size
                p.velocity = if (recycleSize > 0) pointRecycle[recycleSize - 1].velocityTo(p) / 2f else 0f
                // compute starting stroke width
                paint.strokeWidth = computeStrokeWidth(p.velocity)
            }
            2 -> {
                val p0 = pointQueue[0]
                // compute velocity for new point
                p.velocity = p0.velocityTo(p)
                // re-compute velocity for 1st point (predictive velocity)
                p0.velocity = p0.velocity + p.velocity / 2f
                // find control points for first point
                p0.findControlPoints(null, p)
                // update starting stroke width
                paint.strokeWidth = computeStrokeWidth(p0.velocity)
            }
            3 -> {
                val p0 = pointQueue[0]
                val p1 = pointQueue[1]
                // find control points for second point
                p1.findControlPoints(p0, p)
                // compute velocity for new point
                p.velocity = p1.velocityTo(p)
                // draw geometry between first 2 points
                draw(p0, p1)
                // recycle 1st point
                pointRecycle.add(pointQueue.removeAt(0))
            }
        }
    }

    fun getRecycledPoint(x: Float, y: Float, time: Long): InkPoint {
        return if (pointRecycle.size == 0) {
            InkPoint(x, y, time)
        } else pointRecycle.removeAt(0).reset(x, y, time)
    }

    fun computeStrokeWidth(velocity: Float): Float { // compute responsive width
        return if (hasFlags(FLAG_RESPONSIVE_WIDTH)) {
            mMaxStrokeWidth - (mMaxStrokeWidth - mMinStrokeWidth) * min(velocity / THRESHOLD_VELOCITY, 1f)
        } else mMaxStrokeWidth
    }

    fun draw(p: InkPoint) {
        paint.style = Paint.Style.FILL
        // draw dot
        canvas?.drawCircle(p.x, p.y, paint.strokeWidth / 2f, paint)
        invalidate()
    }

    fun draw(p1: InkPoint, p2: InkPoint) { // init dirty rect
        dirty.left = min(p1.x, p2.x)
        dirty.right = max(p1.x, p2.x)
        dirty.top = min(p1.y, p2.y)
        dirty.bottom = max(p1.y, p2.y)
        paint.style = Paint.Style.STROKE
        // adjust low-pass ratio from changing acceleration
// using comfortable range of 0.2 -> 0.3 approx.
        val acceleration = abs((p2.velocity - p1.velocity) / (p2.time - p1.time)) // in/s^2
        val filterRatio = min(FILTER_RATIO_MIN + FILTER_RATIO_ACCELERATION_MODIFIER * acceleration / THRESHOLD_ACCELERATION, 1f)
        // compute new stroke width
        val desiredWidth = computeStrokeWidth(p2.velocity)
        val startWidth = paint.strokeWidth
        val endWidth = filterRatio * desiredWidth + (1f - filterRatio) * startWidth
        val deltaWidth = endWidth - startWidth
        // interpolate bezier curve
        if (hasFlags(FLAG_INTERPOLATION)) { // compute # of steps to interpolate in the bezier curve
            val steps = (sqrt((p2.x - p1.x.toDouble()).pow(2.0) + (p2.y - p1.y.toDouble()).pow(2.0)) / 5).toInt()
            // computational setup for differentials used to interpolate the bezier curve
            val u = 1f / (steps + 1)
            val uu = u * u
            val uuu = u * u * u
            val pre1 = 3f * u
            val pre2 = 3f * uu
            val pre3 = 6f * uu
            val pre4 = 6f * uuu
            val tmp1x = p1.x - p1.c2x * 2f + p2.c1x
            val tmp1y = p1.y - p1.c2y * 2f + p2.c1y
            val tmp2x = (p1.c2x - p2.c1x) * 3f - p1.x + p2.x
            val tmp2y = (p1.c2y - p2.c1y) * 3f - p1.y + p2.y
            var dx = (p1.c2x - p1.x) * pre1 + tmp1x * pre2 + tmp2x * uuu
            var dy = (p1.c2y - p1.y) * pre1 + tmp1y * pre2 + tmp2y * uuu
            var ddx = tmp1x * pre3 + tmp2x * pre4
            var ddy = tmp1y * pre3 + tmp2y * pre4
            val dddx = tmp2x * pre4
            val dddy = tmp2y * pre4
            var x1 = p1.x
            var y1 = p1.y
            var x2: Float
            var y2: Float
            // iterate over each step and draw the curve
            var i = 0
            while (i++ < steps) {
                x2 = x1 + dx
                y2 = y1 + dy
                paint.strokeWidth = startWidth + deltaWidth * i / steps
                canvas?.drawLine(x1, y1, x2, y2, paint)
                x1 = x2
                y1 = y2
                dx += ddx
                dy += ddy
                ddx += dddx
                ddy += dddy
                // adjust dirty bounds to account for curve
                dirty.left = min(dirty.left, x1)
                dirty.right = max(dirty.right, x1)
                dirty.top = min(dirty.top, y1)
                dirty.bottom = max(dirty.bottom, y1)
            }
            paint.strokeWidth = endWidth
            canvas?.drawLine(x1, y1, p2.x, p2.y, paint)
        } else {
            canvas?.drawLine(p1.x, p1.y, p2.x, p2.y, paint)
            paint.strokeWidth = endWidth
        }
        invalidate()
        //invalidate((dirty.left - mMaxStrokeWidth / 2).toInt(), (dirty.top - mMaxStrokeWidth / 2).toInt(), (dirty.right + mMaxStrokeWidth / 2).toInt(), (dirty.bottom + mMaxStrokeWidth / 2).toInt())
    }

    //--------------------------------------
// Util Classes
//--------------------------------------
    inner class InkPoint(x: Float, y: Float, time: Long) {
        var x = 0f
        var y = 0f
        var c1x = 0f
        var c1y = 0f
        var c2x = 0f
        var c2y = 0f
        var velocity = 0f
        var time: Long = 0
        fun reset(x: Float, y: Float, time: Long): InkPoint {
            this.x = x
            this.y = y
            this.time = time
            velocity = 0f
            c1x = x
            c1y = y
            c2x = x
            c2y = y
            return this
        }

        fun equals(x: Float, y: Float): Boolean {
            return this.x == x && this.y == y
        }

        fun distanceTo(p: InkPoint): Float {
            val dx = p.x - x
            val dy = p.y - y
            return sqrt(dx * dx + dy * dy.toDouble()).toFloat()
        }

        fun velocityTo(p: InkPoint): Float {
            return 1000f * distanceTo(p) / (abs(p.time - time) * density) // in/s
        }

        fun findControlPoints(prev: InkPoint?, next: InkPoint?) {
            if (prev == null && next == null) {
                return
            }

            try {
                var r = getSmoothingRatio()
                // if start of a stroke, c2 control points half-way between this and next point
                if (prev == null) {
                    c2x = x + r * (next!!.x - x) / 2f
                    c2y = y + r * (next.y - y) / 2f
                    return
                }
                // if end of a stroke, c1 control points half-way between this and prev point
                if (next == null) {
                    c1x = x + r * (prev.x - x) / 2f
                    c1y = y + r * (prev.y - y) / 2f
                    return
                }
                // init control points
                c1x = (x + prev.x) / 2f
                c1y = (y + prev.y) / 2f
                c2x = (x + next.x) / 2f
                c2y = (y + next.y) / 2f
                // calculate control offsets
                val len1 = distanceTo(prev)
                val len2 = distanceTo(next)
                val k = len1 / (len1 + len2)
                val xM = c1x + (c2x - c1x) * k
                val yM = c1y + (c2y - c1y) * k
                val dx = x - xM
                val dy = y - yM
                // inverse smoothing ratio
                r = 1f - r
                // translate control points
                c1x += dx + r * (xM - c1x)
                c1y += dy + r * (yM - c1y)
                c2x += dx + r * (xM - c2x)
                c2y += dy + r * (yM - c2y)
            } catch (ignore: Exception) {
            }
        }

        init {
            reset(x, y, time)
        }
    }

    companion object {
        /**
         * The default maximum stroke width (dp).
         * Will be used as the standard stroke width if FLAG_RESPONSIVE_WIDTH is removed
         */
        const val DEFAULT_MAX_STROKE_WIDTH = 5f
        /**
         * The default minimum stroke width (dp)
         */
        const val DEFAULT_MIN_STROKE_WIDTH = 1.5f
        /**
         * The default smoothing ratio for calculating the control points for the bezier curves.
         * Will be ignored if FLAG_INTERPOLATION is removed
         */
        const val DEFAULT_SMOOTHING_RATIO = 0.75f
        /**
         * When this flag is added, paths will be drawn as cubic-bezier curves
         */
        const val FLAG_INTERPOLATION = 1
        /**
         * When present, the width of the paths will be responsive to the velocity of the stroke.
         * When missing, the width of the path will be the the max stroke width
         */
        const val FLAG_RESPONSIVE_WIDTH = 1 shl 1
        /**
         * When present, the data points for the path are drawn with their respective control points
         *
         */

        const val THRESHOLD_VELOCITY = 7f // in/s
        const val THRESHOLD_ACCELERATION = 3f // in/s^2
        const val FILTER_RATIO_MIN = 0.22f
        const val FILTER_RATIO_ACCELERATION_MODIFIER = 0.1f
        const val DEFAULT_FLAGS = FLAG_INTERPOLATION or FLAG_RESPONSIVE_WIDTH
        const val DEFAULT_STROKE_COLOR = -0x1000000
    }
}