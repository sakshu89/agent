package com.ultron.core.ai;

/** Minimal provider contract shared by Android and desktop adapters. */
public interface ModelProvider {
    String id();
    ModelCapabilities capabilities();
    String complete(ModelPrompt prompt) throws Exception;
}
