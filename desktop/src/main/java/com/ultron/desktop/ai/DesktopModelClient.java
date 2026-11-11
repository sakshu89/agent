package com.ultron.desktop.ai;

import com.ultron.core.ai.HybridModelRouter;
import com.ultron.core.ai.ModelMode;
import com.ultron.core.ai.ModelRequest;
import com.ultron.core.ai.ModelRoute;
import com.ultron.core.ai.ModelProvider;
import com.ultron.core.ai.ModelCapabilities;
import com.ultron.core.ai.ModelPrompt;
import java.net.URI;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** OpenAI-compatible chat client for desktop agent reasoning. Does not log prompts or credentials. */
public final class DesktopModelClient implements ModelProvider {
    private final DesktopModelSettings settings;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    public DesktopModelClient(DesktopModelSettings settings) { this.settings = settings; }
    @Override public String id() { return "desktop-openai-compatible"; }
    @Override public ModelCapabilities capabilities() {
        boolean cloudSelected = settings.mode() == ModelMode.CLOUD;
        return new ModelCapabilities(cloudSelected ? "openai-compatible-cloud" : "openai-compatible-local",
            !cloudSelected, false, false, false, false, 0, 0);
    }
    @Override public String complete(ModelPrompt prompt) throws Exception {
        return complete(prompt.system(), prompt.user(), prompt.request().containsPrivateData());
    }

    public String complete(String systemPrompt, String userPrompt, boolean containsPrivateData) throws Exception {
        ModelRoute route = HybridModelRouter.choose(settings.mode(),
            new ModelRequest("desktop-agent", containsPrivateData || settings.privacyMode(),
                !settings.privacyMode() && (settings.mode() == ModelMode.CLOUD || settings.allowCloudFallback()), true, null), settings.cloudConfigured());
        if (settings.mode() == ModelMode.CLOUD && route.backend() != ModelRoute.Backend.CLOUD)
            throw new IllegalStateException((containsPrivateData || settings.privacyMode()) ? "Cloud blocked by privacy protection." : "Cloud mode is not configured.");
        if (route.backend() == ModelRoute.Backend.CLOUD) return send(systemPrompt, userPrompt, true);
        try { return send(systemPrompt, userPrompt, false); }
        catch (Exception localFailure) {
            if (HybridModelRouter.shouldFallback(route, 0, true)) return send(systemPrompt, userPrompt, true);
            throw localFailure;
        }
    }

    private String send(String systemPrompt, String userPrompt, boolean cloud) throws Exception {
        String base = cloud ? settings.cloudBaseUrl() : settings.localBaseUrl();
        String model = cloud ? settings.cloudModel() : settings.localModel();
        if (cloud && !settings.cloudConfigured()) throw new IllegalStateException("Cloud provider is not configured.");
        if (base == null || base.isBlank()) throw new IllegalArgumentException("Model endpoint is missing.");
        URI baseUri = URI.create(base);
        if (baseUri.getUserInfo() != null || baseUri.getHost() == null) throw new IllegalArgumentException("Model endpoint must not contain embedded credentials and must have a valid host.");
        if (cloud && !"https".equalsIgnoreCase(baseUri.getScheme())) throw new IllegalArgumentException("Cloud endpoint must use HTTPS.");
        if (!cloud && !isPrivateEndpoint(baseUri)) throw new IllegalArgumentException("Local model endpoint must use loopback, a private IP, or a .local hostname. Public endpoints require explicit cloud configuration and consent.");
        String endpoint = base.replaceAll("/+$", "") + "/chat/completions";
        JsonObject payload = new JsonObject();
        payload.addProperty("model", model);
        payload.addProperty("stream", false);
        JsonArray messages = new JsonArray();
        JsonObject system = new JsonObject(); system.addProperty("role", "system"); system.addProperty("content", systemPrompt == null ? "" : systemPrompt); messages.add(system);
        JsonObject user = new JsonObject(); user.addProperty("role", "user"); user.addProperty("content", userPrompt == null ? "" : userPrompt); messages.add(user);
        payload.add("messages", messages);
        String body = payload.toString();
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(endpoint)).timeout(Duration.ofSeconds(90))
            .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        if (cloud) builder.header("Authorization", "Bearer " + settings.cloudApiKey());
        HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300)
            throw new IllegalStateException("Model request failed with HTTP status " + response.statusCode() + ". Response body omitted to avoid leaking provider details.");
        try {
            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            JsonArray choices = root.getAsJsonArray("choices");
            if (choices == null || choices.isEmpty()) throw new IllegalStateException("Model response contained no choices.");
            JsonObject message = choices.get(0).getAsJsonObject().getAsJsonObject("message");
            if (message == null || !message.has("content") || message.get("content").isJsonNull())
                throw new IllegalStateException("Model response did not contain choices[0].message.content.");
            return message.get("content").getAsString();
        } catch (RuntimeException parseFailure) {
            if (parseFailure instanceof IllegalStateException) throw parseFailure;
            throw new IllegalStateException("Model response was not valid OpenAI-compatible JSON.", parseFailure);
        }
    }

    private static boolean isPrivateEndpoint(URI uri) {
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) return false;
        String host = uri.getHost().toLowerCase(java.util.Locale.ROOT);
        if (host.equals("localhost") || host.endsWith(".localhost") || host.endsWith(".local")) return true;
        // Only resolve numeric address literals; arbitrary DNS names are not trusted as local.
        if (!host.matches("[0-9a-fA-F:.]+")) return false;
        try {
            InetAddress address = InetAddress.getByName(host);
            return address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress();
        } catch (UnknownHostException e) { return false; }
    }
}
