package io.github.bobistuca.vertex.runtime;

import io.github.bobistuca.vertex.core.engine.AnalysisEngine;
import io.github.bobistuca.vertex.core.engine.Observation;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Dedicated worker that runs the checks off the network and server threads. The queue is bounded: when it is full
 * the observation is dropped and counted, never blocking a network thread.
 */
public final class AnalysisThread {
    private final BlockingQueue<Observation> queue;
    private final AnalysisEngine engine;
    private final AtomicLong dropped = new AtomicLong();
    private final Thread thread;
    private volatile boolean running = true;

    public AnalysisThread(AnalysisEngine engine, int capacity) {
        this.engine = engine;
        this.queue = new ArrayBlockingQueue<>(capacity);
        this.thread = new Thread(this::loop, "Vertex-Analysis");
        this.thread.setDaemon(true);
    }

    public void start() {
        thread.start();
    }

    /** Never blocks. */
    public void submit(Observation observation) {
        if (!running || !queue.offer(observation)) {
            dropped.incrementAndGet();
        }
    }

    private void loop() {
        while (running || !queue.isEmpty()) {
            try {
                Observation next = queue.poll(250, TimeUnit.MILLISECONDS);
                if (next != null) {
                    engine.process(next);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    public void stop() {
        running = false;
        try {
            thread.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (thread.isAlive()) {
            thread.interrupt();
        }
    }

    public long dropped() {
        return dropped.get();
    }

    public int queued() {
        return queue.size();
    }
}
