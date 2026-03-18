#include "screen.h"
#include <algorithm>
#include <cstring>

namespace tx {

Screen::Screen(int rows, int cols, int scrollback) 
    : rows_(rows), cols_(cols), scrollback_size_(scrollback) {
    buffer_.resize(rows * cols);
    scroll_bottom_ = rows - 1;
    Clear();
}

Screen::~Screen() = default;

void Screen::Resize(int rows, int cols) {
    std::lock_guard<std::mutex> lock(mtx_);
    
    // Simple resize - keep top-left content
    std::vector<Cell> new_buffer(rows * cols);
    
    int copy_rows = std::min(rows, rows_);
    int copy_cols = std::min(cols, cols_);
    
    for (int r = 0; r < copy_rows; ++r) {
        for (int c = 0; c < copy_cols; ++c) {
            new_buffer[r * cols + c] = buffer_[r * cols_ + c];
        }
    }
    
    buffer_ = std::move(new_buffer);
    rows_ = rows;
    cols_ = cols;
    scroll_bottom_ = rows - 1;
    
    // Clamp cursor
    cursor_.row = std::min(cursor_.row, rows_ - 1);
    cursor_.col = std::min(cursor_.col, cols_ - 1);
}

void Screen::Clear() {
    std::fill(buffer_.begin(), buffer_.end(), Cell{});
    cursor_ = Cursor{};
}

Cell* Screen::GetCell(int row, int col) {
    if (row < 0 || row >= rows_ || col < 0 || col >= cols_) return nullptr;
    return &buffer_[row * cols_ + col];
}

const Cell* Screen::GetCell(int row, int col) const {
    if (row < 0 || row >= rows_ || col < 0 || col >= cols_) return nullptr;
    return &buffer_[row * cols_ + col];
}

Screen::Damage Screen::WriteChar(uint32_t ch, const CellStyle& style) {
    std::lock_guard<std::mutex> lock(mtx_);
    
    Cell* cell = GetCell(cursor_.row, cursor_.col);
    if (cell) {
        cell->codepoint = ch;
        cell->style = style;
        cell->dirty = true;
    }
    
    Damage dmg;
    dmg.startRow = cursor_.row;
    dmg.endRow = cursor_.row;
    dmg.dirty = true;
    
    ++cursor_.col;
    if (cursor_.col >= cols_) {
        cursor_.col = 0;
        ++cursor_.row;
        if (cursor_.row > scroll_bottom_) {
            ScrollUp(1);
            --cursor_.row;
        }
        dmg.endRow = cursor_.row;
        dmg.scroll = true;
    }
    
    return dmg;
}

Screen::Damage Screen::MoveCursor(int row, int col) {
    std::lock_guard<std::mutex> lock(mtx_);
    cursor_.row = std::clamp(row, 0, rows_ - 1);
    cursor_.col = std::clamp(col, 0, cols_ - 1);
    Damage dmg;
    dmg.cursorMoved = true;
    return dmg;
}

Screen::Damage Screen::ScrollUp(int lines) {
    std::lock_guard<std::mutex> lock(mtx_);
    
    if (lines <= 0 || lines > rows_) return Damage{};
    
    // Save to scrollback
    if (scrollback_size_ > 0) {
        for (int i = 0; i < lines; ++i) {
            if (scrollback_.size() >= scrollback_size_) {
                scrollback_.erase(scrollback_.begin());
            }
            std::vector<Cell> line(cols_);
            for (int c = 0; c < cols_; ++c) {
                line[c] = buffer_[i * cols_ + c];
            }
            scrollback_.push_back(std::move(line));
        }
    }
    
    // Move content up
    int keep = rows_ - lines;
    if (keep > 0) {
        std::memmove(buffer_.data(), 
                     buffer_.data() + lines * cols_, 
                     keep * cols_ * sizeof(Cell));
    }
    
    // Clear new lines at bottom
    std::fill(buffer_.begin() + keep * cols_, buffer_.end(), Cell{});
    
    Damage dmg;
    dmg.startRow = 0;
    dmg.endRow = rows_ - 1;
    dmg.scroll = true;
    dmg.dirty = true;
    return dmg;
}

Screen::Damage Screen::ClearScreen() {
    std::lock_guard<std::mutex> lock(mtx_);
    std::fill(buffer_.begin(), buffer_.end(), Cell{});
    Damage dmg;
    dmg.startRow = 0;
    dmg.endRow = rows_ - 1;
    dmg.dirty = true;
    return dmg;
}

} // namespace tx

