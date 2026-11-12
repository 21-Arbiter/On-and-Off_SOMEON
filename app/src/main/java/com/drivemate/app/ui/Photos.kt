package com.drivemate.app.ui

import androidx.annotation.DrawableRes
import com.drivemate.app.R

/** The picture for a photo name in a profile ([com.drivemate.core.Profile]); null for names the app doesn't have. */
@DrawableRes
fun photoRes(name: String?): Int? = when (name) {
    "jiyeon" -> R.drawable.profile_jiyeon
    "night" -> R.drawable.interest_night
    "food" -> R.drawable.interest_food
    "road" -> R.drawable.interest_road
    "cafe" -> R.drawable.place_cafe
    else -> null
}
