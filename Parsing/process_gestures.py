#!/usr/bin/env python3
"""Convert raster gesture paths in input PNG files to 64-point GST files."""

from __future__ import annotations

import argparse
import heapq
import math
import os
import sys
from collections.abc import Iterable
from pathlib import Path
from tempfile import NamedTemporaryFile

try:
    from PIL import Image
except ImportError as error:
    raise SystemExit(
        "Pillow is required to read PNG files. Install it with: python3 -m pip install Pillow"
    ) from error


Point = tuple[int, int]
FloatPoint = tuple[float, float]
NEIGHBOR_OFFSETS = tuple(
    (row_offset, column_offset)
    for row_offset in (-1, 0, 1)
    for column_offset in (-1, 0, 1)
    if (row_offset, column_offset) != (0, 0)
)
POINT_COUNT = 64


def load_path_pixels(image_path: Path) -> tuple[set[Point], dict[Point, int]]:
    """Return opaque path pixels and red marker values as row/column coordinates."""
    with Image.open(image_path) as image:
        rgba_image = image.convert("RGBA")
        width, height = rgba_image.size
        pixels = rgba_image.load()

        path_pixels: set[Point] = set()
        markers: dict[Point, int] = {}
        for row in range(height):
            for column in range(width):
                red, _green, _blue, alpha = pixels[column, row]
                if alpha == 0:
                    continue
                point = (row, column)
                path_pixels.add(point)
                if red > 0:
                    markers[point] = red

    if not path_pixels:
        raise ValueError("contains no non-transparent path pixels")
    if len(markers) < 2:
        raise ValueError("must contain at least two red marker pixels")
    return path_pixels, markers


def neighbors(point: Point, path_pixels: set[Point]) -> Iterable[Point]:
    row, column = point
    for row_offset, column_offset in NEIGHBOR_OFFSETS:
        neighbor = (row + row_offset, column + column_offset)
        if neighbor in path_pixels:
            yield neighbor


def shortest_path(
    source: Point,
    destination: Point,
    path_pixels: set[Point],
    blocked_markers: set[Point],
    visited_pixels: set[Point],
) -> list[Point] | None:
    """Find a path that favors unused pixels and never crosses later markers."""
    queue: list[tuple[int, int, int, int, Point]] = [(0, 0, source[0], source[1], source)]
    predecessors: dict[Point, Point | None] = {source: None}
    costs: dict[Point, tuple[int, int]] = {source: (0, 0)}

    while queue:
        reused_count, step_count, _row, _column, point = heapq.heappop(queue)
        if (reused_count, step_count) != costs[point]:
            continue
        if point == destination:
            path: list[Point] = []
            while point is not None:
                path.append(point)
                point = predecessors[point]
            return list(reversed(path))

        for neighbor in neighbors(point, path_pixels):
            if neighbor in blocked_markers and neighbor != destination:
                continue
            candidate_cost = (
                reused_count + int(neighbor in visited_pixels),
                step_count + 1,
            )
            if candidate_cost >= costs.get(neighbor, (math.inf, math.inf)):
                continue
            costs[neighbor] = candidate_cost
            predecessors[neighbor] = point
            heapq.heappush(
                queue,
                (
                    candidate_cost[0],
                    candidate_cost[1],
                    neighbor[0],
                    neighbor[1],
                    neighbor,
                ),
            )
    return None


def select_next_segment(
    current: Point,
    unvisited_markers: dict[Point, int],
    path_pixels: set[Point],
    visited_pixels: set[Point],
) -> tuple[Point, list[Point]]:
    """Select the highest-red reachable marker with graph-continuous tie breaking."""
    next_red = max(unvisited_markers.values())
    candidates = sorted(
        point for point, red_value in unvisited_markers.items() if red_value == next_red
    )
    blocked_markers = set(unvisited_markers)

    segments: list[tuple[int, int, int, int, Point, list[Point]]] = []
    for candidate in candidates:
        segment = shortest_path(
            current,
            candidate,
            path_pixels,
            blocked_markers - {candidate},
            visited_pixels,
        )
        if segment is None:
            continue
        reused_count = sum(point in visited_pixels for point in segment[1:])
        segments.append(
            (reused_count, len(segment), candidate[0], candidate[1], candidate, segment)
        )

    if not segments:
        raise ValueError(
            f"cannot reach a remaining marker with red value {next_red} "
            "without crossing a later marker"
        )
    _reused, _length, _row, _column, marker, segment = min(segments)
    return marker, segment


def reconstruct_path(path_pixels: set[Point], markers: dict[Point, int]) -> list[Point]:
    """Join marker-to-marker segments in decreasing red order."""
    highest_red = max(markers.values())
    starts = sorted(point for point, value in markers.items() if value == highest_red)
    if len(starts) != 1:
        raise ValueError("must have exactly one highest-red starting marker")

    current = starts[0]
    remaining = dict(markers)
    del remaining[current]
    ordered_path = [current]
    visited_pixels = {current}

    while remaining:
        next_marker, segment = select_next_segment(
            current, remaining, path_pixels, visited_pixels
        )
        ordered_path.extend(segment[1:])
        visited_pixels.update(segment)
        del remaining[next_marker]
        current = next_marker

    return ordered_path


def resample(points: list[Point], point_count: int = POINT_COUNT) -> list[FloatPoint]:
    """Interpolate points at equal distances along an ordered polyline."""
    if len(points) < 2:
        raise ValueError("path must contain at least two distinct pixels")

    segment_lengths = [math.dist(start, end) for start, end in zip(points, points[1:])]
    total_length = sum(segment_lengths)
    if total_length == 0:
        raise ValueError("path has zero length")

    targets = [total_length * index / (point_count - 1) for index in range(point_count)]
    samples: list[FloatPoint] = []
    segment_index = 0
    distance_before_segment = 0.0

    for target in targets:
        while (
            segment_index < len(segment_lengths) - 1
            and target > distance_before_segment + segment_lengths[segment_index]
        ):
            distance_before_segment += segment_lengths[segment_index]
            segment_index += 1

        start = points[segment_index]
        end = points[segment_index + 1]
        segment_length = segment_lengths[segment_index]
        fraction = (target - distance_before_segment) / segment_length
        samples.append(
            (
                start[0] + (end[0] - start[0]) * fraction,
                start[1] + (end[1] - start[1]) * fraction,
            )
        )

    samples[0] = points[0]
    samples[-1] = points[-1]
    return samples


def round_point(point: FloatPoint) -> Point:
    return (math.floor(point[0] + 0.5), math.floor(point[1] + 0.5))


def write_gst(output_path: Path, points: list[FloatPoint]) -> None:
    rounded_points = [round_point(point) for point in points]
    lines = "".join(f"{row},{column}\n" for row, column in rounded_points)
    output_path.parent.mkdir(parents=True, exist_ok=True)

    with NamedTemporaryFile(
        "w", encoding="utf-8", dir=output_path.parent, delete=False
    ) as temporary_file:
        temporary_file.write(lines)
        temporary_path = Path(temporary_file.name)
    os.replace(temporary_path, output_path)


def process_file(input_path: Path, output_directory: Path) -> Path:
    path_pixels, markers = load_path_pixels(input_path)
    ordered_path = reconstruct_path(path_pixels, markers)
    points = resample(ordered_path)
    output_path = output_directory / input_path.with_suffix(".gst").name
    write_gst(output_path, points)
    return output_path


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Convert PNG gesture paths from input/ to 64-point GST files in output/."
    )
    script_directory = Path(__file__).resolve().parent
    parser.add_argument(
        "--input",
        type=Path,
        default=script_directory / "input",
        help="directory containing source PNG files (default: script-relative input/)",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=script_directory / "output",
        help="directory for generated GST files (default: script-relative output/)",
    )
    arguments = parser.parse_args()

    input_directory = arguments.input.resolve()
    output_directory = arguments.output.resolve()
    if not input_directory.is_dir():
        parser.error(f"input directory does not exist: {input_directory}")

    input_paths = sorted(input_directory.glob("*.png"))
    if not input_paths:
        parser.error(f"no PNG files found in: {input_directory}")

    failures: list[str] = []
    for input_path in input_paths:
        try:
            output_path = process_file(input_path, output_directory)
            print(f"{input_path.name} -> {output_path.name}")
        except (OSError, ValueError) as error:
            failures.append(f"{input_path.name}: {error}")

    if failures:
        print("Failed to process:", file=sys.stderr)
        print("\n".join(failures), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
