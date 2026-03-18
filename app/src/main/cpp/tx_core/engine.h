#pragma once

#include <memory>
#include <thread>
#include <atomic>
#include <mutex>
#include <condition_variable>
#include "screen.h"
#include "pty.h"
#include "parser.h"

namespace tx {

class Engine {
public:
    struct Config {
        int rows = 24;
        int cols = 80;
        bool use256Colors = true;
        bool useTrueColor = true;
        int scrollbackLines = 10000;
    };

    explicit Engine(const Config& config);
    ~Engine();

    bool Start(const std::string& shell = "/system/bin/sh");
    void Stop();
    void Resize(int rows, int cols);
    
    void WriteInput(const std::string& data);
    void WriteInput(const char* data, size_t len);
    
    std::shared_ptr<Screen> GetScreen() { return screen_; }
    
    using OnUpdateCallback = std::function<void(const Screen::Damage& damage)>;
    void SetUpdateCallback(OnUpdateCallback cb) { update_cb_ = cb; }

private:
    void IOThread();
    void ParserThread();
    
    Config config_;
    std::unique_ptr<Pty> pty_;
    std::shared_ptr<Screen> screen_;
    std::unique_ptr<Parser> parser_;
    
    std::atomic<bool> running_{false};
    std::thread io_thread_;
    std::thread parser_thread_;
    
    std::mutex input_mutex_;
    std::string input_buffer_;
    
    OnUpdateCallback update_cb_;
    
    // Thread-safe parser queue
    struct ParserQueue {
        std::mutex mtx;
        std::condition_variable cv;
        std::string data;
        bool new_data = false;
    } parser_queue_;
};

} // namespace tx

