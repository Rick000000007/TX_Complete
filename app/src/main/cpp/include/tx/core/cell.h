#pragma once

#include <cstdint>
#include "types.h"

namespace tx {

struct Cell {
    uint32_t codepoint = 0;                 // 4 bytes

    Color fg = Color::FromIndexed(7);       // 4 bytes
    Color bg = Color::FromIndexed(0);       // 4 bytes

    uint16_t flags = 0;                     // 2 bytes

    bool dirty = true;                      // 1 byte
    bool wide = false;                      // 1 byte - for CJK

    uint8_t padding[2];                     // 2 bytes padding

    void Clear() {
        codepoint = 0;
        fg = Color::FromIndexed(7);
        bg = Color::FromIndexed(0);
        flags = 0;
        dirty = true;
        wide = false;
    }

    void SetStyle(const CellStyle& style) {
        fg = style.fg;
        bg = style.bg;
        flags =
            (style.bold ? 1 : 0) |
            (style.italic ? 2 : 0) |
            (style.underline ? 4 : 0);
    }
};

static_assert(sizeof(Cell) == 20, "Cell size must be 20 bytes");

} // namespace tx
