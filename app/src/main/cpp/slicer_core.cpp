// SPDX-License-Identifier: AGPL-3.0-only
#include "slicer_core.h"

#include <algorithm>
#include <array>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <fstream>
#include <iomanip>
#include <limits>
#include <sstream>
#include <string>
#include <utility>
#include <vector>

namespace feresa_slicer {
namespace {

constexpr double kGeometryEpsilon = 1e-7;
constexpr double kGcodeResolutionMm = 1e-4;
constexpr double kJoinToleranceMm = 0.03;
constexpr std::size_t kMaximumTriangleCount = 5'000'000;
constexpr double kPi = 3.14159265358979323846;

struct Vec3 {
    double x = 0.0;
    double y = 0.0;
    double z = 0.0;
};

struct Point2 {
    double x = 0.0;
    double y = 0.0;
};

struct Triangle {
    std::array<Vec3, 3> vertices{};
};

struct Segment {
    Point2 a;
    Point2 b;
};

double squared_distance(const Point2& a, const Point2& b) {
    const double dx = a.x - b.x;
    const double dy = a.y - b.y;
    return dx * dx + dy * dy;
}

double distance(const Point2& a, const Point2& b) {
    return std::sqrt(squared_distance(a, b));
}

std::uint32_t read_u32_le(const unsigned char* bytes) {
    return static_cast<std::uint32_t>(bytes[0]) |
           (static_cast<std::uint32_t>(bytes[1]) << 8U) |
           (static_cast<std::uint32_t>(bytes[2]) << 16U) |
           (static_cast<std::uint32_t>(bytes[3]) << 24U);
}

float read_f32_le(const unsigned char* bytes) {
    const std::uint32_t bits = read_u32_le(bytes);
    float value = 0.0F;
    static_assert(sizeof(value) == sizeof(bits));
    std::memcpy(&value, &bits, sizeof(value));
    return value;
}

bool read_binary_stl(
    const std::string& path,
    std::uint64_t file_size,
    std::vector<Triangle>& triangles
) {
    if (file_size < 84U) {
        return false;
    }

    std::ifstream stream(path, std::ios::binary);
    if (!stream) {
        return false;
    }

    std::array<unsigned char, 84> header{};
    stream.read(reinterpret_cast<char*>(header.data()),
                static_cast<std::streamsize>(header.size()));
    if (!stream) {
        return false;
    }

    const std::uint32_t count = read_u32_le(header.data() + 80U);
    const std::uint64_t expected_size = 84ULL + static_cast<std::uint64_t>(count) * 50ULL;
    if (count == 0U || count > kMaximumTriangleCount || expected_size > file_size) {
        return false;
    }

    triangles.clear();
    triangles.reserve(count);
    std::array<unsigned char, 50> record{};
    for (std::uint32_t index = 0; index < count; ++index) {
        stream.read(reinterpret_cast<char*>(record.data()),
                    static_cast<std::streamsize>(record.size()));
        if (!stream) {
            triangles.clear();
            return false;
        }

        Triangle triangle;
        for (std::size_t vertex = 0; vertex < 3; ++vertex) {
            const std::size_t offset = 12U + vertex * 12U;
            triangle.vertices[vertex] = {
                static_cast<double>(read_f32_le(record.data() + offset)),
                static_cast<double>(read_f32_le(record.data() + offset + 4U)),
                static_cast<double>(read_f32_le(record.data() + offset + 8U)),
            };
        }
        triangles.push_back(triangle);
    }
    return true;
}

bool read_ascii_stl(const std::string& path, std::vector<Triangle>& triangles) {
    std::ifstream stream(path);
    if (!stream) {
        return false;
    }

    triangles.clear();
    std::string token;
    std::array<Vec3, 3> vertices{};
    std::size_t vertex_index = 0;
    while (stream >> token) {
        if (token != "vertex") {
            continue;
        }

        Vec3 vertex;
        if (!(stream >> vertex.x >> vertex.y >> vertex.z)) {
            triangles.clear();
            return false;
        }

        vertices[vertex_index++] = vertex;
        if (vertex_index == vertices.size()) {
            triangles.push_back({vertices});
            vertex_index = 0;
            if (triangles.size() > kMaximumTriangleCount) {
                triangles.clear();
                return false;
            }
        }
    }
    return !triangles.empty() && vertex_index == 0;
}

bool load_stl(const std::string& path, std::vector<Triangle>& triangles) {
    std::ifstream size_stream(path, std::ios::binary | std::ios::ate);
    if (!size_stream) {
        return false;
    }
    const auto end = size_stream.tellg();
    if (end <= 0) {
        return false;
    }
    const auto file_size = static_cast<std::uint64_t>(end);
    size_stream.close();

    return read_binary_stl(path, file_size, triangles) || read_ascii_stl(path, triangles);
}

void add_unique_intersection(std::vector<Point2>& points, const Point2& candidate) {
    const double tolerance_squared = kGeometryEpsilon * kGeometryEpsilon;
    const bool duplicate = std::any_of(points.begin(), points.end(), [&](const Point2& point) {
        return squared_distance(point, candidate) <= tolerance_squared;
    });
    if (!duplicate) {
        points.push_back(candidate);
    }
}

void intersect_edge(const Vec3& a, const Vec3& b, double z, std::vector<Point2>& points) {
    const double da = a.z - z;
    const double db = b.z - z;
    const bool a_on_plane = std::abs(da) <= kGeometryEpsilon;
    const bool b_on_plane = std::abs(db) <= kGeometryEpsilon;

    if (a_on_plane) {
        add_unique_intersection(points, {a.x, a.y});
    }
    if (b_on_plane) {
        add_unique_intersection(points, {b.x, b.y});
    }
    if ((da < -kGeometryEpsilon && db > kGeometryEpsilon) ||
        (da > kGeometryEpsilon && db < -kGeometryEpsilon)) {
        const double ratio = (z - a.z) / (b.z - a.z);
        add_unique_intersection(points, {
            a.x + ratio * (b.x - a.x),
            a.y + ratio * (b.y - a.y),
        });
    }
}

std::vector<Segment> slice_layer(const std::vector<Triangle>& triangles, double z) {
    std::vector<Segment> segments;
    segments.reserve(triangles.size() / 3U);

    for (const Triangle& triangle : triangles) {
        const double minimum_z = std::min({
            triangle.vertices[0].z,
            triangle.vertices[1].z,
            triangle.vertices[2].z,
        });
        const double maximum_z = std::max({
            triangle.vertices[0].z,
            triangle.vertices[1].z,
            triangle.vertices[2].z,
        });
        if (z < minimum_z - kGeometryEpsilon || z > maximum_z + kGeometryEpsilon) {
            continue;
        }

        std::vector<Point2> intersections;
        intersections.reserve(3);
        intersect_edge(triangle.vertices[0], triangle.vertices[1], z, intersections);
        intersect_edge(triangle.vertices[1], triangle.vertices[2], z, intersections);
        intersect_edge(triangle.vertices[2], triangle.vertices[0], z, intersections);

        if (intersections.size() < 2U) {
            continue;
        }

        std::pair<std::size_t, std::size_t> farthest{0U, 1U};
        double farthest_distance = squared_distance(intersections[0], intersections[1]);
        for (std::size_t first = 0; first < intersections.size(); ++first) {
            for (std::size_t second = first + 1U; second < intersections.size(); ++second) {
                const double candidate = squared_distance(intersections[first], intersections[second]);
                if (candidate > farthest_distance) {
                    farthest = {first, second};
                    farthest_distance = candidate;
                }
            }
        }
        if (farthest_distance > kGeometryEpsilon * kGeometryEpsilon) {
            segments.push_back({intersections[farthest.first], intersections[farthest.second]});
        }
    }
    return segments;
}

std::vector<std::vector<Point2>> connect_segments(const std::vector<Segment>& segments) {
    std::vector<std::vector<Point2>> paths;
    std::vector<bool> used(segments.size(), false);
    const double tolerance_squared = kJoinToleranceMm * kJoinToleranceMm;

    for (std::size_t seed = 0; seed < segments.size(); ++seed) {
        if (used[seed]) {
            continue;
        }
        used[seed] = true;
        std::vector<Point2> path{segments[seed].a, segments[seed].b};

        while (true) {
            const Point2 tail = path.back();
            std::size_t best_index = segments.size();
            bool reverse = false;
            double best_distance = tolerance_squared;
            for (std::size_t candidate = 0; candidate < segments.size(); ++candidate) {
                if (used[candidate]) {
                    continue;
                }
                const double to_a = squared_distance(tail, segments[candidate].a);
                const double to_b = squared_distance(tail, segments[candidate].b);
                if (to_a <= best_distance) {
                    best_distance = to_a;
                    best_index = candidate;
                    reverse = false;
                }
                if (to_b <= best_distance) {
                    best_distance = to_b;
                    best_index = candidate;
                    reverse = true;
                }
            }
            if (best_index == segments.size()) {
                break;
            }
            used[best_index] = true;
            path.push_back(reverse ? segments[best_index].a : segments[best_index].b);
            if (path.size() > 3U && squared_distance(path.front(), path.back()) <= tolerance_squared) {
                path.back() = path.front();
                break;
            }
        }
        if (path.size() >= 2U) {
            paths.push_back(std::move(path));
        }
    }
    return paths;
}

// Rectilinear sweep adapted from OrcaSlicer's FillRectilinear geometry at
// d5dbd96dd64b830076c81053ed5fda26d5a1771b. Feresa keeps millimetre-space
// points here instead of pulling the complete desktop libslic3r dependency
// graph into this first Android headless port.
std::vector<Segment> generate_rectilinear_infill(
    const std::vector<Segment>& boundaries,
    double spacing,
    double angle_degrees
) {
    std::vector<Segment> infill;
    if (boundaries.empty() || !std::isfinite(spacing) || spacing <= kGeometryEpsilon) {
        return infill;
    }

    const double angle = angle_degrees * 3.14159265358979323846 / 180.0;
    const double cosine = std::cos(angle);
    const double sine = std::sin(angle);
    auto to_scan = [&](const Point2& point) {
        return Point2{
            point.x * cosine + point.y * sine,
            -point.x * sine + point.y * cosine,
        };
    };
    auto from_scan = [&](double u, double v) {
        return Point2{
            u * cosine - v * sine,
            u * sine + v * cosine,
        };
    };

    std::vector<Segment> scan_boundaries;
    scan_boundaries.reserve(boundaries.size());
    double minimum_v = std::numeric_limits<double>::max();
    double maximum_v = std::numeric_limits<double>::lowest();
    for (const Segment& boundary : boundaries) {
        Segment scan{to_scan(boundary.a), to_scan(boundary.b)};
        minimum_v = std::min({minimum_v, scan.a.y, scan.b.y});
        maximum_v = std::max({maximum_v, scan.a.y, scan.b.y});
        scan_boundaries.push_back(scan);
    }

    // Offset the grid by half a spacing so it does not repeatedly pass through
    // polygon vertices. The half-open edge rule below then counts every crossing
    // exactly once, including for polygons with holes.
    const double first_v = std::floor(minimum_v / spacing) * spacing + spacing * 0.5;
    for (double v = first_v; v < maximum_v - kGeometryEpsilon; v += spacing) {
        std::vector<double> crossings;
        crossings.reserve(scan_boundaries.size() / 2U);
        for (const Segment& edge : scan_boundaries) {
            const double first = edge.a.y;
            const double second = edge.b.y;
            if (!((first <= v && v < second) || (second <= v && v < first))) {
                continue;
            }
            const double ratio = (v - first) / (second - first);
            crossings.push_back(edge.a.x + ratio * (edge.b.x - edge.a.x));
        }
        std::sort(crossings.begin(), crossings.end());

        std::vector<double> unique_crossings;
        unique_crossings.reserve(crossings.size());
        for (double crossing : crossings) {
            if (unique_crossings.empty() ||
                std::abs(crossing - unique_crossings.back()) > kGeometryEpsilon) {
                unique_crossings.push_back(crossing);
            }
        }
        for (std::size_t index = 1U; index < unique_crossings.size(); index += 2U) {
            const double start = unique_crossings[index - 1U];
            const double end = unique_crossings[index];
            if (end - start > kGeometryEpsilon) {
                infill.push_back({from_scan(start, v), from_scan(end, v)});
            }
        }
    }
    return infill;
}

double cross_product(const Point2& first, const Point2& second) {
    return first.x * second.y - first.y * second.x;
}

Point2 subtract(const Point2& first, const Point2& second) {
    return {first.x - second.x, first.y - second.y};
}

Point2 interpolate(const Point2& first, const Point2& second, double ratio) {
    return {
        first.x + (second.x - first.x) * ratio,
        first.y + (second.y - first.y) * ratio,
    };
}

bool point_inside_boundaries(const std::vector<Segment>& boundaries, const Point2& point) {
    bool inside = false;
    for (const Segment& edge : boundaries) {
        if ((edge.a.y > point.y) == (edge.b.y > point.y)) {
            continue;
        }
        const double crossing_x = edge.a.x +
            (point.y - edge.a.y) * (edge.b.x - edge.a.x) / (edge.b.y - edge.a.y);
        if (crossing_x > point.x) {
            inside = !inside;
        }
    }
    return inside;
}

std::vector<Segment> clip_polyline_to_boundaries(
    const std::vector<Point2>& polyline,
    const std::vector<Segment>& boundaries
) {
    std::vector<Segment> clipped;
    for (std::size_t point_index = 1U; point_index < polyline.size(); ++point_index) {
        const Point2 start = polyline[point_index - 1U];
        const Point2 end = polyline[point_index];
        const Point2 direction = subtract(end, start);
        if (squared_distance(start, end) <= kGeometryEpsilon * kGeometryEpsilon) {
            continue;
        }

        std::vector<double> ratios{0.0, 1.0};
        for (const Segment& edge : boundaries) {
            const Point2 edge_direction = subtract(edge.b, edge.a);
            const double denominator = cross_product(direction, edge_direction);
            if (std::abs(denominator) <= kGeometryEpsilon) {
                continue;
            }
            const Point2 offset = subtract(edge.a, start);
            const double path_ratio = cross_product(offset, edge_direction) / denominator;
            const double edge_ratio = cross_product(offset, direction) / denominator;
            if (path_ratio > kGeometryEpsilon && path_ratio < 1.0 - kGeometryEpsilon &&
                edge_ratio >= -kGeometryEpsilon && edge_ratio <= 1.0 + kGeometryEpsilon) {
                ratios.push_back(path_ratio);
            }
        }
        std::sort(ratios.begin(), ratios.end());
        ratios.erase(std::unique(ratios.begin(), ratios.end(), [](double first, double second) {
            return std::abs(first - second) <= kGeometryEpsilon;
        }), ratios.end());

        for (std::size_t ratio_index = 1U; ratio_index < ratios.size(); ++ratio_index) {
            const double first = ratios[ratio_index - 1U];
            const double second = ratios[ratio_index];
            if (second - first <= kGeometryEpsilon) {
                continue;
            }
            if (point_inside_boundaries(boundaries, interpolate(start, end, (first + second) * 0.5))) {
                clipped.push_back({interpolate(start, end, first), interpolate(start, end, second)});
            }
        }
    }
    return clipped;
}

Point2 rotate_point(const Point2& point, double angle) {
    const double cosine = std::cos(angle);
    const double sine = std::sin(angle);
    return {
        cosine * point.x - sine * point.y,
        sine * point.x + cosine * point.y,
    };
}

// Port of OrcaSlicer FillGyroid's standard parametric generator. The formula,
// -45 degree correction, 2.44 density correction, adaptive subdivision and
// layer-Z phase match the pinned upstream implementation. Clipping is adapted
// to Feresa's lightweight boundary segments in place of libslic3r ExPolygon.
double orca_gyroid_value(
    double x,
    double z_sine,
    double z_cosine,
    bool vertical,
    bool flip
) {
    if (vertical) {
        const double phase = (z_cosine < 0.0 ? kPi : 0.0) + kPi;
        const double a = std::sin(x + phase);
        const double b = -z_cosine;
        const double result = z_sine * std::cos(x + phase + (flip ? kPi : 0.0));
        const double radius = std::sqrt(a * a + b * b);
        return std::asin(a / radius) + std::asin(result / radius) + kPi;
    }
    const double phase = z_sine < 0.0 ? kPi : 0.0;
    const double a = std::cos(x + phase);
    const double b = -z_sine;
    const double result = z_cosine * std::sin(x + phase + (flip ? 0.0 : kPi));
    const double radius = std::sqrt(a * a + b * b);
    return std::asin(a / radius) + std::asin(result / radius) + 0.5 * kPi;
}

std::vector<Point2> orca_gyroid_period(
    double width,
    double z_cosine,
    double z_sine,
    bool vertical,
    bool flip,
    double tolerance
) {
    std::vector<Point2> points;
    const double limit = std::min(2.0 * kPi, width);
    for (double x = 0.0; x < limit - kGeometryEpsilon; x += 0.5 * kPi) {
        points.push_back({x, orca_gyroid_value(x, z_sine, z_cosine, vertical, flip)});
    }
    points.push_back({limit, orca_gyroid_value(limit, z_sine, z_cosine, vertical, flip)});

    while (true) {
        const std::size_t original_size = points.size();
        for (std::size_t index = 1U; index < original_size; ++index) {
            const Point2& left = points[index - 1U];
            const Point2& right = points[index];
            const double x = (left.x + right.x) * 0.5;
            const Point2 middle{x, orca_gyroid_value(x, z_sine, z_cosine, vertical, flip)};
            const double deviation = std::abs(cross_product(subtract(middle, left), subtract(middle, right)));
            if (deviation > tolerance * tolerance) {
                points.push_back(middle);
            }
        }
        if (points.size() == original_size) {
            break;
        }
        std::sort(points.begin(), points.end(), [](const Point2& first, const Point2& second) {
            return first.x < second.x;
        });
    }
    return points;
}

std::vector<Point2> orca_gyroid_wave(
    const std::vector<Point2>& one_period,
    double width,
    double height,
    double offset,
    double scale,
    double z_cosine,
    double z_sine,
    bool vertical,
    bool flip
) {
    std::vector<Point2> points = one_period;
    const double period = points.back().x;
    if (width > period + kGeometryEpsilon) {
        points.pop_back();
        const std::size_t period_size = points.size();
        do {
            const Point2 source = points[points.size() - period_size];
            points.push_back({source.x + period, source.y});
        } while (points.back().x < width - kGeometryEpsilon);
        points.push_back({width, orca_gyroid_value(width, z_sine, z_cosine, vertical, flip)});
    }
    for (Point2& point : points) {
        point.y = std::clamp(point.y + offset, 0.0, height);
        if (vertical) {
            std::swap(point.x, point.y);
        }
        point.x *= scale;
        point.y *= scale;
    }
    return points;
}

std::vector<Segment> generate_orca_gyroid_infill(
    const std::vector<Segment>& boundaries,
    double extrusion_spacing,
    double density_fraction,
    double angle_degrees,
    double layer_z
) {
    constexpr double density_adjust = 2.44;
    constexpr double correction_angle_degrees = -45.0;
    constexpr double pattern_tolerance_mm = 0.2;
    const double adjusted_density = std::max(0.001, density_fraction * density_adjust);
    const double scale = extrusion_spacing / adjusted_density;
    const double angle = (angle_degrees + correction_angle_degrees) * kPi / 180.0;

    std::vector<Segment> rotated_boundaries;
    rotated_boundaries.reserve(boundaries.size());
    double minimum_x = std::numeric_limits<double>::max();
    double minimum_y = std::numeric_limits<double>::max();
    double maximum_x = std::numeric_limits<double>::lowest();
    double maximum_y = std::numeric_limits<double>::lowest();
    for (const Segment& boundary : boundaries) {
        const Point2 first = rotate_point(boundary.a, -angle);
        const Point2 second = rotate_point(boundary.b, -angle);
        rotated_boundaries.push_back({first, second});
        minimum_x = std::min({minimum_x, first.x, second.x});
        minimum_y = std::min({minimum_y, first.y, second.y});
        maximum_x = std::max({maximum_x, first.x, second.x});
        maximum_y = std::max({maximum_y, first.y, second.y});
    }

    const double module = 2.0 * kPi * scale;
    minimum_x = std::floor(minimum_x / module) * module - 10.0 * extrusion_spacing;
    minimum_y = std::floor(minimum_y / module) * module - 10.0 * extrusion_spacing;
    maximum_x += 10.0 * extrusion_spacing;
    maximum_y += 10.0 * extrusion_spacing;
    const double width = (maximum_x - minimum_x) / scale;
    const double height = (maximum_y - minimum_y) / scale;
    const double grid_z = layer_z / scale;
    const double z_sine = std::sin(grid_z);
    const double z_cosine = std::cos(grid_z);
    const bool vertical = std::abs(z_sine) <= std::abs(z_cosine);
    const bool initial_flip = vertical ? false : true;
    double lower_bound = vertical ? -kPi : 0.0;
    double upper_bound = vertical ? width - 0.5 * kPi : height;
    const double wave_width = vertical ? height : width;
    const double wave_height = vertical ? width : height;
    const double tolerance = std::min(extrusion_spacing / 2.0, pattern_tolerance_mm) / scale;
    const auto odd_period = orca_gyroid_period(
        wave_width, z_cosine, z_sine, vertical, initial_flip, tolerance
    );
    const auto even_period = orca_gyroid_period(
        wave_width, z_cosine, z_sine, vertical, !initial_flip, tolerance
    );

    std::vector<Segment> result;
    bool flip = initial_flip;
    for (double offset = lower_bound; offset < upper_bound + kGeometryEpsilon; offset += kPi) {
        auto wave = orca_gyroid_wave(
            flip ? even_period : odd_period,
            wave_width,
            wave_height,
            offset,
            scale,
            z_cosine,
            z_sine,
            vertical,
            flip
        );
        for (Point2& point : wave) {
            point.x += minimum_x;
            point.y += minimum_y;
        }
        auto clipped = clip_polyline_to_boundaries(wave, rotated_boundaries);
        for (Segment& segment : clipped) {
            segment.a = rotate_point(segment.a, angle);
            segment.b = rotate_point(segment.b, angle);
            if (distance(segment.a, segment.b) >= 0.8 * extrusion_spacing) {
                result.push_back(segment);
            }
        }
        flip = !flip;
    }
    return result;
}

bool valid_settings(const SliceSettings& settings) {
    return settings.layer_height_mm >= 0.04 && settings.layer_height_mm <= 1.0 &&
           settings.nozzle_diameter_mm >= 0.15 && settings.nozzle_diameter_mm <= 2.0 &&
           settings.filament_diameter_mm >= 1.0 && settings.filament_diameter_mm <= 3.5 &&
           settings.print_speed_mm_s >= 1.0 && settings.print_speed_mm_s <= 500.0 &&
           settings.infill_density_percent >= 0.0 && settings.infill_density_percent <= 100.0 &&
           std::isfinite(settings.infill_angle_degrees) &&
           settings.infill_speed_mm_s >= 1.0 && settings.infill_speed_mm_s <= 500.0 &&
           settings.bed_width_mm >= 10.0 && settings.bed_width_mm <= 2'000.0 &&
           settings.bed_depth_mm >= 10.0 && settings.bed_depth_mm <= 2'000.0 &&
           std::isfinite(settings.model_position_x_mm) &&
           std::isfinite(settings.model_position_y_mm) &&
           std::isfinite(settings.model_rotation_degrees) &&
           settings.model_scale >= 0.01 && settings.model_scale <= 100.0 &&
           settings.nozzle_temperature_c >= 0 && settings.nozzle_temperature_c <= 400 &&
           settings.bed_temperature_c >= 0 && settings.bed_temperature_c <= 150;
}

}  // namespace

SliceResult slice_stl_to_gcode(
    const std::string& input_path,
    const std::string& output_path,
    const SliceSettings& settings
) {
    if (settings.infill_pattern != "gyroid" &&
        settings.infill_pattern != "rectilinear" &&
        settings.infill_pattern != "line" &&
        settings.infill_pattern != "grid") {
        return {false, "Unsupported infill pattern: " + settings.infill_pattern};
    }
    if (!valid_settings(settings)) {
        return {false, "Invalid slicing settings"};
    }

    std::vector<Triangle> triangles;
    if (!load_stl(input_path, triangles)) {
        return {false, "The selected file is not a readable binary or ASCII STL"};
    }

    double source_minimum_x = std::numeric_limits<double>::max();
    double source_maximum_x = std::numeric_limits<double>::lowest();
    double source_minimum_y = std::numeric_limits<double>::max();
    double source_maximum_y = std::numeric_limits<double>::lowest();
    double source_minimum_z = std::numeric_limits<double>::max();
    for (const Triangle& triangle : triangles) {
        for (const Vec3& vertex : triangle.vertices) {
            source_minimum_x = std::min(source_minimum_x, vertex.x);
            source_maximum_x = std::max(source_maximum_x, vertex.x);
            source_minimum_y = std::min(source_minimum_y, vertex.y);
            source_maximum_y = std::max(source_maximum_y, vertex.y);
            source_minimum_z = std::min(source_minimum_z, vertex.z);
        }
    }

    const double source_center_x = (source_minimum_x + source_maximum_x) / 2.0;
    const double source_center_y = (source_minimum_y + source_maximum_y) / 2.0;
    const double rotation_radians = settings.model_rotation_degrees *
        3.14159265358979323846 / 180.0;
    const double cosine = std::cos(rotation_radians);
    const double sine = std::sin(rotation_radians);
    for (Triangle& triangle : triangles) {
        for (Vec3& vertex : triangle.vertices) {
            const double centered_x = (vertex.x - source_center_x) * settings.model_scale;
            const double centered_y = (vertex.y - source_center_y) * settings.model_scale;
            vertex.x = cosine * centered_x - sine * centered_y + settings.model_position_x_mm;
            vertex.y = sine * centered_x + cosine * centered_y + settings.model_position_y_mm;
            vertex.z = (vertex.z - source_minimum_z) * settings.model_scale;
        }
    }

    double minimum_x = std::numeric_limits<double>::max();
    double minimum_y = std::numeric_limits<double>::max();
    double minimum_z = std::numeric_limits<double>::max();
    double maximum_x = std::numeric_limits<double>::lowest();
    double maximum_y = std::numeric_limits<double>::lowest();
    double maximum_z = std::numeric_limits<double>::lowest();
    for (const Triangle& triangle : triangles) {
        for (const Vec3& vertex : triangle.vertices) {
            minimum_x = std::min(minimum_x, vertex.x);
            minimum_y = std::min(minimum_y, vertex.y);
            minimum_z = std::min(minimum_z, vertex.z);
            maximum_x = std::max(maximum_x, vertex.x);
            maximum_y = std::max(maximum_y, vertex.y);
            maximum_z = std::max(maximum_z, vertex.z);
        }
    }

    if (minimum_x < -kGeometryEpsilon || minimum_y < -kGeometryEpsilon ||
        maximum_x > settings.bed_width_mm + kGeometryEpsilon ||
        maximum_y > settings.bed_depth_mm + kGeometryEpsilon) {
        std::ostringstream message;
        message << std::fixed << std::setprecision(1)
                << "Model is outside the " << settings.bed_width_mm << " x "
                << settings.bed_depth_mm << " mm print bed";
        return {false, message.str()};
    }

    const double model_height = maximum_z - minimum_z;
    if (!std::isfinite(model_height) || model_height <= kGeometryEpsilon) {
        return {false, "The STL has no printable height"};
    }

    const auto layer_count = static_cast<std::size_t>(
        std::ceil(model_height / settings.layer_height_mm)
    );
    if (layer_count == 0U || layer_count > 100'000U) {
        return {false, "The requested layer count is outside the supported range"};
    }

    std::ofstream output(output_path, std::ios::trunc);
    if (!output) {
        return {false, "Cannot create the G-code output file"};
    }

    const double filament_area = 3.14159265358979323846 *
        settings.filament_diameter_mm * settings.filament_diameter_mm / 4.0;
    const double extrusion_width = settings.nozzle_diameter_mm * 1.10;
    const double extrusion_per_mm = settings.layer_height_mm * extrusion_width / filament_area;
    const double print_feed_rate = settings.print_speed_mm_s * 60.0;
    const double infill_feed_rate = settings.infill_speed_mm_s * 60.0;
    const double infill_spacing = settings.infill_density_percent > kGeometryEpsilon
        ? extrusion_width / (settings.infill_density_percent / 100.0)
        : 0.0;

    output << "; Generated by Feresa Slicer technical preview\n"
           << "; Perimeters and rectilinear infill\n"
           << "; triangles = " << triangles.size() << "\n"
           << "G90\nM82\n"
           << "M140 S" << settings.bed_temperature_c << "\n"
           << "M104 S" << settings.nozzle_temperature_c << "\n"
           << "G28\n"
           << "M190 S" << settings.bed_temperature_c << "\n"
           << "M109 S" << settings.nozzle_temperature_c << "\n"
           << "G92 E0\n";
    output << std::fixed << std::setprecision(4);

    double extrusion = 0.0;
    std::size_t extrusion_segment_count = 0U;
    std::size_t generated_layers = 0U;
    for (std::size_t layer = 0; layer < layer_count; ++layer) {
        const double model_z = std::min(
            minimum_z + (static_cast<double>(layer) + 0.5) * settings.layer_height_mm,
            maximum_z - kGeometryEpsilon
        );
        const double output_z = (static_cast<double>(layer) + 1.0) * settings.layer_height_mm;
        const auto segments = slice_layer(triangles, model_z);
        const auto paths = connect_segments(segments);
        if (paths.empty()) {
            continue;
        }

        ++generated_layers;
        output << ";LAYER:" << layer << "\n"
               << "G0 Z" << output_z << " F600\n";
        for (const auto& path : paths) {
            output << ";TYPE:Perimeter\n";
            output << "G0 X" << path.front().x
                   << " Y" << path.front().y << " F6000\n";
            for (std::size_t point_index = 1; point_index < path.size(); ++point_index) {
                const double segment_length = distance(path[point_index - 1U], path[point_index]);
                if (segment_length <= kGcodeResolutionMm) {
                    continue;
                }
                extrusion += segment_length * extrusion_per_mm;
                ++extrusion_segment_count;
                output << "G1 X" << path[point_index].x
                       << " Y" << path[point_index].y
                       << " E" << extrusion
                       << " F" << print_feed_rate << "\n";
            }
        }
        if (settings.infill_density_percent > kGeometryEpsilon) {
            const double density_fraction = settings.infill_density_percent / 100.0;
            const double layer_angle = settings.infill_angle_degrees +
                (layer % 2U == 0U ? 0.0 : 90.0);
            std::vector<Segment> infill;
            if (settings.infill_pattern == "gyroid") {
                infill = generate_orca_gyroid_infill(
                    segments,
                    extrusion_width,
                    density_fraction,
                    settings.infill_angle_degrees,
                    model_z
                );
            } else if (settings.infill_pattern == "grid") {
                infill = generate_rectilinear_infill(
                    segments, infill_spacing * 2.0, settings.infill_angle_degrees
                );
                auto crossing = generate_rectilinear_infill(
                    segments, infill_spacing * 2.0, settings.infill_angle_degrees + 90.0
                );
                infill.insert(infill.end(), crossing.begin(), crossing.end());
            } else {
                infill = generate_rectilinear_infill(segments, infill_spacing, layer_angle);
            }
            if (!infill.empty()) {
                output << ";TYPE:Sparse infill\n"
                       << "; infill_pattern = " << settings.infill_pattern << "\n";
            }
            for (std::size_t index = 0; index < infill.size(); ++index) {
                const Segment& line = infill[index];
                // Reverse alternating lines to reduce travel without ever
                // extruding across holes or separate islands.
                const Point2 start = index % 2U == 0U ? line.a : line.b;
                const Point2 end = index % 2U == 0U ? line.b : line.a;
                output << "G0 X" << start.x << " Y" << start.y << " F6000\n";
                const double segment_length = distance(start, end);
                if (segment_length <= kGcodeResolutionMm) {
                    continue;
                }
                extrusion += segment_length * extrusion_per_mm;
                ++extrusion_segment_count;
                output << "G1 X" << end.x
                       << " Y" << end.y
                       << " E" << extrusion
                       << " F" << infill_feed_rate << "\n";
            }
        }
    }

    output << "M104 S0\nM140 S0\n"
           << "G91\nG1 Z10 F600\nG90\n"
           << "M84\n";
    output.close();

    if (generated_layers == 0U || extrusion_segment_count == 0U) {
        return {false, "No printable contours were produced"};
    }

    return {
        true,
        "G-code generated",
        generated_layers,
        extrusion_segment_count,
        extrusion,
    };
}

}  // namespace feresa_slicer
