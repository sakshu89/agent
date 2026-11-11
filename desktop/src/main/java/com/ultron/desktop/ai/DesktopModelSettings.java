package com.ultron.desktop.ai;

import com.ultron.core.ai.ModelMode;

/** Desktop provider settings are supplied through environment variables; secrets are never written to logs. */
public record DesktopModelSettings(ModelMode mode, String localBaseUrl, String localModel,
                                   String cloudBaseUrl, String cloudModel, String cloudApiKey,
                                   boolean allowCloudFallback, boolean privacyMode) {
    public static DesktopModelSettings fromEnvironment() {
        String mode = env("ULTRON_AI_MODE", "LOCAL");
        String localUrl = env("ULTRON_LOCAL_BASE_URL", "http://127.0.0.1:8080/v1");
        String localModel = env("ULTRON_LOCAL_MODEL", "local-model");
        String cloudUrl = env("ULTRON_CLOUD_BASE_URL", "https://api.openai.com/v1");
        String cloudModel = env("ULTRON_CLOUD_MODEL", "");
        String key = env("ULTRON_CLOUD_API_KEY", "");
        boolean fallback = Boolean.parseBoolean(env("ULTRON_ALLOW_CLOUD_FALLBACK", "false"));
        boolean privacy = Boolean.parseBoolean(env("ULTRON_PRIVACY_MODE", "false"));
        return new DesktopModelSettings(ModelMode.from(mode), localUrl, localModel, cloudUrl, cloudModel, key, fallback, privacy);
    }
    public boolean cloudConfigured() {
        return cloudApiKey != null && !cloudApiKey.isBlank() && cloudModel != null && !cloudModel.isBlank()
            && cloudBaseUrl != null && cloudBaseUrl.startsWith("https://");
    }
    private static String env(String key, String fallback) {
        String value = System.getenv(key); return value == null || value.isBlank() ? fallback : value.trim();
    }
}
