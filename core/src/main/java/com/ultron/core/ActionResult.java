package com.ultron.core;

public record ActionResult(boolean success, String message, String resultingFingerprint) {
    public ActionResult {
        message = message == null ? "" : message;
        resultingFingerprint = resultingFingerprint == null ? "" : resultingFingerprint;
    }
}
