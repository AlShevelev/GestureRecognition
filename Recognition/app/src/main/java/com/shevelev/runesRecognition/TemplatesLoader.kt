package com.shevelev.runesRecognition

import android.content.res.Resources
import com.shevelev.runesRecognition.Consts.KEY_POINT_COUNT
import com.shevelev.runesRecognition.model.GesturePoint
import com.shevelev.runesRecognition.model.GestureTemplate

internal object TemplatesLoader {
    private val GESTURE_RESOURCES = listOf(
        R.raw.blessing to "blessing.gst",
        R.raw.blizzard to "blizzard.gst",
        R.raw.chain_lightning to "chain_lightning.gst",
        R.raw.cold_strike to "cold_strike.gst",
        R.raw.cure to "cure.gst",
        R.raw.electric_strike to "electric_strike.gst",
        R.raw.fire_ball to "fire_ball.gst",
        R.raw.fire_storm to "fire_storm.gst",
        R.raw.fire_strike to "fire_strike.gst",
        R.raw.ice_fog to "ice_fog.gst",
        R.raw.lightning to "lightning.gst",
        R.raw.oldness to "oldness.gst",
        R.raw.regeneration to "regeneration.gst",
        R.raw.summoning_a_demon to "summoning_a_demon.gst",
        R.raw.vampirism to "vampirism.gst",
        R.raw.weakness_to_cold to "weakness_to_cold.gst",
        R.raw.weakness_to_electricity to "weakness_to_electricity.gst",
        R.raw.weakness_to_fire to "weakness_to_fire.gst"
    )

    fun loadTemplates(resources: Resources): List<GestureTemplate> =
        GESTURE_RESOURCES.map { (resourceId, fileName) ->
            val points = resources.openRawResource(resourceId)
                .bufferedReader()
                .useLines { lines ->
                    lines.filter { it.isNotBlank() }
                        .map { line ->
                            val coordinates = line.split(',')
                            require(coordinates.size == 2) {
                                "Invalid point in $fileName: $line"
                            }
                            GesturePoint(
                                row = coordinates[0].trim().toFloat(),
                                column = coordinates[1].trim().toFloat()
                            )
                        }
                        .toList()
                }
            require(points.size == KEY_POINT_COUNT) {
                "$fileName must contain $KEY_POINT_COUNT points."
            }
            GestureTemplate(fileName, points)
        }
}