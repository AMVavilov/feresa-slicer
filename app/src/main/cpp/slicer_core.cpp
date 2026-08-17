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
constexpr double kJoinToleranceMm = 0.03;
constexpr std::size_t kMaximumTriangleCount = 5'000'000;

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

bool valid_settings(const SliceSettings& settings) {
    return settings.layer_height_mm >= 0.04 && settings.layer_height_mm <= 1.0 &&
           settings.nozzle_diameter_mm >= 0.15 && settings.nozzle_diameter_mm <= 2.0 &&
           settings.filament_diameter_mm >= 1.0 && settings.filament_diameter_mm <= 3.5 &&
           settings.print_speed_mm_s >= 1.0 && settings.print_speed_mm_s <= 500.0 &&
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

    output << "; Generated by Feresa Slicer technical preview\n"
           << "; WARNING: perimeter-only experimental output\n"
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
            output << "G0 X" << path.front().x
                   << " Y" << path.front().y << " F6000\n";
            for (std::size_t point_index = 1; point_index < path.size(); ++point_index) {
                const double segment_length = distance(path[point_index - 1U], path[point_index]);
                if (segment_length <= kGeometryEpsilon) {
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
