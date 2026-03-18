#pragma once

#include <string>
#include <termios.h>
#include <pthread.h>

namespace tx {

class Pty {
public:
    Pty();
    ~Pty();
    
    bool Open(int rows, int cols);
    void Close();
    bool IsOpen() const;
    bool Spawn(const std::string& cmd);
    void Resize(int rows, int cols);
    
    ssize_t Read(char* buffer, size_t size, int timeout_ms);
    ssize_t Write(const char* data, size_t len);
    
    int GetMasterFd() const { return master_fd_; }

private:
    int master_fd_ = -1;
    int slave_fd_ = -1;
    pid_t child_pid_ = -1;
    pthread_mutex_t write_mutex_ = PTHREAD_MUTEX_INITIALIZER;
    
    bool SetupPty(int rows, int cols);
};

} // namespace tx

