package com.shevelev.runesRecognition.model

internal data class RecognitionResult(
    val template: GestureTemplate,
    val fuzziness: Float
)