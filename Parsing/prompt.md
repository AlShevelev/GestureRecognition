# General
Write a Python application in one single file.

The application must process PNG files one by one. For every processed file the result must be put to a text file with the same name, but with "gst" extention.

# Input path
Get all the source files from this folder: @input

# Output path
Put the result files to this folder: @output

# Processing of one file
Each file contains a set of lines represented by non-transparent pixels. The lines intersect to form a path.

The path traversal order is determined by red pixels (where the R value in RGB is greater than zero). The starting point of the path is defined by the red pixel with the highest R value, while the ending point is defined by the red pixel with the lowest R value. Thus, the path is traversed in the order of decreasing R values ​​of the red pixels.

For these paths, generate a list of 64 key points in accordance with the "$1 Gesture Recognizer" algorithm. The first point in the list must correspond to the path's starting point, and the last point in the list must correspond to the path's ending point.

Write the coordinates of the points from the resulting list to a text file. The filename must match that of the PNG file being processed, with the extension changed to "gst". The coordinates must be recorded as row and column indices separated by commas. Write each coordinate on a new line. Each output file must contain 64 rows.
