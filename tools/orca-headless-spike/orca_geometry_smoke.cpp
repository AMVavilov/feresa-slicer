#include <clipper2/clipper.h>

#include <cstdint>

// This is deliberately not a JNI API. It is a link-time proof that Android can
// consume geometry code from the pinned OrcaSlicer tree before the production
// JNI bridge is switched from the legacy slicer to libslic3r.
extern "C" std::int32_t feresa_orca_geometry_smoke()
{
    using namespace Clipper2Lib;

    const Paths64 source {{
        Point64 {0, 0},
        Point64 {10'000, 0},
        Point64 {10'000, 10'000},
        Point64 {0, 10'000},
    }};

    const Paths64 expanded = InflatePaths(
        source,
        400.0,
        JoinType::Miter,
        EndType::Polygon
    );

    return expanded.size() == 1 && expanded.front().size() >= 4 ? 0 : 1;
}
