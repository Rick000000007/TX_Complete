#include "pty.h"
#include <fcntl.h>
#include <unistd.h>
#include <sys/wait.h>
#include <sys/ioctl.h>
#include <android/log.h>
#include <poll.h>
#include <errno.h>

#define LOG_TAG "TX_PTY"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace tx {

Pty::Pty() = default;

Pty::~Pty() {
    Close();
}

bool Pty::Open(int rows, int cols) {
    master_fd_ = posix_openpt(O_RDWR | O_NOCTTY);
    if (master_fd_ < 0) {
        LOGE("posix_openpt failed: %d", errno);
        return false;
    }
    
    if (grantpt(master_fd_) < 0 || unlockpt(master_fd_) < 0) {
        LOGE("grantpt/unlockpt failed: %d", errno);
        close(master_fd_);
        master_fd_ = -1;
        return false;
    }
    
    SetupPty(rows, cols);
    return true;
}

void Pty::Close() {
    if (master_fd_ >= 0) {
        close(master_fd_);
        master_fd_ = -1;
    }
    if (child_pid_ > 0) {
        kill(child_pid_, SIGTERM);
        waitpid(child_pid_, nullptr, WNOHANG);
        child_pid_ = -1;
    }
}

bool Pty::IsOpen() const {
    return master_fd_ >= 0;
}

bool Pty::Spawn(const std::string& cmd) {
    if (master_fd_ < 0) return false;
    
    const char* slave_name = ptsname(master_fd_);
    if (!slave_name) return false;
    
    pid_t pid = fork();
    if (pid < 0) return false;
    
    if (pid == 0) {
        // Child process
        close(master_fd_);
        
        // Open slave
        int slave = open(slave_name, O_RDWR);
        if (slave < 0) _exit(1);
        
        // Setup terminal
        dup2(slave, STDIN_FILENO);
        dup2(slave, STDOUT_FILENO);
        dup2(slave, STDERR_FILENO);
        
        if (slave > STDERR_FILENO) close(slave);
        
        // Set controlling terminal
        setsid();
        ioctl(0, TIOCSCTTY, 0);
        
        // Environment
        setenv("TERM", "xterm-256color", 1);
        setenv("HOME", "/data/data/com.tx.terminal/files", 1);
        
        execl(cmd.c_str(), cmd.c_str(), nullptr);
        _exit(1);
    }
    
    child_pid_ = pid;
    return true;
}

void Pty::Resize(int rows, int cols) {
    if (master_fd_ < 0) return;
    
    struct winsize ws;
    ws.ws_row = rows;
    ws.ws_col = cols;
    ws.ws_xpixel = 0;
    ws.ws_ypixel = 0;
    
    ioctl(master_fd_, TIOCSWINSZ, &ws);
}

ssize_t Pty::Read(char* buffer, size_t size, int timeout_ms) {
    if (master_fd_ < 0) return -1;
    
    struct pollfd pfd = {master_fd_, POLLIN, 0};
    int ret = poll(&pfd, 1, timeout_ms);
    
    if (ret < 0) return -1;
    if (ret == 0) return 0; // Timeout
    
    return read(master_fd_, buffer, size);
}

ssize_t Pty::Write(const char* data, size_t len) {
    if (master_fd_ < 0) return -1;
    
    pthread_mutex_lock(&write_mutex_);
    ssize_t written = write(master_fd_, data, len);
    pthread_mutex_unlock(&write_mutex_);
    
    return written;
}

bool Pty::SetupPty(int rows, int cols) {
    Resize(rows, cols);
    return true;
}

} // namespace tx

