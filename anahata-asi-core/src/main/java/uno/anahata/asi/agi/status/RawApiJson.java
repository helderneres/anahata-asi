/*
 * Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça!
 */
package uno.anahata.asi.agi.status;

import java.io.Serializable;

/**
 * Immutable container for provider JSON diagnostics of an API exchange.
 * <p>
 * Providers supply request configuration, serialized history and response text;
 * an SDK may reconstruct that text, so it is not a byte-for-byte HTTP capture.
 * The normal owner is the transient last-exchange field on
 * {@link uno.anahata.asi.agi.Agi}. This record itself is serializable: its
 * lifetime and persistence depend on where the caller retains it.
 * </p>
 * <p>
 * Values are not redacted, parsed or validated here and may contain sensitive
 * request or response data. Keep diagnostic inspection separate from public
 * tool results. Instances are immutable; publishing or replacing the latest
 * snapshot is the owner's responsibility.
 * </p>
 *
 * @param requestConfigJson provider configuration JSON, possibly null or empty
 * @param historyJson serialized request history, possibly null or empty
 * @param responseJson response JSON or accumulated chunk array, possibly null
 * or empty before a response is available
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
     * Returns a snapshot with a non-blank chunk appended to the response text.
     * The existing request configuration and history are carried over unchanged.
     * <p>
     * A first chunk is wrapped in an array; an existing bracketed array receives
     * another element. Other non-blank response text is treated as the first element.
     * This is string assembly, not JSON validation: callers must supply valid JSON
     * values. Each append copies the accumulated text, so this is not a bounded or
     * constant-cost streaming buffer.
     * </p>
     *
     * @param chunk JSON text to append; null or blank leaves this instance unchanged
     * @return this instance for null/blank input, otherwise a new diagnostic snapshot
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
