package com.ultron.desktop;

import com.ultron.core.*;
import com.ultron.desktop.ai.DesktopModelSettings;
import com.ultron.desktop.ai.DesktopModelClient;
import com.ultron.desktop.ai.DesktopPlanProvider;
import com.ultron.core.agent.AgentPlan;
import com.ultron.core.agent.AgentStep;
import com.ultron.core.agent.PlanProvider;
import com.ultron.core.ai.ModelProviderRegistry;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.DefaultCaret;
import javax.swing.text.Document;
import javax.swing.text.Element;
import java.awt.*;
import java.awt.event.*;
import java.util.Map;
import java.util.concurrent.*;

/** OS-native, responsive desktop command center. UI work stays on the EDT; agent work stays off it. */
public final class UltronDesktop {
    private static final int MAX_LOG_LINES = 1200;
    private final JFrame frame = new JFrame("ULTRON 10.0 — Command Center");
    private final JTextArea log = new JTextArea();
    private final JTextField command = new JTextField();
    private final JLabel status = new JLabel("● IDLE");
    private final JLabel platform = new JLabel("Detecting platform…");
    private final JButton run = new JButton("Run command");
    private final JButton stop = new JButton("Emergency stop");
    private final ExecutorService actions = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ultron-desktop-agent"); t.setDaemon(true); return t;
    });
    private AgentRuntime runtime;
    private PlatformController controller;
    private DesktopModelClient modelClient;
    private PlanProvider planProvider;
    private final ModelProviderRegistry modelProviders = new ModelProviderRegistry();

    public static void main(String[] args) {
        configureNativeLookAndFeel();
        SwingUtilities.invokeLater(() -> new UltronDesktop().show());
    }

    private static void configureNativeLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) { /* Swing cross-platform fallback */ }
        // Respect OS HiDPI defaults; only set a scale if the user explicitly supplied one.
        String scale = System.getProperty("ultron.ui.scale");
        if (scale != null) System.setProperty("sun.java2d.uiScale", scale);
    }

    private void show() {
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.setMinimumSize(new Dimension(620, 420));
        frame.setPreferredSize(new Dimension(980, 680));
        frame.setLayout(new BorderLayout(0, 12));
        ((JComponent) frame.getContentPane()).setBorder(new EmptyBorder(16, 18, 14, 18));

        frame.add(buildHeader(), BorderLayout.NORTH);
        frame.add(buildWorkspace(), BorderLayout.CENTER);
        frame.add(buildCommandBar(), BorderLayout.SOUTH);
        installShortcuts();
        initializeAgent();
        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { shutdown(); }
        });
        frame.pack(); frame.setLocationRelativeTo(null); frame.setVisible(true);
        command.requestFocusInWindow();
    }

    private JComponent buildHeader() {
        JPanel header = new JPanel(new BorderLayout(12, 8));
        JPanel identity = new JPanel(); identity.setLayout(new BoxLayout(identity, BoxLayout.Y_AXIS));
        JLabel brand = new JLabel("ULTRON 10.0");
        brand.setFont(brand.getFont().deriveFont(Font.BOLD, 22f));
        JLabel subtitle = new JLabel("CROSS-PLATFORM AGENT  /  COMMAND CENTER");
        subtitle.setFont(subtitle.getFont().deriveFont(Font.PLAIN, 10f));
        subtitle.setForeground(UIManager.getColor("Label.disabledForeground"));
        identity.add(brand); identity.add(Box.createVerticalStrut(3)); identity.add(subtitle);
        JPanel right = new JPanel(); right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        status.setFont(status.getFont().deriveFont(Font.BOLD, 13f));
        status.setHorizontalAlignment(SwingConstants.RIGHT);
        platform.setHorizontalAlignment(SwingConstants.RIGHT);
        platform.setFont(platform.getFont().deriveFont(11f));
        right.add(status); right.add(Box.createVerticalStrut(5)); right.add(platform);
        header.add(identity, BorderLayout.WEST); header.add(right, BorderLayout.EAST);
        return header;
    }

    private JComponent buildWorkspace() {
        JPanel workspace = new JPanel(new BorderLayout(0, 8));
        JPanel section = new JPanel(new BorderLayout());
        JLabel title = new JLabel("ACTIVITY & VERIFICATION");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 11f));
        section.add(title, BorderLayout.WEST);
        JButton clear = new JButton("Clear"); clear.addActionListener(e -> log.setText(""));
        clear.setFocusable(false); section.add(clear, BorderLayout.EAST);
        log.setEditable(false); log.setLineWrap(true); log.setWrapStyleWord(true);
        log.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        log.setMargin(new Insets(12, 12, 12, 12));
        ((DefaultCaret) log.getCaret()).setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        JScrollPane scroll = new JScrollPane(log);
        scroll.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor")),
            new EmptyBorder(2, 2, 2, 2)));
        workspace.add(section, BorderLayout.NORTH); workspace.add(scroll, BorderLayout.CENTER);
        return workspace;
    }

    private JComponent buildCommandBar() {
        JPanel outer = new JPanel(new BorderLayout(8, 8));
        JLabel hint = new JLabel("Try: open chrome   ·   click 400 300   ·   type hello   ·   scroll 3");
        hint.setFont(hint.getFont().deriveFont(10f));
        hint.setForeground(UIManager.getColor("Label.disabledForeground"));
        outer.add(hint, BorderLayout.NORTH);
        command.setToolTipText("Enter a command and press Enter");
        command.addActionListener(e -> executeCommand());
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        run.addActionListener(e -> executeCommand());
        stop.setForeground(new Color(180, 45, 45));
        stop.addActionListener(e -> { if (runtime != null) runtime.emergencyStop(); append("EMERGENCY STOP requested."); });
        controls.add(stop); controls.add(run);
        outer.add(command, BorderLayout.CENTER); outer.add(controls, BorderLayout.EAST);
        return outer;
    }

    private void initializeAgent() {
        try {
            boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
            controller = windows ? new WindowsPlatformController() : new LinuxPlatformController();
            runtime = new AgentRuntime(controller,
                this::confirmPermission,
                (state, detail) -> SwingUtilities.invokeLater(() -> {
                    status.setText("● " + state.name()); append(state + "  /  " + detail);
                    boolean busy = state == AgentState.EXECUTING || state == AgentState.OBSERVING || state == AgentState.VERIFYING;
                    run.setEnabled(!busy); command.setEnabled(!busy);
                }));
            platform.setText(controller.platformId().toUpperCase() + "  ·  NATIVE UI");
            DesktopModelSettings aiSettings = DesktopModelSettings.fromEnvironment();
            modelClient = new DesktopModelClient(aiSettings);
            modelProviders.register(modelClient);
            planProvider = new DesktopPlanProvider(modelClient);
            modelProviders.capabilities().forEach(c -> append("Model capability descriptor: " + c.providerId() +
                " · local=" + c.local() + " · streaming=" + c.streaming() + " · tools=" + c.toolCalling() +
                " · vision=" + c.vision() + " · context=" + (c.contextWindowTokens() == 0 ? "unknown" : c.contextWindowTokens())));
            append("AI mode: " + aiSettings.mode() + (aiSettings.mode() == com.ultron.core.ai.ModelMode.LOCAL ? " · local provider" : aiSettings.cloudConfigured() ? " · cloud provider configured" : " · cloud provider not configured"));
            append("Ready. Actions are permission-gated and verified.");
            controller.capabilities().forEach(c -> append(c.id() + " — " + c.status()));
        } catch (Exception ex) { append("Desktop initialization failed: " + ex.getMessage()); }
    }

    private boolean confirmPermission(PlatformAction action) {
        if (SwingUtilities.isEventDispatchThread()) return showPermissionDialog(action);
        final boolean[] allowed = {false};
        try { SwingUtilities.invokeAndWait(() -> allowed[0] = showPermissionDialog(action)); }
        catch (Exception ignored) { return false; }
        return allowed[0];
    }

    private boolean showPermissionDialog(PlatformAction action) {
        return JOptionPane.showConfirmDialog(frame,
            "Allow " + action.operation() + " on this computer?\n" + action.arguments(),
            "ULTRON permission", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE)
            == JOptionPane.YES_OPTION;
    }

    private void executeCommand() {
        if (runtime == null || run.isEnabled() == false) return;
        String raw = command.getText().trim(); command.setText("");
        if (raw.isEmpty()) return;
        if (raw.equalsIgnoreCase("reset stop")) { runtime.resetStop(); return; }
        try {
            String[] p = raw.split("\\s+", 3);
            PlatformAction action;
            if (p.length >= 2 && p[0].equalsIgnoreCase("open")) {
                action = new PlatformAction("desktop.launch", "launch_app", Map.of("app", p[1]), true);
            } else if (p.length == 3 && p[0].equalsIgnoreCase("click")) {
                action = new PlatformAction("desktop.input", "click", Map.of("x", p[1], "y", p[2]), true);
            } else if (p.length >= 2 && p[0].equalsIgnoreCase("type")) {
                action = new PlatformAction("desktop.input", "type_text", Map.of("text", raw.substring(5)), true);
            } else if (p.length == 2 && p[0].equalsIgnoreCase("scroll")) {
                action = new PlatformAction("desktop.input", "scroll", Map.of("amount", p[1]), true);
            } else {
                run.setEnabled(false); command.setEnabled(false);
                actions.submit(() -> interpretAndExecute(raw));
                return;
            }
            run.setEnabled(false); command.setEnabled(false);
            actions.submit(() -> finishAction(runtime.run(action)));
        } catch (Exception ex) { append("Input error: " + ex.getMessage()); }
    }

    /** Natural-language requests become a bounded shared AgentPlan, then pass the existing permission/verification runtime. */
    private void interpretAndExecute(String raw) {
        try {
            if (planProvider == null) throw new IllegalStateException("AI planner is unavailable.");
            String availableTools = "desktop.launch: launch an allowlisted desktop application\n" +
                "desktop.input: click, type text, or scroll within the desktop controller";
            AgentPlan plan = planProvider.createPlan(raw, "Desktop session; no untrusted screen content is supplied to the planner.", availableTools);
            if (plan.steps().size() != 1) throw new IllegalArgumentException("No supported single action was produced. No action was executed.");
            AgentStep step = plan.steps().get(0);
            Map<String,String> args = new java.util.HashMap<>(step.arguments());
            String operation = args.remove("operation");
            boolean valid = (step.toolId().equals("desktop.launch") && "launch_app".equals(operation) && args.keySet().equals(java.util.Set.of("app"))) ||
                (step.toolId().equals("desktop.input") && "click".equals(operation) && args.keySet().equals(java.util.Set.of("x", "y"))) ||
                (step.toolId().equals("desktop.input") && "type_text".equals(operation) && args.keySet().equals(java.util.Set.of("text"))) ||
                (step.toolId().equals("desktop.input") && "scroll".equals(operation) && args.keySet().equals(java.util.Set.of("amount")));
            if (!valid) throw new IllegalArgumentException("Planner returned an unsupported operation or argument set.");
            finishAction(runtime.run(new PlatformAction(step.toolId(), operation, args, true)));
        } catch (Exception ex) {
            SwingUtilities.invokeLater(() -> append("AI planning failed: " + ex.getMessage()));
            finishAction(new ActionResult(false, "No action was executed. " + ex.getMessage(), ""));
        }
    }

    private void finishAction(ActionResult result) {
        SwingUtilities.invokeLater(() -> {
            append((result.success() ? "SUCCESS: " : "NOT VERIFIED: ") + result.message());
            run.setEnabled(true); command.setEnabled(true); command.requestFocusInWindow();
        });
    }

    private void installShortcuts() {
        JRootPane root = frame.getRootPane();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "ultron-stop");
        root.getActionMap().put("ultron-stop", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { if (runtime != null) runtime.emergencyStop(); }
        });
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK), "ultron-run");
        root.getActionMap().put("ultron-run", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { executeCommand(); }
        });
    }

    private void append(String text) {
        if (!SwingUtilities.isEventDispatchThread()) { SwingUtilities.invokeLater(() -> append(text)); return; }
        log.append(text + "\n");
        Document doc = log.getDocument();
        Element root = doc.getDefaultRootElement();
        int excess = root.getElementCount() - MAX_LOG_LINES;
        if (excess > 0) {
            Element lastRemoved = root.getElement(excess - 1);
            try { doc.remove(0, lastRemoved.getEndOffset()); } catch (Exception ignored) { }
        }
    }

    private void shutdown() {
        if (runtime != null) runtime.emergencyStop();
        actions.shutdownNow();
        if (controller != null) try { controller.close(); } catch (Exception ignored) { }
        frame.dispose();
    }
}
