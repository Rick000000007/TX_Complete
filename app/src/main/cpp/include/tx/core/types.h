#pragma once

#include <cstdint>
#include <cstddef>

namespace tx {

// Color representation
struct Color {
    uint8_t r, g, b, a;
    bool isIndexed;
    uint8_t index;
    
    Color() : r(0), g(0), b(0), a(255), isIndexed(true), index(0) {}
    
    static Color FromIndexed(uint8_t idx) {
        Color c;
        c.isIndexed = true;
        c.index = idx;
        // Standard xterm 256 color palette would be mapped here
        return c;
    }
    
    static Color FromRGB(uint8_t r, uint8_t g, uint8_t b) {
        Color c;
        c.isIndexed = false;
        c.r = r; c.g = g; c.b = b; c.a = 255;
        return c;
    }
};

// Cell attributes - packed to minimize memory
struct CellStyle {
    // Colors
    union {
        struct {
            uint8_t fgColor;
            uint8_t bgColor;
        };
        uint16_t colors;
    };
    
    // True color components (only valid if fgTrueColor/bgTrueColor)
    uint8_t fgR, fgG, fgB;
    uint8_t bgR, bgG, bgB;
    
    // Flags
    bool fgTrueColor : 1;
    bool bgTrueColor : 1;
    bool bold : 1;
    bool italic : 1;
    bool underline : 1;
    bool blink : 1;
    bool reverse : 1;
    bool invisible : 1;
    bool strikethrough : 1;
    
    CellStyle() 
        : fgColor(7), bgColor(0),
          fgR(255), fgG(255), fgB(255),
          bgR(0), bgG(0), bgB(0),
          fgTrueColor(false), bgTrueColor(false),
          bold(false), italic(false), underline(false),
          blink(false), reverse(false), invisible(false),
          strikethrough(false) {}
};

// Screen cell - aligned to 16 bytes for cache efficiency
struct Cell {
    uint32_t codepoint;      // 4 bytes (Unicode)
    CellStyle style;         // 12 bytes
    bool dirty;              // 1 byte
    bool wide;               // 1 byte (CJK wide character)
    uint8_t padding[2];      // 2 bytes alignment
    
    Cell() : codepoint(0), dirty(true), wide(false) {}
    
    void Clear() {
        codepoint = 0;
        style = CellStyle{};
        dirty = true;
        wide = false;
    }
};

static_assert(sizeof(Cell) == 20, "Cell size should be 20 bytes");

// Damage region for efficient updating
struct DamageRegion {
    int startRow;
    int endRow;
    bool scroll;
    bool cursor;
    bool dirty;
    
    DamageRegion() : startRow(0), endRow(0), scroll(false), cursor(false), dirty(false) {}
};

} // namespace tx

