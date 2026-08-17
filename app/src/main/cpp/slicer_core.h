// SPDX-License-Identifier: AGPL-3.0-only
#pragma once

#include <cstddef>
#include <string>

namespace feresa_slicer {

struct SliceSettings {
    double layer_height_mm = 0.20;
    double nozzle_diameter_mm = 0.40;
    double filament_diameter_mm = 1.75;
    double print_speed_mm_s = 45.0;
    double infill_density_percent = 20.0;
    double infill_angle_degrees = 45.0;
    double infill_speed_mm_s = 100.0;
    std::string infill_pattern = "gyroid";
    double bed_width_mm = 220.0;
    double bed_depth_mm = 220.0;
    double model_position_x_mm = 110.0;
    double model_position_y_mm = 110.0;
    double model_rotation_degrees = 0.0;
    double model_scale = 1.0;
    int nozzle_temperature_c = 210;
    int bed_temperature_c = 60;
};

struct SliceResult {
    bool success = false;
    std::string message;
    std::size_t layer_count = 0;
    std::size_t extrusion_segment_count = 0;
    double filament_length_mm = 0.0;
};

SliceResult slice_stl_to_gcode(
    const std::string& input_path,
    const std::string& output_path,
    const SliceSettings& settings
);

}  // namespace feresa_slicer
