#include "parser.h"
#include <cctype>
#include <algorithm>
#include <android/log.h>

#define LOG_TAG "TX_Parser"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace tx {

Parser::Parser(Screen* screen) : screen_(screen) {}

Screen::Damage Parser::Parse(const char* data, size_t len) {
    Screen::Damage total_damage;
    
    for (size_t i = 0; i < len; ++i) {
        uint8_t ch = static_cast<uint8_t>(data[i]);
        Screen::Damage step_damage;
        
        // UTF-8 multibyte handling
        if (utf8_remaining_ > 0) {
            if ((ch & 0xC0) == 0x80) {
                utf8_codepoint_ = (utf8_codepoint_ << 6) | (ch & 0x3F);
                utf8_remaining_--;
                if (utf8_remaining_ == 0) {
                    step_damage = screen_->WriteChar(utf8_codepoint_, current_style_);
                }
                continue;
            } else {
                // Invalid UTF-8 sequence, reset
                utf8_remaining_ = 0;
            }
        }
        
        // Check for UTF-8 start
        if (ch >= 0x80) {
            if ((ch & 0xE0) == 0xC0) { // 2 bytes
                utf8_codepoint_ = ch & 0x1F;
                utf8_remaining_ = 1;
                continue;
            } else if ((ch & 0xF0) == 0xE0) { // 3 bytes
                utf8_codepoint_ = ch & 0x0F;
                utf8_remaining_ = 2;
                continue;
            } else if ((ch & 0xF8) == 0xF0) { // 4 bytes
                utf8_codepoint_ = ch & 0x07;
                utf8_remaining_ = 3;
                continue;
            }
        }
        
        switch (state_) {
            case ParseState::Ground:
                step_damage = HandleGround(ch);
                break;
            case ParseState::Escape:
                step_damage = HandleEscape(ch);
                break;
            case ParseState::CSIEntry:
                state_ = ParseState::CSIParam;
                param_buffer_.clear();
                [[fallthrough]];
            case ParseState::CSIParam:
                step_damage = HandleCSIParam(ch);
                break;
            case ParseState::OSC:
                step_damage = HandleOSC(ch);
                break;
            default:
                state_ = ParseState::Ground;
                break;
        }
        
        // Merge damage regions
        if (step_damage.dirty) {
            if (!total_damage.dirty) {
                total_damage = step_damage;
            } else {
                total_damage.startRow = std::min(total_damage.startRow, step_damage.startRow);
                total_damage.endRow = std::max(total_damage.endRow, step_damage.endRow);
                total_damage.cursorMoved |= step_damage.cursorMoved;
                total_damage.scroll |= step_damage.scroll;
            }
        }
    }
    
    return total_damage;
}

Screen::Damage Parser::HandleGround(uint8_t ch) {
    if (ch == 0x1B) { // ESC
        state_ = ParseState::Escape;
        return {};
    }
    if (ch == 0x9B) { // CSI (8-bit)
        state_ = ParseState::CSIEntry;
        return {};
    }
    if (ch == 0x9D) { // OSC (8-bit)
        state_ = ParseState::OSC;
        return {};
    }
    if (ch == 0x07) { // BEL
        // Handle bell if needed
        return {};
    }
    if (ch == 0x08) { // BS
        auto cur = screen_->GetCursor();
        return screen_->MoveCursor(cur.row, cur.col - 1);
    }
    if (ch == 0x09) { // HT
        auto cur = screen_->GetCursor();
        int new_col = ((cur.col / 8) + 1) * 8;
        return screen_->MoveCursor(cur.row, std::min(new_col, screen_->GetCols() - 1));
    }
    if (ch == 0x0A || ch == 0x0C) { // LF or FF
        auto cur = screen_->GetCursor();
        if (cur.row == screen_->GetRows() - 1) {
            screen_->ScrollUp(1);
        }
        return screen_->MoveCursor(std::min(cur.row + 1, screen_->GetRows() - 1), cur.col);
    }
    if (ch == 0x0D) { // CR
        auto cur = screen_->GetCursor();
        return screen_->MoveCursor(cur.row, 0);
    }
    
    // Printable character
    if (ch >= 0x20 && ch < 0x7F) {
        return screen_->WriteChar(ch, current_style_);
    }
    
    return {};
}

Screen::Damage Parser::HandleEscape(uint8_t ch) {
    state_ = ParseState::Ground;
    
    switch (ch) {
        case '[': // CSI
            state_ = ParseState::CSIEntry;
            break;
        case ']': // OSC
            state_ = ParseState::OSC;
            break;
        case '7': // Save cursor
            // Implement save cursor
            break;
        case '8': // Restore cursor
            // Implement restore cursor
            break;
        case 'c': // Reset
            screen_->Clear();
            break;
        case 'M': // Reverse line feed
            // Implement reverse LF
            break;
    }
    return {};
}

Screen::Damage Parser::HandleCSIParam(uint8_t ch) {
    if (ch >= 0x40 && ch <= 0x7E) { // Final byte
        state_ = ParseState::Ground;
        return ExecuteCSI(param_buffer_, ch);
    }
    
    if ((ch >= 0x30 && ch <= 0x3F) || (ch >= 0x20 && ch <= 0x2F)) {
        param_buffer_ += static_cast<char>(ch);
    }
    
    return {};
}

Screen::Damage Parser::HandleOSC(uint8_t ch) {
    if (ch == 0x07 || ch == 0x9C || (ch == 0x1B)) { // BEL, ST, or ESC ST
        if (ch == 0x1B) {
            // Expect \
            return {};
        }
        // Process OSC
        state_ = ParseState::Ground;
    } else {
        osc_buffer_ += static_cast<char>(ch);
    }
    return {};
}

std::vector<int> Parser::ParseParams(const std::string& s) {
    std::vector<int> params;
    size_t start = 0;
    size_t end = s.find(';');
    
    while (end != std::string::npos) {
        std::string token = s.substr(start, end - start);
        params.push_back(token.empty() ? 0 : std::stoi(token));
        start = end + 1;
        end = s.find(';', start);
    }
    
    std::string last = s.substr(start);
    params.push_back(last.empty() ? 0 : std::stoi(last));
    
    return params;
}

Screen::Damage Parser::ExecuteCSI(const std::string& params, uint8_t final_char) {
    auto p = ParseParams(params);
    auto cur = screen_->GetCursor();
    
    switch (final_char) {
        case 'A': { // Cursor Up
            int n = p.empty() ? 1 : p[0];
            return screen_->MoveCursor(cur.row - n, cur.col);
        }
        case 'B': { // Cursor Down
            int n = p.empty() ? 1 : p[0];
            return screen_->MoveCursor(cur.row + n, cur.col);
        }
        case 'C': { // Cursor Forward
            int n = p.empty() ? 1 : p[0];
            return screen_->MoveCursor(cur.row, cur.col + n);
        }
        case 'D': { // Cursor Backward
            int n = p.empty() ? 1 : p[0];
            return screen_->MoveCursor(cur.row, cur.col - n);
        }
        case 'H':   // Cursor Position
        case 'f': { // Horizontal Vertical Position
            int row = (p.size() > 0 && p[0] > 0) ? p[0] - 1 : 0;
            int col = (p.size() > 1 && p[1] > 0) ? p[1] - 1 : 0;
            return screen_->MoveCursor(row, col);
        }
        case 'J': { // Erase Display
            int mode = p.empty() ? 0 : p[0];
            return screen_->ClearScreen(); // Simplified
        }
        case 'K': { // Erase Line
            int mode = p.empty() ? 0 : p[0];
            // Implement line clearing
            return screen_->ClearLine(cur.row);
        }
        case 'm': { // SGR
            for (int code : p) {
                switch (code) {
                    case 0: current_style_ = CellStyle{}; break;
                    case 1: current_style_.flags |= 1; break; // Bold
                    case 30: current_style_.fg.index = 0; current_style_.fg.isIndexed = true; break;
                    case 31: current_style_.fg.index = 1; current_style_.fg.isIndexed = true; break;
                    case 32: current_style_.fg.index = 2; current_style_.fg.isIndexed = true; break;
                    case 33: current_style_.fg.index = 3; current_style_.fg.isIndexed = true; break;
                    case 34: current_style_.fg.index = 4; current_style_.fg.isIndexed = true; break;
                    case 35: current_style_.fg.index = 5; current_style_.fg.isIndexed = true; break;
                    case 36: current_style_.fg.index = 6; current_style_.fg.isIndexed = true; break;
                    case 37: current_style_.fg.index = 7; current_style_.fg.isIndexed = true; break;
                    case 38: // Extended color (simplified)
                        if (p.size() >= 3 && p[1] == 5) {
                            current_style_.fg.index = p[2];
                            current_style_.fg.isIndexed = true;
                        }
                        break;
                }
            }
            break;
        }
        case 's': // Save cursor
            // Implement
            break;
        case 'u': // Restore cursor
            // Implement
            break;
    }
    
    return {};
}

void Parser::Reset() {
    state_ = ParseState::Ground;
    param_buffer_.clear();
    osc_buffer_.clear();
    utf8_remaining_ = 0;
}

} // namespace tx

