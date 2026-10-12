/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import lombok.Getter;
import uno.anahata.asi.agi.Agi;
import uno.anahata.asi.agi.message.ResponseUsageMetadata;
import uno.anahata.asi.agi.provider.Response;

/**
 * Specialized Response implementation for the OpenAI Responses API.
 * <p>
 * Aggregates the entire 'output' array from a Responses API call into a single
 * turn (one OpenAiModelMessage). Maps API usage metadata and orchestrates the
 * item-by-item processing of the model's output.</p>
 *
 * @author anahata
 */
@Getter
public class OpenAiResponse extends Response<OpenAiModelMessage> {

    /**
     * Internal mapper for response JSON parsing.
     */
    private static final ObjectMapper API_MAPPER = new ObjectMapper();

    /**
     * The unique identifier of the response.
     */
    private final String id;
    /**
     * The list of generated message candidates (exactly one for Responses API).
     */
    private final List<OpenAiModelMessage> candidates;
    /**
     * Metadata describing the API token consumption and reasoning costs.
     */
    private final ResponseUsageMetadata usageMetadata;
    /**
     * The specific version identifier returned by the model.
     */
    private final String modelVersion;

    /**
     * Constructs a new response object for a streaming chunk.
     */
    public OpenAiResponse() {
        this.id = "stream";
        this.usageMetadata = ResponseUsageMetadata.builder().build();
        this.modelVersion = "unknown";
        this.candidates = List.of();
    }

    /**
     * Constructs a new full response by parsing the OpenAI response body.
     * @param agi The parent AGI session.
     * @param modelId The expected model ID.
     * @param responseBody The raw API response body.
     * @throws Exception on parsing failures.
     */
    public OpenAiResponse(Agi agi, String modelId, String responseBody) throws Exception {
        JsonNode root = API_MAPPER.readTree(responseBody);
        this.id = root.path("id").asText("openai-response");
        this.modelVersion = root.path("model").asText(modelId);

        // 1. Map Usage
        JsonNode usage = root.get("usage");
        if (usage != null) {
            this.usageMetadata = ResponseUsageMetadata.builder()
                    .promptTokenCount(usage.path("input_tokens").asInt())
                    .candidatesTokenCount(usage.path("output_tokens").asInt())
                    .totalTokenCount(usage.path("total_tokens").asInt())
                    .rawJson(usage.toString())
                    .build();
        } else {
            this.usageMetadata = ResponseUsageMetadata.builder().build();
        }

        // 2. Aggregate all output items into ONE turn message
        OpenAiModelMessage turnMessage = new OpenAiModelMessage(agi, modelVersion);
        turnMessage.setResponse(this);

        JsonNode output = root.get("output");
        if (output != null && output.isArray()) {
            for (JsonNode item : output) {
                turnMessage.processItem(item);
            }
        }

        // OpenAI Responses API always generates exactly one turn (no candidate count parameter)
        this.candidates = List.of(turnMessage);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<String> getPromptFeedback() {
        return Optional.empty();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getTotalTokenCount() {
        return usageMetadata.getTotalTokenCount();
    }
}
