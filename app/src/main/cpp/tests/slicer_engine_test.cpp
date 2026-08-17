// SPDX-License-Identifier: AGPL-3.0-only
#include "slicer_core.h"

#include <filesystem>
#include <fstream>
#include <iostream>

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

    auto outside_settings = feresa_slicer::SliceSettings{};
    outside_settings.model_position_x_mm = 1.0;
    const auto outside_result = feresa_slicer::slice_stl_to_gcode(
        input.string(), output.string(), outside_settings
    );
    if (outside_result.success || outside_result.message.find("print bed") == std::string::npos) {
        std::cerr << "Expected out-of-bed rejection, got: " << outside_result.message << '\n';
        return 3;
    }

    auto transformed_settings = feresa_slicer::SliceSettings{};
    transformed_settings.model_rotation_degrees = 45.0;
    transformed_settings.model_scale = 1.5;
    const auto transformed_result = feresa_slicer::slice_stl_to_gcode(
        input.string(), output.string(), transformed_settings
    );
    if (!transformed_result.success || transformed_result.layer_count != 75U) {
        std::cerr << "Transformed slice failed: " << transformed_result.message << '\n';
        return 4;
    }
    std::cout << "layers=" << result.layer_count
              << " segments=" << result.extrusion_segment_count
              << " filament_mm=" << result.filament_length_mm << '\n';
    return 0;
}
