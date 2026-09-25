package com.shevelev.runesRecognition.model

internal data class GestureTemplate(
    val fileName: String,
    val points: List<GesturePoint>
)
