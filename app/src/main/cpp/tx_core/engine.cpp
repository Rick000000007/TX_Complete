#include "engine.h"
#include <android/log.h>
#include <poll.h>

#define LOG_TAG "TX_Engine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace tx {

Engine::Engine(const Config& config) : config_(config) {
    screen_ = std::make_shared<Screen>(config.rows, config.cols, config.scrollbackLines);
    parser_ = std::make_unique<Parser>(screen_.get());
}

Engine::~Engine() {
    Stop();
}

bool Engine::Start(const std::string& shell) {
    pty_ = std::make_unique<Pty>();
    if (!pty_->Open(config_.rows, config_.cols)) {
        LOGE("Failed to open PTY");
        return false;
    }
    
    if (!pty_->Spawn(shell)) {
        LOGE("Failed to spawn shell");
        return false;
    }
    
    running_ = true;
    io_thread_ = std::thread(&Engine::IOThread, this);
    parser_thread_ = std::thread(&Engine::ParserThread, this);
    
    LOGI("Engine started with %dx%d", config_.rows, config_.cols);
    return true;
}

void Engine::Stop() {
    running_ = false;
    parser_queue_.cv.notify_all();
    
    if (io_thread_.joinable()) io_thread_.join();
    if (parser_thread_.joinable()) parser_thread_.join();
    
    pty_.reset();
}

void Engine::Resize(int rows, int cols) {
    std::lock_guard<std::mutex> lock(screen_->GetMutex());
    screen_->Resize(rows, cols);
    if (pty_) {
        pty_->Resize(rows, cols);
    }
}

void Engine::WriteInput(const std::string& data) {
    WriteInput(data.data(), data.size());
}

void Engine::WriteInput(const char* data, size_t len) {
    if (!pty_ || !pty_->IsOpen()) return;
    
    std::lock_guard<std::mutex> lock(input_mutex_);
    // Thread-safe write to PTY
    pty_->Write(data, len);
}

void Engine::IOThread() {
    LOGI("IO Thread started");
    char buffer[4096];
    
    while (running_ && pty_->IsOpen()) {
        // Non-blocking read with timeout
        ssize_t n = pty_->Read(buffer, sizeof(buffer), 50); // 50ms timeout
        if (n > 0) {
            std::lock_guard<std::mutex> lock(parser_queue_.mtx);
            parser_queue_.data.append(buffer, n);
            parser_queue_.new_data = true;
            parser_queue_.cv.notify_one();
        } else if (n < 0 && errno != EAGAIN && errno != EWOULDBLOCK) {
            LOGE("PTY read error: %d", errno);
            break;
        }
    }
}

void Engine::ParserThread() {
    LOGI("Parser Thread started");
    
    while (running_) {
        std::unique_lock<std::mutex> lock(parser_queue_.mtx);
        parser_queue_.cv.wait(lock, [this] { return !running_ || parser_queue_.new_data; });
        
        if (!running_) break;
        
        std::string data = std::move(parser_queue_.data);
        parser_queue_.new_data = false;
        lock.unlock();
        
        if (!data.empty()) {
            // Parse and update screen
            auto damage = parser_->Parse(data.data(), data.size());
            
            if (update_cb_ && damage.dirty) {
                update_cb_(damage);
            }
        }
    }
}

} // namespace tx

