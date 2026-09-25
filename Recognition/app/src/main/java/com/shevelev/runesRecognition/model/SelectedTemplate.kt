package com.shevelev.runesRecognition.model

internal data class SelectedTemplate(
    val template: GestureTemplate,
    val sumDistance: Float,
)
