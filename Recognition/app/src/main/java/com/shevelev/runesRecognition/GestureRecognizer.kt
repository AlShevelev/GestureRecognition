package com.shevelev.runesRecognition

import androidx.compose.ui.geometry.Offset
import com.shevelev.runesRecognition.Consts.KEY_POINT_COUNT
import com.shevelev.runesRecognition.Consts.NORMALIZATION_SQUARE_SIDE
import com.shevelev.runesRecognition.Consts.RECOGNITION_THRESHOLD
import com.shevelev.runesRecognition.Consts.ROUGH_FACTOR
import com.shevelev.runesRecognition.model.GesturePoint
import com.shevelev.runesRecognition.model.GestureTemplate
import com.shevelev.runesRecognition.model.RecognitionResult
import com.shevelev.runesRecognition.model.SelectedTemplate
import kotlin.math.hypot
import kotlin.math.pow

internal object GestureRecognizer {
    fun recognizeGesture(
        strokePoints: List<Offset>,
        templates: List<GestureTemplate>,
    ) : RecognitionResult? {
        val resampledPoints = resample(strokePoints, KEY_POINT_COUNT) ?: return null
        val normalizedPoints = scaleToSquare(resampledPoints, NORMALIZATION_SQUARE_SIDE)

        val bestTemplate = templates
            .map { SelectedTemplate(it, totalDistance(normalizedPoints, it.points)) }
            .minByOrNull { it.sumDistance } ?: return null

        val fuzziness = calculateFuzziness(bestTemplate.sumDistance)

        return fuzziness?.let {
            RecognitionResult(bestTemplate.template, it)
        }
    }

    /**
     * @return null - not recognized, [0-1] - the greater, the better
     */
    private fun calculateFuzziness(sumDistance: Float): Float? {
        val fuzziness = ((sumDistance / KEY_POINT_COUNT) /
                (0.5f * hypot(NORMALIZATION_SQUARE_SIDE, NORMALIZATION_SQUARE_SIDE)))

        if (fuzziness > RECOGNITION_THRESHOLD) return null

        return (1f - (fuzziness / RECOGNITION_THRESHOLD).coerceIn(0f, 1f)).pow(ROUGH_FACTOR)
    }

    private fun resample(
        strokePoints: List<Offset>,
        pointCount: Int
    ): List<GesturePoint>? {
        if (strokePoints.size < 2) {
            return null
        }

        val pathLength = strokePoints.zipWithNext { first, second ->
            hypot(
                (second.x - first.x).toDouble(),
                (second.y - first.y).toDouble()
            ).toFloat()
        }.sum()
        if (pathLength == 0f) {
            return null
        }

        val interval = pathLength / (pointCount - 1)
        val resampled = mutableListOf(strokePoints.first().toGesturePoint())
        var accumulatedDistance = 0f
        var previousPoint = strokePoints.first()

        strokePoints.drop(1).forEach { nextPoint ->
            var segmentStart = previousPoint
            var segmentLength = distance(segmentStart, nextPoint)

            while (
                resampled.size < pointCount - 1 &&
                accumulatedDistance + segmentLength >= interval
            ) {
                val ratio = (interval - accumulatedDistance) / segmentLength
                val interpolatedPoint = Offset(
                    x = segmentStart.x + ratio * (nextPoint.x - segmentStart.x),
                    y = segmentStart.y + ratio * (nextPoint.y - segmentStart.y)
                )
                resampled += interpolatedPoint.toGesturePoint()
                segmentStart = interpolatedPoint
                segmentLength = distance(segmentStart, nextPoint)
                accumulatedDistance = 0f
            }

            accumulatedDistance += segmentLength
            previousPoint = nextPoint
        }

        resampled += strokePoints.last().toGesturePoint()
        return resampled
    }

    private fun scaleToSquare(
        points: List<GesturePoint>,
        sideLength: Float
    ): List<GesturePoint> {
        val minimumRow = points.minOf { it.row }
        val maximumRow = points.maxOf { it.row }
        val minimumColumn = points.minOf { it.column }
        val maximumColumn = points.maxOf { it.column }
        val rowRange = maximumRow - minimumRow
        val columnRange = maximumColumn - minimumColumn

        return points.map { point ->
            GesturePoint(
                row = scaleCoordinate(point.row, minimumRow, rowRange, sideLength),
                column = scaleCoordinate(point.column, minimumColumn, columnRange, sideLength)
            )
        }
    }

    private fun scaleCoordinate(
        coordinate: Float,
        minimum: Float,
        range: Float,
        sideLength: Float
    ): Float = if (range == 0f) 0f else (coordinate - minimum) * sideLength / range

    private fun totalDistance(
        first: List<GesturePoint>,
        second: List<GesturePoint>
    ): Float = first.zip(second).sumOf { (firstPoint, secondPoint) ->
        hypot(
            (firstPoint.row - secondPoint.row).toDouble(),
            (firstPoint.column - secondPoint.column).toDouble()
        )
    }.toFloat()

    private fun distance(first: Offset, second: Offset): Float = hypot(
        (second.x - first.x).toDouble(),
        (second.y - first.y).toDouble()
    ).toFloat()

    private fun Offset.toGesturePoint(): GesturePoint = GesturePoint(row = y, column = x)
}