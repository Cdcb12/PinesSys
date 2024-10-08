package com.setvene.jm.pinessys.components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.TextView.OnEditorActionListener
import androidx.appcompat.widget.AppCompatImageView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textview.MaterialTextView
import com.setvene.jm.pinessys.R

class SetVeneEditText : TextInputEditText {
    constructor(context: Context?) : super(context!!)
    constructor(context: Context?, attrs: AttributeSet?) : super(context!!, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context!!, attrs, defStyleAttr)

    interface OnEnterKeyListener {
        fun onEnterKeyPressed(editText: SetVeneEditText, actionId: Int)
    }

    private var onEnterKeyListener: OnEnterKeyListener? = null

    fun setOnEnterKeyListener(listener: OnEnterKeyListener) {
        onEnterKeyListener = listener
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun init() {
        setOnEditorActionListener(OnEditorActionListener { _, actionId, _ ->
            onEnterKeyListener?.onEnterKeyPressed(this,actionId)
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                hideKeyboard()
                return@OnEditorActionListener true
            }
            false
        })

        setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val handler = Handler(Looper.getMainLooper())
                handler.postDelayed({
                    if (!isFocused) {
                        hideKeyboard()
                    }
                }, 100) // 100ms delay
            }
            false
        }

        setOnFocusChangeListener { view, hasFocus ->
            if (!hasFocus) {
                val nextView = view.focusSearch(View.FOCUS_DOWN)
                if (nextView != null && nextView is SetVeneEditText) {
                    nextView.requestFocus()
                    return@setOnFocusChangeListener
                }
                hideKeyboard()
            }
        }
    }

    fun hideKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(windowToken, 0)
        clearFocus()
    }

    init {
        init()
    }
}


class SetveneShadow(context: Context?, attrs: AttributeSet) : View(context!!, attrs) {
    private var shadowColorStart = 0
    private var shadowColorEnd = 0
    private var shadowSize = 0
    private var shadowPosition = 0
    private var shadowOpacityStart = 0f
    private lateinit var shadowDrawable: GradientDrawable

    init {
        init(attrs)
        elevation = 0f
    }

    private fun init(attrs: AttributeSet) {
        val a = context.theme.obtainStyledAttributes(
            attrs,
            R.styleable.SetveneShadow,
            0, 0
        )
        try {
            shadowColorStart = a.getColor(R.styleable.SetveneShadow_shadowColorStart, Color.BLACK)
            shadowColorEnd =
                a.getColor(R.styleable.SetveneShadow_shadowColorEnd, Color.TRANSPARENT)
            shadowOpacityStart = a.getFloat(R.styleable.SetveneShadow_shadowOpacity, 0.4f)
            shadowPosition = a.getInteger(R.styleable.SetveneShadow_shadowPosition, 4)
        } finally {
            a.recycle()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        shadowSize = height
        val shadowOpacity = shadowOpacityStart * (shadowSize / 100f)
        shadowColorStart = adjustOpacity(shadowColorStart, shadowOpacity)

        shadowDrawable = GradientDrawable(
            getOrientation(shadowPosition),
            intArrayOf(shadowColorStart, shadowColorEnd)
        )
        shadowDrawable.setBounds(0, 0, width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        shadowDrawable.draw(canvas)
    }

    private fun adjustOpacity(color: Int, opacity: Float): Int {
        val alpha = Math.round(Color.alpha(color) * opacity)
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)
        return Color.argb(alpha, red, green, blue)
    }

    private fun getOrientation(position: Int): GradientDrawable.Orientation {
        return when (position) {
            0 -> GradientDrawable.Orientation.BOTTOM_TOP
            1 -> GradientDrawable.Orientation.TOP_BOTTOM
            2 -> GradientDrawable.Orientation.RIGHT_LEFT
            3 -> GradientDrawable.Orientation.LEFT_RIGHT
            else -> GradientDrawable.Orientation.BOTTOM_TOP
        }
    }
}



@SuppressLint("CustomViewStyleable")
class SetveneTextView(context: Context?, attrs: AttributeSet) : MaterialTextView(context!!, attrs) {
    private var strikethrough: Boolean = false

    init {
        val typedArray = context!!.obtainStyledAttributes(attrs, R.styleable.SetveneTextView)
        strikethrough = typedArray.getBoolean(R.styleable.SetveneTextView_strikethrough, false)
        typedArray.recycle()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (strikethrough) {
            paint.flags = paint.flags or Paint.STRIKE_THRU_TEXT_FLAG
            canvas.drawText(text.toString(), 0, text.length, paddingStart.toFloat(), baseline.toFloat(), paint)
        }
    }
}


@SuppressLint("CustomViewStyleable")
class RoundedImageView(context: Context, attrs: AttributeSet) : AppCompatImageView(context, attrs) {
    private var cornerRadius: Float = 0f

    init {
        val a = context.theme.obtainStyledAttributes(
            attrs,
            R.styleable.RoundedImageView,
            0, 0
        )
        try {
            cornerRadius = a.getDimension(R.styleable.RoundedImageView_cornerRadius, 0f)
        } finally {
            a.recycle()
        }
    }

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        val path = Path()
        val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())
        path.addRoundRect(rect, cornerRadius, cornerRadius, Path.Direction.CW)
        canvas.clipPath(path)

        super.onDraw(canvas)
    }
}