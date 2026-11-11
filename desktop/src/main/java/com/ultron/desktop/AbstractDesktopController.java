package com.ultron.desktop;

import com.ultron.core.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.security.MessageDigest;
import java.util.*;
import java.util.List;

abstract class AbstractDesktopController implements PlatformController {
    protected final Robot robot;
    private final java.util.concurrent.atomic.AtomicBoolean stopRequested = new java.util.concurrent.atomic.AtomicBoolean(false);
    protected AbstractDesktopController() throws AWTException { robot = new Robot(); robot.setAutoDelay(15); }

    protected abstract String launch(String app) throws Exception;
    protected abstract boolean has(String executable);

    @Override public List<Capability> capabilities() {
        return List.of(
            new Capability("desktop.launch", "Launch an allowlisted desktop application", CapabilityStatus.AVAILABLE),
            new Capability("desktop.screen", "Capture a local desktop screenshot", CapabilityStatus.AVAILABLE),
            new Capability("desktop.input", "Mouse and keyboard input via OS desktop session", CapabilityStatus.AVAILABLE),
            new Capability("desktop.clipboard", "Local clipboard access", CapabilityStatus.AVAILABLE)
        );
    }

    @Override public Observation observe() throws Exception {
        Rectangle bounds = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        BufferedImage image = robot.createScreenCapture(bounds);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (int y = 0; y < image.getHeight(); y += 12)
            for (int x = 0; x < image.getWidth(); x += 12) {
                int pixel = image.getRGB(x, y);
                digest.update((byte)(pixel >>> 24)); digest.update((byte)(pixel >>> 16));
                digest.update((byte)(pixel >>> 8)); digest.update((byte)pixel);
            }
        String fingerprint = HexFormat.of().formatHex(digest.digest());
        return new Observation("Desktop screenshot " + bounds.width + "x" + bounds.height,
            fingerprint, java.time.Instant.now(), Map.of("width", ""+bounds.width, "height", ""+bounds.height));
    }

    @Override public void emergencyStop() { stopRequested.set(true); }
    @Override public void resetEmergencyStop() { stopRequested.set(false); }

    @Override public ActionResult execute(PlatformAction action) throws Exception {
        if (stopRequested.get()) return new ActionResult(false, "Emergency stop is active.", "");
        Map<String,String> a = action.arguments();
        switch (action.operation()) {
            case "launch_app" -> {
                String app = required(a, "app");
                String result = launch(app);
                return new ActionResult(true, result, "launched:" + app);
            }
            case "click" -> {
                int x = integer(a, "x"), y = integer(a, "y");
                robot.mouseMove(x, y); robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
                return new ActionResult(true, "Clicked at " + x + "," + y, "click:" + x + ":" + y);
            }
            case "type_text" -> {
                String value = required(a, "text");
                if (value.length() > 500) throw new IllegalArgumentException("Text input limited to 500 characters.");
                for (char c : value.toCharArray()) {
                    if (stopRequested.get()) return new ActionResult(false, "Typing interrupted by emergency stop.", "");
                    int code = KeyEvent.getExtendedKeyCodeForChar(c);
                    if (code == KeyEvent.VK_UNDEFINED) throw new IllegalArgumentException("Unsupported character in keyboard input.");
                    boolean upper = Character.isUpperCase(c);
                    if (upper) robot.keyPress(KeyEvent.VK_SHIFT);
                    robot.keyPress(code); robot.keyRelease(code);
                    if (upper) robot.keyRelease(KeyEvent.VK_SHIFT);
                }
                return new ActionResult(true, "Typed " + value.length() + " characters.", "typed:" + value.length());
            }
            case "scroll" -> {
                int amount = Math.max(-10, Math.min(10, integer(a, "amount")));
                robot.mouseWheel(amount);
                return new ActionResult(true, "Scroll input sent.", "scroll:" + amount);
            }
            default -> throw new IllegalArgumentException("Unsupported desktop operation: " + action.operation());
        }
    }

    protected static String required(Map<String,String> a, String key) {
        String value = a.get(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing " + key);
        return value;
    }
    protected static int integer(Map<String,String> a, String key) {
        return Integer.parseInt(required(a,key));
    }
}
