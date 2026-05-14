package com.cosmicproximity.client.audio;

import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Tiny FIFO of decoded PCM frames per sender. Drops oldest frames if growth
 * exceeds the cap (congestion). pop() returns null when empty → emit silence.
 */
public final class JitterBuffer {
    private final int maxFrames;
    private final ConcurrentLinkedDeque<short[]> queue = new ConcurrentLinkedDeque<>();

    public JitterBuffer(int maxFrames) {
        this.maxFrames = maxFrames;
    }

    public void push(short[] frame) {
        queue.addLast(frame);
        while (queue.size() > maxFrames) queue.pollFirst();
    }

    public short[] pop() {
        return queue.pollFirst();
    }

    public int size() { return queue.size(); }
}
