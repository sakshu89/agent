package com.ultron.desktop;

import javax.sound.sampled.*;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Shared-mode desktop microphone capture for Windows and Linux.
 *
 * Java Sound opens the OS default capture endpoint without requesting multimedia
 * playback focus or changing the system output device/volume. On Windows this uses
 * the normal shared endpoint exposed by the Java Sound provider. On Linux it uses
 * the configured Java Sound capture provider (commonly PulseAudio/PipeWire or ALSA).
 *
 * This class deliberately does not implement speech recognition. The caller owns
 * recognition and must close this capture when listening is stopped.
 */
public final class DesktopMicrophoneCapture implements AutoCloseable {
    public static final AudioFormat DEFAULT_FORMAT =
        new AudioFormat(16000.0f, 16, 1, true, false);

    private final TargetDataLine line;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread worker;

    private DesktopMicrophoneCapture(TargetDataLine line) {
        this.line = Objects.requireNonNull(line);
    }

    /** Opens the default microphone in shared capture mode; no playback focus is requested. */
    public static DesktopMicrophoneCapture openDefault() throws LineUnavailableException {
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, DEFAULT_FORMAT);
        if (!AudioSystem.isLineSupported(info)) {
            throw new LineUnavailableException(
                "No Java Sound microphone input supports 16 kHz mono PCM on this system."
            );
        }
        TargetDataLine input = (TargetDataLine) AudioSystem.getLine(info);
        input.open(DEFAULT_FORMAT, 4096);
        return new DesktopMicrophoneCapture(input);
    }

    /**
     * Streams microphone PCM frames to the consumer on a daemon thread.
     * Consumer must return quickly; expensive recognition should run elsewhere.
     */
    public synchronized void start(Consumer<byte[]> onPcmFrame) {
        Objects.requireNonNull(onPcmFrame, "onPcmFrame");
        if (!running.compareAndSet(false, true)) return;
        line.start();
        worker = new Thread(() -> {
            byte[] frame = new byte[2048];
            try {
                while (running.get()) {
                    int count = line.read(frame, 0, frame.length);
                    if (count > 0 && running.get()) {
                        byte[] copy = new byte[count];
                        System.arraycopy(frame, 0, copy, 0, count);
                        onPcmFrame.accept(copy);
                    }
                }
            } catch (RuntimeException failure) {
                running.set(false);
                throw failure;
            }
        }, "ultron-microphone-capture");
        worker.setDaemon(true);
        worker.start();
    }

    public boolean isRunning() {
        return running.get();
    }

    @Override public synchronized void close() {
        running.set(false);
        line.stop();
        line.flush();
        line.close();
        if (worker != null && worker != Thread.currentThread()) {
            worker.interrupt();
            try { worker.join(500); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        worker = null;
    }
}
