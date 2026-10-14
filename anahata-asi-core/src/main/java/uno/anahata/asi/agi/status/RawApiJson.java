/*
 * Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça!
 */
package uno.anahata.asi.agi.status;

import java.io.Serializable;

/**
 * Ephemeral wire-level debug snapshot of an AI API exchange.
 * <p>
 * Captures the exact JSON payloads exchanged over the wire (request configuration,
 * serialized conversation history, and raw provider response). Held exclusively
 * as a transient field on {@link uno.anahata.asi.agi.Agi} for live UI telemetry
 * in the StatusPanel, completely decoupled from historical messages and session persistence.
 * </p>
 *
 * @param requestConfigJson The raw JSON of the request configuration (system instructions, tools, etc.).
 * @param historyJson The raw JSON of the conversation history sent in the HTTP payload.
 * @param responseJson The raw JSON response returned by the AI provider.
 *
 * @author anahata
 */
public record RawApiJson(
        String requestConfigJson,
        String historyJson,
        String responseJson
) implements Serializable {

    /**
     * An empty fallback instance when no API exchange has occurred.
     */
    public static final RawApiJson EMPTY = new RawApiJson("", "", "");

    /**
     * Returns a new {@code RawApiJson} with the specified chunk appended to the response JSON array.
     * <p>
     * If {@code responseJson} is null or blank, wraps the chunk in a JSON array.
     * For subsequent chunks, the chunk is appended before the closing array bracket,
     * maintaining a valid JSON array format for syntax highlighting and inspection in the UI.
     * </p>
     *
     * @param chunk The raw JSON chunk to append.
     * @return A new {@code RawApiJson} instance containing the appended chunk.
     */
    public RawApiJson appendResponseChunk(String chunk) {
        if (chunk == null || chunk.isBlank()) {
            return this;
        }
        String trimmedChunk = chunk.trim();
        String newResponse;
        if (this.responseJson == null || this.responseJson.isBlank()) {
            newResponse = "[\n" + trimmedChunk + "\n]";
        } else {
            String trimmed = this.responseJson.trim();
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                String inner = trimmed.substring(1, trimmed.length() - 1).trim();
                if (inner.isEmpty()) {
                    newResponse = "[\n" + trimmedChunk + "\n]";
                } else {
                    newResponse = "[\n" + inner + ",\n" + trimmedChunk + "\n]";
                }
            } else {
                newResponse = "[\n" + trimmed + ",\n" + trimmedChunk + "\n]";
            }
        }
        return new RawApiJson(this.requestConfigJson, this.historyJson, newResponse);
    }
}
