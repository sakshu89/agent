package com.ultron.desktop;

import java.awt.AWTException;
import java.util.List;
import java.util.Map;

public final class WindowsPlatformController extends AbstractDesktopController {
    public WindowsPlatformController() throws AWTException { super(); }
    @Override public String platformId() { return "windows"; }
    @Override protected boolean has(String executable) { return true; }

    @Override protected String launch(String app) throws Exception {
        Map<String,String> allow = Map.of(
            "notepad", "notepad.exe", "calculator", "calc.exe",
            "paint", "mspaint.exe", "explorer", "explorer.exe",
            "chrome", "chrome.exe", "edge", "msedge.exe"
        );
        String command = allow.get(app.toLowerCase());
        if (command == null) throw new IllegalArgumentException("App is not in the built-in allowlist: " + app);
        new ProcessBuilder(command).start();
        return "Launched " + app;
    }
}
