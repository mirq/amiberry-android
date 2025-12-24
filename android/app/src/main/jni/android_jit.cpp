/*
 * android_jit.cpp - JIT memory allocation with W^X compliance for Android
 *
 * Implements dual-mapping using memfd_create for Android 10+ W^X compliance.
 * See android_jit.h for API documentation.
 */

#ifdef __ANDROID__

#include "android_jit.h"

#include <android/log.h>
#include <errno.h>
#include <fcntl.h>
#include <pthread.h>
#include <sys/mman.h>
#include <sys/syscall.h>
#include <unistd.h>

#include <cstdlib>
#include <cstring>
#include <map>
#include <mutex>

#define LOG_TAG "AmiberryJIT"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

// memfd_create syscall wrapper (available from API 30, but syscall works earlier)
#ifndef MFD_CLOEXEC
#define MFD_CLOEXEC 0x0001U
#endif

static inline int memfd_create_wrapper(const char* name, unsigned int flags) {
#if __ANDROID_API__ >= 30
    return memfd_create(name, flags);
#else
    return syscall(__NR_memfd_create, name, flags);
#endif
}

// Structure to track JIT allocations
struct JitAllocation {
    void* rw_ptr;
    void* rx_ptr;
    size_t size;
    int fd;
};

// Global state
static std::map<void*, JitAllocation> g_jit_allocations_by_rw;
static std::map<void*, JitAllocation*> g_jit_allocations_by_rx;
static std::mutex g_jit_mutex;
static size_t g_page_size = 0;
static bool g_initialized = false;

// Round up to page size
static inline size_t round_up_to_page(size_t size) {
    return (size + g_page_size - 1) & ~(g_page_size - 1);
}

int android_jit_init(void) {
    std::lock_guard<std::mutex> lock(g_jit_mutex);
    
    if (g_initialized) {
        return 0;
    }
    
    g_page_size = sysconf(_SC_PAGESIZE);
    if (g_page_size == 0) {
        g_page_size = 4096; // Fallback
    }
    
    LOGI("Android JIT initialized, page size: %zu", g_page_size);
    g_initialized = true;
    return 0;
}

void android_jit_shutdown(void) {
    std::lock_guard<std::mutex> lock(g_jit_mutex);
    
    // Free any remaining allocations
    for (auto& pair : g_jit_allocations_by_rw) {
        JitAllocation& alloc = pair.second;
        if (alloc.rw_ptr) {
            munmap(alloc.rw_ptr, alloc.size);
        }
        if (alloc.rx_ptr) {
            munmap(alloc.rx_ptr, alloc.size);
        }
        if (alloc.fd >= 0) {
            close(alloc.fd);
        }
    }
    
    g_jit_allocations_by_rw.clear();
    g_jit_allocations_by_rx.clear();
    g_initialized = false;
    
    LOGI("Android JIT shutdown complete");
}

int android_jit_alloc(size_t size, void** rw_ptr, void** rx_ptr) {
    if (!rw_ptr || !rx_ptr) {
        errno = EINVAL;
        return -1;
    }
    
    *rw_ptr = nullptr;
    *rx_ptr = nullptr;
    
    if (!g_initialized) {
        android_jit_init();
    }
    
    // Round up to page size
    size_t alloc_size = round_up_to_page(size);
    
    // Create anonymous shared memory file
    int fd = memfd_create_wrapper("amiberry_jit", MFD_CLOEXEC);
    if (fd < 0) {
        LOGE("memfd_create failed: %s", strerror(errno));
        return -1;
    }
    
    // Set the size
    if (ftruncate(fd, alloc_size) < 0) {
        LOGE("ftruncate failed: %s", strerror(errno));
        close(fd);
        return -1;
    }
    
    // Create RW mapping for the JIT compiler
    void* rw = mmap(nullptr, alloc_size, PROT_READ | PROT_WRITE,
                    MAP_SHARED, fd, 0);
    if (rw == MAP_FAILED) {
        LOGE("mmap RW failed: %s", strerror(errno));
        close(fd);
        return -1;
    }
    
    // Create RX mapping for execution
    void* rx = mmap(nullptr, alloc_size, PROT_READ | PROT_EXEC,
                    MAP_SHARED, fd, 0);
    if (rx == MAP_FAILED) {
        LOGE("mmap RX failed: %s", strerror(errno));
        munmap(rw, alloc_size);
        close(fd);
        return -1;
    }
    
    // Store allocation info
    {
        std::lock_guard<std::mutex> lock(g_jit_mutex);
        
        JitAllocation alloc;
        alloc.rw_ptr = rw;
        alloc.rx_ptr = rx;
        alloc.size = alloc_size;
        alloc.fd = fd;
        
        g_jit_allocations_by_rw[rw] = alloc;
        g_jit_allocations_by_rx[rx] = &g_jit_allocations_by_rw[rw];
    }
    
    *rw_ptr = rw;
    *rx_ptr = rx;
    
    LOGD("JIT alloc: size=%zu, rw=%p, rx=%p", alloc_size, rw, rx);
    
    return 0;
}

void android_jit_free(void* rw_ptr, void* rx_ptr, size_t size) {
    if (!rw_ptr && !rx_ptr) {
        return;
    }
    
    std::lock_guard<std::mutex> lock(g_jit_mutex);
    
    auto it = g_jit_allocations_by_rw.find(rw_ptr);
    if (it == g_jit_allocations_by_rw.end()) {
        LOGE("JIT free: unknown allocation rw=%p", rw_ptr);
        return;
    }
    
    JitAllocation& alloc = it->second;
    
    // Verify pointers match
    if (alloc.rx_ptr != rx_ptr) {
        LOGE("JIT free: rx_ptr mismatch, expected %p, got %p", alloc.rx_ptr, rx_ptr);
    }
    
    // Unmap both mappings
    if (alloc.rw_ptr) {
        munmap(alloc.rw_ptr, alloc.size);
    }
    if (alloc.rx_ptr) {
        munmap(alloc.rx_ptr, alloc.size);
    }
    
    // Close the fd
    if (alloc.fd >= 0) {
        close(alloc.fd);
    }
    
    // Remove from tracking
    g_jit_allocations_by_rx.erase(alloc.rx_ptr);
    g_jit_allocations_by_rw.erase(it);
    
    LOGD("JIT free: rw=%p, rx=%p", rw_ptr, rx_ptr);
}

void* android_jit_rx_to_rw(void* rx_ptr) {
    if (!rx_ptr) {
        return nullptr;
    }
    
    std::lock_guard<std::mutex> lock(g_jit_mutex);
    
    // First check for exact match
    auto it = g_jit_allocations_by_rx.find(rx_ptr);
    if (it != g_jit_allocations_by_rx.end()) {
        return it->second->rw_ptr;
    }
    
    // Check if rx_ptr is within any allocation
    for (auto& pair : g_jit_allocations_by_rw) {
        JitAllocation& alloc = pair.second;
        uintptr_t rx_base = reinterpret_cast<uintptr_t>(alloc.rx_ptr);
        uintptr_t rx_addr = reinterpret_cast<uintptr_t>(rx_ptr);
        
        if (rx_addr >= rx_base && rx_addr < rx_base + alloc.size) {
            // Calculate offset and return corresponding RW address
            size_t offset = rx_addr - rx_base;
            return reinterpret_cast<void*>(
                reinterpret_cast<uintptr_t>(alloc.rw_ptr) + offset);
        }
    }
    
    return nullptr;
}

void* android_jit_rw_to_rx(void* rw_ptr) {
    if (!rw_ptr) {
        return nullptr;
    }
    
    std::lock_guard<std::mutex> lock(g_jit_mutex);
    
    // First check for exact match
    auto it = g_jit_allocations_by_rw.find(rw_ptr);
    if (it != g_jit_allocations_by_rw.end()) {
        return it->second.rx_ptr;
    }
    
    // Check if rw_ptr is within any allocation
    for (auto& pair : g_jit_allocations_by_rw) {
        JitAllocation& alloc = pair.second;
        uintptr_t rw_base = reinterpret_cast<uintptr_t>(alloc.rw_ptr);
        uintptr_t rw_addr = reinterpret_cast<uintptr_t>(rw_ptr);
        
        if (rw_addr >= rw_base && rw_addr < rw_base + alloc.size) {
            // Calculate offset and return corresponding RX address
            size_t offset = rw_addr - rw_base;
            return reinterpret_cast<void*>(
                reinterpret_cast<uintptr_t>(alloc.rx_ptr) + offset);
        }
    }
    
    return nullptr;
}

void android_jit_flush_cache(void* start, size_t size) {
    // Use GCC/Clang builtin for cache flush
    __builtin___clear_cache(
        static_cast<char*>(start),
        static_cast<char*>(start) + size);
}

size_t android_jit_page_size(void) {
    if (g_page_size == 0) {
        g_page_size = sysconf(_SC_PAGESIZE);
        if (g_page_size == 0) {
            g_page_size = 4096;
        }
    }
    return g_page_size;
}

#endif /* __ANDROID__ */
