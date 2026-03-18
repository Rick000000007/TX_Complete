pragma once
#include <cstdint>
#include <string>
#include <vector>
#include <functional>
#include "screen.h"

namespace tx {

enum class ParseState {
    Ground,
    Escape,
    EscapeIntermediate,
    CSIEntry,
    CSIParam,
    CSIIntermediate,
    CSIIgnore,
    SS3,
    DCS,
    OSC,
    String,
    UTF8 // Custom state for UTF-8 multibyte
};

class Parser {
public:
    explicit Parser(Screen* screen);
    
    // Parse incoming data, returns damage regions
    Screen::Damage Parse(const char* data, size_t len);
    
    // Reset parser state
    void Reset();
    
private:
    Screen* screen_;
    ParseState state_ = ParseState::Ground;
    std::string param_buffer_;
    std::string osc_buffer_;
    
    // UTF-8 handling
    uint32_t utf8_codepoint_ = 0;
    int utf8_remaining_ = 0;
    
    // Current style being built
    CellStyle current_style_;
    
    // State handlers
    Screen::Damage HandleGround(uint8_t ch);
    Screen::Damage HandleEscape(uint8_t ch);
    Screen::Damage HandleCSIEntry(uint8_t ch);
    Screen::Damage HandleCSIParam(uint8_t ch);
    Screen::Damage HandleOSC(uint8_t ch);
    
    // CSI execution
    Screen::Damage ExecuteCSI(const std::string& params, uint8_t final_char);
    
    // Utility
    std::vector<int> ParseParams(const std::string& s);
    void ClearLine(int row, int start, int end);
};

} // namespace tx

