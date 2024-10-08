package com.setvene.jm.pinessys.ui

import android.content.res.Resources
import android.util.TypedValue

fun Int.toPx(): Int {
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        this.toFloat(),
        Resources.getSystem().displayMetrics
    ).toInt()
}
