// SPDX-License-Identifier: AGPL-3.0-only
#include "slicer_core.h"

#include <jni.h>

#include <sstream>
#include <string>

namespace {

std::string from_jstring(JNIEnv* environment, jstring value) {
    if (value == nullptr) {
        return {};
    }
    const char* characters = environment->GetStringUTFChars(value, nullptr);
    if (characters == nullptr) {
        return {};
    }
    std::string result(characters);
    environment->ReleaseStringUTFChars(value, characters);
    return result;
}

}  // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_tech_g24_feresaslicer_slicer_NativeSlicer_slice(
    JNIEnv* environment,
    jobject,
    jstring input_path,
    jstring output_path,
    jdouble layer_height,
    jdouble nozzle_diameter,
    jdouble filament_diameter,
    jint nozzle_temperature,
    jint bed_temperature,
    jdouble print_speed,
    jdouble bed_width,
    jdouble bed_depth,
    jdouble position_x,
    jdouble position_y,
    jdouble rotation_degrees,
    jdouble model_scale
) {
    feresa_slicer::SliceSettings settings;
    settings.layer_height_mm = layer_height;
    settings.nozzle_diameter_mm = nozzle_diameter;
    settings.filament_diameter_mm = filament_diameter;
    settings.nozzle_temperature_c = nozzle_temperature;
    settings.bed_temperature_c = bed_temperature;
    settings.print_speed_mm_s = print_speed;
    settings.bed_width_mm = bed_width;
    settings.bed_depth_mm = bed_depth;
    settings.model_position_x_mm = position_x;
    settings.model_position_y_mm = position_y;
    settings.model_rotation_degrees = rotation_degrees;
    settings.model_scale = model_scale;

    const auto result = feresa_slicer::slice_stl_to_gcode(
        from_jstring(environment, input_path),
        from_jstring(environment, output_path),
        settings
    );

    std::ostringstream response;
    response << (result.success ? "OK" : "ERROR") << '\t'
             << result.message << '\t'
             << result.layer_count << '\t'
             << result.extrusion_segment_count << '\t'
             << result.filament_length_mm;
    return environment->NewStringUTF(response.str().c_str());
}
