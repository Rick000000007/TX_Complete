#pragma once

#include <vector>
#include <string>
#include <mutex>
#include <cstdint>
#include "tx/core/cell.h"

namespace tx {

struct Screen {
    struct Damage {
        int startRow = -1;
        int endRow = -1;
        bool cursorMoved = false;
        bool dirty = false;
        bool scroll = false;
    };

    struct Cursor {
        int row = 0;
        int col = 0;
        bool visible = true;
        bool blink = false;
        CellStyle style;
    };

    Screen(int rows, int cols, int scrollback);
    ~Screen();
    
    void Resize(int rows, int cols);
    void Clear();
    
    // Thread safety
    std::mutex& GetMutex() { return mtx_; }
    
    // Cell access
    Cell* GetCell(int row, int col);
    const Cell* GetCell(int row, int col) const;
    
    // Operations
    Damage WriteChar(uint32_t ch, const CellStyle& style);
    Damage MoveCursor(int row, int col);
    Damage ScrollUp(int lines);
    Damage ScrollDown(int lines);
    Damage ClearLine(int row);
    Damage ClearScreen();
    Damage SetScrollRegion(int top, int bottom);
    
    // Getters
    int GetRows() const { return rows_; }
    int GetCols() const { return cols_; }
    Cursor GetCursor() const { return cursor_; }
    
    // Scrollback
    int GetScrollbackSize() const;
    const Cell* GetScrollbackLine(int index) const;

private:
    int rows_, cols_;
    int scrollback_size_;
    int scrollback_pos_ = 0;
    
    std::vector<Cell> buffer_; // Active screen
    std::vector<std::vector<Cell>> scrollback_; // Scrollback buffer (circular)
    
    Cursor cursor_;
    int scroll_top_ = 0;
    int scroll_bottom_;
    
    mutable std::mutex mtx_;
    
    void EnsureSize();
    Damage MakeDamage(int start, int end);
};

} // namespace tx

