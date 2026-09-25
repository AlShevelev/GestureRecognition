# General
The app for android devices detects user's one-finger gesture and draws the gesture on the screen. 

There are several files in the "raw" resources folder. Each file contains 64 pair of integers separated by comma.

# Frameworks
Kotlin and Jetpack Compose.

# Restrictions
Do not add any tests.

# What the app must do
In addition to drawing the user's gesture, the drawn gesture must be broken down into 64 key points using the "$1 Gesture Recognizer" algorithm. The first point corresponds to the start of the gesture, and the last point corresponds to the end.

To perform this segmentation, the gesture must be scaled to fit entirely within a square with a side length of 251 points (without maintaining the aspect ratio). The key points should be stored as pairs of row and column indices, where the row index corresponds to the Y-coordinate and the column index corresponds to the X-coordinate.

The coordinates of the stored key points must be compared sequentially against the coordinates found in each file within the "raw" directory. To do this, calculate the total geometric distance between each key point and its corresponding point in the file. Finally, output the name of the file with the smallest total distance to the console using the standard Android logger.