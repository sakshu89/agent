package com.ultron.core.workflow;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.Base64;

/** Append-only, crash-tolerant workflow journal. Payloads are intentionally limited to non-secret metadata. */
public final class WorkflowJournal {
    public enum State { CREATED, PLANNED, WAITING_FOR_APPROVAL, RUNNING, VERIFYING, COMPLETED, FAILED, CANCELLED, RECOVERY_REQUIRED }
    public record Event(String taskId, String stepId, State state, Instant at, String detail) {
        public Event {
            if (taskId == null || taskId.isBlank()) throw new IllegalArgumentException("taskId required");
            if (stepId == null || stepId.isBlank()) throw new IllegalArgumentException("stepId required");
            Objects.requireNonNull(state, "state"); at = at == null ? Instant.now() : at;
            detail = redact(detail == null ? "" : detail);
            if (detail.length() > 1000) detail = detail.substring(0, 1000);
        }
    }
    private final Path file;
    public WorkflowJournal(Path file) throws IOException {
        this.file = Objects.requireNonNull(file).toAbsolutePath();
        Path parent = this.file.getParent(); if (parent != null) Files.createDirectories(parent);
        if (!Files.exists(this.file)) Files.createFile(this.file);
    }
    public synchronized void append(Event event) throws IOException {
        String line = enc(event.taskId()) + "\t" + enc(event.stepId()) + "\t" + event.state().name() + "\t" + event.at() + "\t" + enc(event.detail()) + "\n";
        byte[] bytes = line.getBytes(StandardCharsets.UTF_8);
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
            java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(bytes); while (buffer.hasRemaining()) channel.write(buffer); channel.force(true);
        }
    }
    /** Ignores a torn final line, but rejects malformed complete records rather than silently losing history. */
    public synchronized List<Event> readAll() throws IOException {
        byte[] bytes = Files.readAllBytes(file); String raw = new String(bytes, StandardCharsets.UTF_8);
        String[] lines = raw.split("\\n", -1); List<Event> events = new ArrayList<>();
        for (int i=0; i<lines.length; i++) {
            String line = lines[i]; if (line.isEmpty()) continue;
            if (i == lines.length - 1 && !raw.endsWith("\n")) break;
            String[] p = line.split("\\t", -1); if (p.length != 5) throw new IOException("Malformed workflow journal record at line " + (i+1));
            try { events.add(new Event(dec(p[0]), dec(p[1]), State.valueOf(p[2]), Instant.parse(p[3]), dec(p[4]))); }
            catch (RuntimeException e) { throw new IOException("Invalid workflow journal record at line " + (i+1), e); }
        }
        return List.copyOf(events);
    }
    /** Active tasks at restart are marked for explicit review; actions are never replayed automatically. */
    public synchronized Map<String,State> recoverySnapshot() throws IOException {
        Map<String,State> latest = new LinkedHashMap<>();
        for (Event e : readAll()) latest.put(e.taskId()+"/"+e.stepId(), e.state());
        latest.replaceAll((id,state) -> switch (state) {
            case CREATED, PLANNED, WAITING_FOR_APPROVAL, RUNNING, VERIFYING -> State.RECOVERY_REQUIRED;
            default -> state;
        });
        return Collections.unmodifiableMap(latest);
    }
    private static String enc(String s) { return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8)); }
    private static String dec(String s) { return new String(Base64.getUrlDecoder().decode(s), StandardCharsets.UTF_8); }
    private static String redact(String s) {
        return s.replaceAll("(?i)(api[_-]?key|token|password|authorization)\\s*[:=]\\s*[^\\s,;]+", "$1=[REDACTED]")
                .replaceAll("(?i)Bearer\\s+[A-Za-z0-9._~+/-]+=*", "Bearer [REDACTED]")
                .replaceAll("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", "[EMAIL]");
    }
}
