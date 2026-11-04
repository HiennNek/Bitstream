#ifndef BITSTREAM_RING_BUFFER_H
#define BITSTREAM_RING_BUFFER_H

#include <algorithm>
#include <atomic>
#include <cstddef>
#include <cstdint>
#include <cstring>

/**
 * Single-producer / single-consumer byte ring.
 *
 * The producer only advances `head_`, the consumer only advances `tail_`, and
 * both use release/acquire ordering so a successful read sees the bytes that
 * were written before the head update. The counters grow monotonically; the
 * buffer index is the counter modulo capacity.
 */
class RingBuffer {
public:
    explicit RingBuffer(size_t capacity)
        : capacity_(capacity), buffer_(new uint8_t[capacity]) {}

    ~RingBuffer() { delete[] buffer_; }

    RingBuffer(const RingBuffer&) = delete;
    RingBuffer& operator=(const RingBuffer&) = delete;

    size_t write(const uint8_t* data, size_t size) {
        const size_t head = head_.load(std::memory_order_relaxed);
        const size_t tail = tail_.load(std::memory_order_acquire);
        const size_t free_bytes = capacity_ - (head - tail);
        const size_t count = std::min(size, free_bytes);
        if (count == 0) {
            return 0;
        }
        copy_in(buffer_, head % capacity_, data, count);
        head_.store(head + count, std::memory_order_release);
        return count;
    }

    size_t read(uint8_t* data, size_t size) {
        const size_t tail = tail_.load(std::memory_order_relaxed);
        const size_t head = head_.load(std::memory_order_acquire);
        const size_t available_bytes = head - tail;
        const size_t count = std::min(size, available_bytes);
        if (count == 0) {
            return 0;
        }
        copy_out(buffer_, tail % capacity_, data, count);
        tail_.store(tail + count, std::memory_order_release);
        return count;
    }

    size_t available() const {
        const size_t head = head_.load(std::memory_order_acquire);
        const size_t tail = tail_.load(std::memory_order_acquire);
        return head - tail;
    }

    size_t free() const {
        const size_t head = head_.load(std::memory_order_acquire);
        const size_t tail = tail_.load(std::memory_order_acquire);
        return capacity_ - (head - tail);
    }

    size_t capacity() const { return capacity_; }

    void clear() {
        const size_t head = head_.load(std::memory_order_acquire);
        tail_.store(head, std::memory_order_release);
    }

private:
    void copy_in(uint8_t* buffer, size_t offset, const uint8_t* data, size_t size) const {
        const size_t first = std::min(size, capacity_ - offset);
        std::memcpy(buffer + offset, data, first);
        if (size > first) {
            std::memcpy(buffer, data + first, size - first);
        }
    }

    void copy_out(const uint8_t* buffer, size_t offset, uint8_t* data, size_t size) const {
        const size_t first = std::min(size, capacity_ - offset);
        std::memcpy(data, buffer + offset, first);
        if (size > first) {
            std::memcpy(data + first, buffer, size - first);
        }
    }

    size_t capacity_;
    uint8_t* buffer_;
    std::atomic<size_t> head_{0};
    std::atomic<size_t> tail_{0};
};

#endif  // BITSTREAM_RING_BUFFER_H
