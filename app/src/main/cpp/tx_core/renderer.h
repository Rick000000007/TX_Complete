#pragma once
#include "screen.h"
#include <vector>
#include <functional>

namespace tx {

class Renderer {
public:
    struct RenderCell {
        uint32_t codepoint;
        Color fg;
        Color bg;
        uint16_t flags;
        bool dirty;
    };
    
    struct RenderRow {
        std::vector<RenderCell> cells;
        bool dirty;
    };
    
    struct Damage {
        int startRow;
        int endRow;
        bool fullRedraw;
    };
    
    explicit Renderer(int rows, int cols);
    
    // Compare screen to internal buffer, return damage regions
    Damage UpdateFromScreen(const Screen& screen);
    
    // Access render buffer
    const std::vector<RenderRow>& GetBuffer() const { return buffer_; }
    
    // Mark all dirty (e.g., after resize)
    void InvalidateAll();
    
    // Get cursor position in render coordinates
    std::pair<int, int> GetCursorPosition() const { return cursor_pos_; }
    
private:
    int rows_;
    int cols_;
    std::vector<RenderRow> buffer_;
    std::pair<int, int> cursor_pos_;
};

} // namespace tx

