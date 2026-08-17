// SPDX-License-Identifier: AGPL-3.0-only
#include "slicer_core.h"

#include <filesystem>
#include <fstream>
#include <iostream>
#include <iterator>

int main() {
    const auto directory = std::filesystem::temp_directory_path();
    const auto input = directory / "feresa_slicer_cube.stl";
    const auto output = directory / "feresa_slicer_cube.gcode";

    std::ofstream stl(input);
    stl << R"(solid cube
facet normal 0 0 -1 outer loop
vertex 0 0 0
vertex 10 10 0
vertex 10 0 0
endloop endfacet
facet normal 0 0 -1 outer loop
vertex 0 0 0
vertex 0 10 0
vertex 10 10 0
endloop endfacet
facet normal 0 0 1 outer loop
vertex 0 0 10
vertex 10 0 10
vertex 10 10 10
endloop endfacet
facet normal 0 0 1 outer loop
vertex 0 0 10
vertex 10 10 10
vertex 0 10 10
endloop endfacet
facet normal 0 -1 0 outer loop
vertex 0 0 0
vertex 10 0 0
vertex 10 0 10
endloop endfacet
facet normal 0 -1 0 outer loop
vertex 0 0 0
vertex 10 0 10
vertex 0 0 10
endloop endfacet
facet normal 1 0 0 outer loop
vertex 10 0 0
vertex 10 10 0
vertex 10 10 10
endloop endfacet
facet normal 1 0 0 outer loop
vertex 10 0 0
vertex 10 10 10
vertex 10 0 10
endloop endfacet
facet normal 0 1 0 outer loop
vertex 10 10 0
vertex 0 10 0
vertex 0 10 10
endloop endfacet
facet normal 0 1 0 outer loop
vertex 10 10 0
vertex 0 10 10
vertex 10 10 10
endloop endfacet
facet normal -1 0 0 outer loop
vertex 0 10 0
vertex 0 0 0
vertex 0 0 10
endloop endfacet
facet normal -1 0 0 outer loop
vertex 0 10 0
vertex 0 0 10
vertex 0 10 10
endloop endfacet
endsolid cube
)";
    stl.close();

    const auto result = feresa_slicer::slice_stl_to_gcode(
        input.string(), output.string(), feresa_slicer::SliceSettings{}
    );
    if (!result.success || result.layer_count != 50U || result.extrusion_segment_count == 0U) {
        std::cerr << result.message << '\n';
        return 1;
    }
    if (!std::filesystem::exists(output) || std::filesystem::file_size(output) == 0U) {
        return 2;
    }
    std::ifstream generated_gcode(output);
    const std::string gcode(
        (std::istreambuf_iterator<char>(generated_gcode)),
        std::istreambuf_iterator<char>()
    );
    if (gcode.find(";TYPE:Sparse infill") == std::string::npos ||
        gcode.find("; infill_pattern = gyroid") == std::string::npos ||
        gcode.find("F6000.0000") == std::string::npos) {
        std::cerr << "Expected clipped infill at Orca's configured 100 mm/s default\n";
        return 5;
    }

    auto unsupported_settings = feresa_slicer::SliceSettings{};
    unsupported_settings.infill_pattern = "honeycomb";
    const auto unsupported_result = feresa_slicer::slice_stl_to_gcode(
        input.string(), output.string(), unsupported_settings
    );
    if (unsupported_result.success ||
        unsupported_result.message.find("Unsupported infill pattern") == std::string::npos) {
        std::cerr << "Unsupported patterns must never silently fall back\n";
        return 9;
    }

    for (const std::string pattern : {"rectilinear", "line", "grid"}) {
        auto pattern_settings = feresa_slicer::SliceSettings{};
        pattern_settings.infill_pattern = pattern;
        const auto pattern_result = feresa_slicer::slice_stl_to_gcode(
            input.string(), output.string(), pattern_settings
        );
        if (!pattern_result.success || pattern_result.extrusion_segment_count == 0U) {
            std::cerr << "Supported Orca pattern failed: " << pattern << '\n';
            return 10;
        }
    }

    auto no_infill_settings = feresa_slicer::SliceSettings{};
    no_infill_settings.infill_density_percent = 0.0;
    const auto no_infill_result = feresa_slicer::slice_stl_to_gcode(
        input.string(), output.string(), no_infill_settings
    );
    if (!no_infill_result.success ||
        result.extrusion_segment_count <= no_infill_result.extrusion_segment_count) {
        std::cerr << "Infill did not add extrusion paths\n";
        return 6;
    }

    auto outside_settings = feresa_slicer::SliceSettings{};
    outside_settings.model_position_x_mm = 1.0;
    const auto outside_result = feresa_slicer::slice_stl_to_gcode(
        input.string(), output.string(), outside_settings
    );
    if (outside_result.success || outside_result.message.find("print bed") == std::string::npos) {
        std::cerr << "Expected out-of-bed rejection, got: " << outside_result.message << '\n';
        return 7;
    }

    auto transformed_settings = feresa_slicer::SliceSettings{};
    transformed_settings.model_rotation_degrees = 45.0;
    transformed_settings.model_scale = 1.5;
    const auto transformed_result = feresa_slicer::slice_stl_to_gcode(
        input.string(), output.string(), transformed_settings
    );
    if (!transformed_result.success || transformed_result.layer_count != 75U) {
        std::cerr << "Transformed slice failed: " << transformed_result.message << '\n';
        return 8;
    }
    std::cout << "layers=" << result.layer_count
              << " segments=" << result.extrusion_segment_count
              << " filament_mm=" << result.filament_length_mm << '\n';
    return 0;
}
