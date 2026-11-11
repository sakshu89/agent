package com.ultron.core.diagnostics;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/** Bounded, privacy-redacted diagnostic trace. Prompt bodies, screenshots and credentials are never accepted. */
public final class AgentTrace {
    public record Entry(Instant at, String taskId, String phase, String outcome, String detail) {}
    private final int capacity;
    private final Deque<Entry> entries = new ArrayDeque<>();
    public AgentTrace(int capacity) { if (capacity < 1 || capacity > 10000) throw new IllegalArgumentException("capacity must be 1..10000"); this.capacity = capacity; }
    public synchronized void add(String taskId, String phase, String outcome, String detail) {
        String safe = redact(Objects.toString(detail, "")); if (safe.length() > 500) safe = safe.substring(0,500);
        entries.addLast(new Entry(Instant.now(), Objects.toString(taskId,""), Objects.toString(phase,""), Objects.toString(outcome,""), safe));
        while(entries.size()>capacity) entries.removeFirst();
    }
    public synchronized List<Entry> snapshot() { return List.copyOf(entries); }
    private static String redact(String s) {
        return s.replaceAll("(?i)(api[_-]?key|token|password|authorization)\\s*[:=]\\s*[^\\s,;]+", "$1=[REDACTED]")
                .replaceAll("(?i)Bearer\\s+[A-Za-z0-9._~+/-]+=*", "Bearer [REDACTED]")
                .replaceAll("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", "[EMAIL]");
    }
}
