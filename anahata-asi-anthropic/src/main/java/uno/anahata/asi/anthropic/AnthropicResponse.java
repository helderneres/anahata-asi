/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.anthropic;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.Getter;
import uno.anahata.asi.agi.Agi;
import uno.anahata.asi.agi.message.ResponseUsageMetadata;
import uno.anahata.asi.agi.provider.Response;
import uno.anahata.asi.internal.JacksonUtils;

/**
 * Response implementation for Anthropic's Claude.
 * 
 * @author anahata
 */
@Getter
public class AnthropicResponse extends Response<AnthropicMessage> {

    /**
     * The unique response ID provided by Anthropic.
     */
    private final String id;
    /**
     * The list of generated messages.
     */
    private final List<AnthropicMessage> candidates = new ArrayList<>();
    /**
     * Token usage metadata.
     */
    private final ResponseUsageMetadata usageMetadata;

    /**
     * Constructs a full response from the Anthropic API.
     * @param agi The session.
     * @param modelId The model ID.
     * @param responseBody The raw response body.
     */
    public AnthropicResponse(Agi agi, String modelId, String responseBody) {
        JsonNode root = JacksonUtils.parse(responseBody, JsonNode.class);
        this.id = root.path("id").asText("anthropic-response");
        
        JsonNode usage = root.get("usage");
        if (usage != null) {
            int inputTokens = usage.path("input_tokens").asInt(0);
            int outputTokens = usage.path("output_tokens").asInt(0);
            this.usageMetadata = ResponseUsageMetadata.builder()
                    .promptTokenCount(inputTokens)
                    .candidatesTokenCount(outputTokens)
                    .totalTokenCount(inputTokens + outputTokens)
                    .rawJson(usage.toString())
                    .build();
        } else {
            this.usageMetadata = ResponseUsageMetadata.builder().build();
        }

        AnthropicMessage msg = new AnthropicMessage(agi, modelId);
        msg.setResponse(this);
        
        JsonNode content = root.get("content");
        if (content != null && content.isArray()) {
            msg.parseFinalContent(content);
        }
        if (root.has("stop_reason")) {
            msg.setFinishReasonFromAnthropic(root.get("stop_reason").asText());
        }
        candidates.add(msg);
    }
    
    /**
     * Constructs a stream chunk response.
     */
    public AnthropicResponse() {
        this.id = "stream";
        this.usageMetadata = ResponseUsageMetadata.builder().build();
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
        return usageMetadata != null ? usageMetadata.getTotalTokenCount() : 0;
    }
}