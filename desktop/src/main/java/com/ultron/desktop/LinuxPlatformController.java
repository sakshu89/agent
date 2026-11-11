package com.ultron.desktop;

import java.awt.AWTException;
import java.io.IOException;
import java.util.Map;
import java.util.List;
import com.ultron.core.Capability;
import com.ultron.core.CapabilityStatus;

public final class LinuxPlatformController extends AbstractDesktopController {
    public LinuxPlatformController() throws AWTException { super(); }
    @Override public String platformId() { return "linux"; }
    @Override public List<Capability> capabilities() {
        boolean wayland = System.getenv("WAYLAND_DISPLAY") != null || "wayland".equalsIgnoreCase(System.getenv("XDG_SESSION_TYPE"));
        if (!wayland) return super.capabilities();
        return super.capabilities().stream().map(c ->
            c.id().equals("desktop.input") || c.id().equals("desktop.screen")
                ? new Capability(c.id(), c.description() + " (Wayland session: Java AWT control is not guaranteed)", CapabilityStatus.TEMPORARILY_UNAVAILABLE)
                : c).toList();
    }
    @Override protected boolean has(String executable) {
        try { return new ProcessBuilder("sh","-lc","command -v " + executable).start().waitFor() == 0; }
        catch (Exception e) { return false; }
    }

    @Override protected String launch(String app) throws Exception {
        Map<String,String> allow = Map.of(
            "files", "xdg-open $HOME", "browser", "xdg-open https://www.google.com",
            "firefox", "firefox", "chrome", "google-chrome",
            "terminal", "x-terminal-emulator", "calculator", "gnome-calculator"
        );
        String command = allow.get(app.toLowerCase());
        if (command == null) throw new IllegalArgumentException("App is not in the built-in allowlist: " + app);
        if (command.startsWith("xdg-open ")) new ProcessBuilder("sh","-lc",command).start();
        else new ProcessBuilder(command.split(" ")).start();
        return "Launch requested for " + app;
    }
}
