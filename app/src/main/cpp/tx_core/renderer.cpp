#include "renderer.h"
#include <cstring>

namespace tx {

Renderer::Renderer(int rows, int cols) : rows_(rows), cols_(cols) {
    buffer_.resize(rows);
    for (auto& row : buffer_) {
        row.cells.resize(cols);
    }
}

Renderer::Damage Renderer::UpdateFromScreen(const Screen& screen) {
    Damage damage{};
    damage.startRow = -1;
    damage.endRow = -1;
    damage.fullRedraw = false;
    
    std::lock_guard<std::mutex> lock(screen.GetMutex());
    
    // Check if dimensions changed
    if (screen.GetRows() != rows_ || screen.GetCols() != cols_) {
        rows_ = screen.GetRows();
        cols_ = screen.GetCols();
        buffer_.resize(rows_);
        for (auto& row : buffer_) {
            row.cells.resize(cols);
        }
        damage.fullRedraw = true;
    }
    
    // Compare cells
    bool any_dirty = false;
    for (int r = 0; r < rows_; ++r) {
        bool row_dirty = false;
        for (int c = 0; c < cols_; ++c) {
            const auto* cell = screen.GetCell(r, c);
            auto& render_cell = buffer_[r].cells[c];
            
            if (!cell) continue;
            
            // Only update if changed or marked dirty in screen
            if (cell->codepoint != render_cell.codepoint ||
                cell->style.fg.index != render_cell.fg.index ||
                cell->style.bg.index != render_cell.bg.index ||
                cell->style.flags != render_cell.flags ||
                cell->dirty) {
                
                render_cell.codepoint = cell->codepoint;
                render_cell.fg = cell->style.fg;
                render_cell.bg = cell->style.bg;
                render_cell.flags = cell->style.flags;
                render_cell.dirty = true;
                
                row_dirty = true;
                any_dirty = true;
                
                // Track damage region
                if (damage.startRow == -1 || r < damage.startRow) damage.startRow = r;
                if (r > damage.endRow) damage.endRow = r;
            }
        }
        buffer_[r].dirty = row_dirty;
    }
    
    // Update cursor
    auto cursor = screen.GetCursor();
    cursor_pos_ = {cursor.row, cursor.col};
    
    if (!any_dirty && !damage.fullRedraw) {
        // Cursor only update
        damage.startRow = cursor.row;
        damage.endRow = cursor.row;
    }
    
    return damage;
}

void Renderer::InvalidateAll() {
    for (auto& row : buffer_) {
        row.dirty = true;
        for (auto& cell : row.cells) {
            cell.dirty = true;
        }
    }
}

} // namespace tx

