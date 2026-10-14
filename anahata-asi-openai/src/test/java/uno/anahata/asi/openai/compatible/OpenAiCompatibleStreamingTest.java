/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. ForÃ§a BarÃ§a! */
package uno.anahata.asi.openai.compatible;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import uno.anahata.asi.agi.Agi;
import uno.anahata.asi.agi.message.AbstractModelMessage;
import uno.anahata.asi.agi.provider.GenerationRequest;
import uno.anahata.asi.agi.provider.RequestConfig;
import uno.anahata.asi.agi.provider.Response;
import uno.anahata.asi.agi.provider.StreamObserver;
import uno.anahata.asi.agi.status.RawApiJson;
import uno.anahata.asi.internal.JacksonUtils;

/** Tests the real streaming loop with an in-memory transport, without API requests. */
class OpenAiCompatibleStreamingTest {
    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void finalResponsePreservesProviderUsageAfterRawJsonIsMovedToTelemetry() throws Exception {
        Agi agi = mock(Agi.class);
        RequestConfig config = mock(RequestConfig.class);
        when(config.getAgi()).thenReturn(agi);
        GenerationRequest request = new GenerationRequest(config, java.util.List.of());
        OpenAiChatCompletionsProvider provider = mock(OpenAiChatCompletionsProvider.class);
        HttpClient client = mock(HttpClient.class);
        HttpResponse<Stream<String>> httpResponse = mock(HttpResponse.class);
        when(provider.createHttpClient()).thenReturn(client);
        when(provider.createRequestBuilder(anyString())).thenAnswer(invocation ->
                HttpRequest.newBuilder(URI.create("https://example.invalid/test")));
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(Stream.of(
                "data: {\"type\":\"response.created\",\"id\":\"test-response\"}",
                "data: {\"type\":\"response.completed\",\"id\":\"test-response\",\"usage\":{\"prompt_tokens\":11,\"completion_tokens\":7,\"total_tokens\":18}}",
                "data: [DONE]"));
        doReturn(httpResponse).when(client).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        OpenAiCompatibleModelMessage target = mock(OpenAiCompatibleModelMessage.class);
        when(target.getBilledPromptTokens()).thenReturn(11);
        when(target.getBilledCompletionTokens()).thenReturn(7);
        OpenAiCompatibleModel model = new OpenAiCompatibleModel(provider, "anahata-test", "Anahata test") {
            @Override
            protected ObjectNode preparePayload(GenerationRequest ignored, boolean streaming) {
                return JacksonUtils.parse("{\"model\":\"anahata-test\",\"messages\":[],\"stream\":true}", ObjectNode.class);
            }
            @Override
            public OpenAiCompatibleModelMessage createModelMessage(Agi ignored) {
                return target;
            }
        };
        StreamObserver<Response<? extends AbstractModelMessage>> observer = mock(StreamObserver.class);

        model.generateContentStream(request, observer);

        ArgumentCaptor<OpenAiCompatibleResponse> finalResponse = ArgumentCaptor.forClass(OpenAiCompatibleResponse.class);
        verify(target).setResponse(finalResponse.capture());
        assertEquals(11, finalResponse.getValue().getUsageMetadata().getPromptTokenCount());
        assertEquals(7, finalResponse.getValue().getUsageMetadata().getCandidatesTokenCount());
        assertEquals(18, finalResponse.getValue().getTotalTokenCount());
        verify(observer).onComplete();
        verify(observer, never()).onError(any());
        ArgumentCaptor<RawApiJson> telemetry = ArgumentCaptor.forClass(RawApiJson.class);
        verify(agi, times(3)).setLastRawApiJson(telemetry.capture());
        assertEquals(2, JacksonUtils.parse(telemetry.getValue().responseJson(), com.fasterxml.jackson.databind.JsonNode.class).size());
        verify(client).close();
    }
}
