package com.ultron.desktop.ai;

import com.google.gson.*;
import com.ultron.core.agent.*;
import java.util.*;

/** Converts desktop natural language into a bounded shared AgentPlan; it never executes tools. */
public final class DesktopPlanProvider implements PlanProvider {
    private static final int MAX_STEPS = 1; // Desktop UI currently exposes one-action commands only.
    private final DesktopModelClient model;
    public DesktopPlanProvider(DesktopModelClient model) { this.model = Objects.requireNonNull(model); }

    @Override public AgentPlan createPlan(String goal, String observation, String availableTools) throws Exception {
        String system = "Return exactly one JSON object, no markdown. Create a plan with schema " +
            "{\"steps\":[{\"id\":string,\"toolId\":string,\"objective\":string,\"arguments\":{\"operation\":string,...}}]}. " +
            "Use ONLY tool IDs listed in AVAILABLE_TOOLS. Never invent tools, commands, paths or applications. " +
            "If unsupported, return {\"steps\":[]}. At most one step. Include operation inside arguments; other arguments must be string or number primitives.\nAVAILABLE_TOOLS:\n" +
            Objects.toString(availableTools, "");
        String goalText = Objects.toString(goal, "");
        boolean sensitive = goalText.matches("(?is).*\\b(password|passcode|otp|secret|api key|card number)\\b.*")
            || goalText.matches(".*[0-9]{10,}.*")
            || goalText.matches("(?is).*\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b.*");
        String response = model.complete(system, "GOAL: " + goalText + "\nOBSERVATION: " + Objects.toString(observation, ""), sensitive);
        String json = response.trim().replaceFirst("(?s)^```(?:json)?\\s*", "").replaceFirst("(?s)\\s*```$", "").trim();
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonArray stepsJson = root.getAsJsonArray("steps");
        if (stepsJson == null || stepsJson.size() > MAX_STEPS) throw new IllegalArgumentException("Plan must contain at most one step.");
        Set<String> allowed = new HashSet<>();
        for (String line : Objects.toString(availableTools, "").split("\\R")) {
            int colon = line.indexOf(':'); if (colon > 0) allowed.add(line.substring(0, colon).trim());
        }
        List<AgentStep> steps = new ArrayList<>();
        for (JsonElement element : stepsJson) {
            JsonObject step = element.getAsJsonObject();
            String toolId = requiredString(step, "toolId");
            if (!allowed.contains(toolId)) throw new SecurityException("Planner selected an unregistered tool: " + toolId);
            JsonObject argsJson = step.getAsJsonObject("arguments");
            Map<String,String> args = new LinkedHashMap<>();
            if (argsJson != null) for (Map.Entry<String,JsonElement> entry : argsJson.entrySet()) {
                JsonElement value = entry.getValue();
                if (value == null || !value.isJsonPrimitive()) throw new IllegalArgumentException("Tool arguments must be primitive values.");
                args.put(entry.getKey(), value.getAsString());
            }
            steps.add(new AgentStep(requiredString(step, "id"), toolId,
                step.has("objective") ? step.get("objective").getAsString() : "", args));
        }
        return new AgentPlan(goal, steps, MAX_STEPS);
    }
    private static String requiredString(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()) throw new IllegalArgumentException("Missing string field: " + key);
        String value = object.get(key).getAsString();
        if (value.isBlank()) throw new IllegalArgumentException("Blank field: " + key);
        return value;
    }
}
