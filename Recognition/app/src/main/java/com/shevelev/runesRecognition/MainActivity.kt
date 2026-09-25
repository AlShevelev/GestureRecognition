package com.shevelev.runesRecognition

import android.content.res.Resources
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.Density

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RunesRecognitionSurface()
        }
    }

    @Composable
    private fun RunesRecognitionSurface(resources: Resources = LocalResources.current) {
        var strokePoints by remember { mutableStateOf(emptyList<Offset>()) }

        val density = LocalDensity.current
        val templates = remember(resources) { TemplatesLoader.loadTemplates(resources) }

        val context = LocalContext.current

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val pointerId = down.id
                        var currentStrokePoints = listOf(down.position)
                        strokePoints = currentStrokePoints
                        var strokeEnded = false

                        while (true) {
                            val change = awaitPointerEvent()
                                .changes
                                .firstOrNull { it.id == pointerId }
                                ?: break

                            if (change.position != currentStrokePoints.last()) {
                                currentStrokePoints = currentStrokePoints + change.position
                                strokePoints = currentStrokePoints
                            }
                            if (!change.pressed) {
                                strokeEnded = true
                                break
                            }
                        }

                        if (strokeEnded) {
                            val result = GestureRecognizer.recognizeGesture(
                                currentStrokePoints,
                                templates,
                            )

                            val text = result?.let {
                                "${it.template.fileName}; Fuzziness [0-1]: ${it.fuzziness}"
                            } ?:
                                "Not recognized"

                            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
        ) {
            when {
                strokePoints.size > 1 -> {
                    val path = Path().apply {
                        moveTo(strokePoints.first().x, strokePoints.first().y)
                        strokePoints.drop(1).forEach { point ->
                            lineTo(point.x, point.y)
                        }
                    }
                    drawPath(
                        path = path,
                        color = Color.Black,
                        style = Stroke(width = getStrokeWidth(density))
                    )
                }
                strokePoints.size == 1 -> {
                    drawCircle(
                        color = Color.Black,
                        radius = getStrokeWidth(density) / 2f,
                        center = strokePoints.first()
                    )
                }
            }
        }
    }

    private fun getStrokeWidth(density: Density): Float = 10f / density.density
}