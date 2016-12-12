package org.gradle.trace;

public class TraceEvent {
    private final String name;
    private final String category;
    private final String type;
    private final long threadId;
    private final long timestamp;

    public TraceEvent(String name, String category, String type, long timestampNanos) {
        this.name = name;
        this.category = category;
        this.type = type;
        this.threadId = Thread.currentThread().getId();
        this.timestamp = timestampNanos / 1000;
    }

    static TraceEvent started(String name, String category) {
        return started(name, category, getTimestamp());
    }

    static TraceEvent started(String name, String category, long timestamp) {
        return new TraceEvent(name, category, "B", timestamp);
    }

    static TraceEvent finished(String name, String category) {
        return new TraceEvent(name, category, "E", getTimestamp());
    }

    @Override
    public String toString() {
        return String.format("{\"name\": \"%s\", \"cat\": \"%s\", \"ph\": \"%s\", \"pid\": 0, \"tid\": %d, \"ts\": %d}", name, category, type, threadId, timestamp);
    }

    private static long getTimestamp() {
        return (System.nanoTime());
    }
}
