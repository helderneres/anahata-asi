/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.agi.tool.graphql;

import graphql.schema.idl.RuntimeWiring;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uno.anahata.ai.tool.MockAsiContainer;
import uno.anahata.asi.agi.Agi;
import uno.anahata.asi.agi.AgiConfig;
import uno.anahata.asi.agi.message.AbstractModelMessage;
import uno.anahata.asi.agi.message.ResponseUsageMetadata;
import uno.anahata.asi.agi.provider.Response;
import uno.anahata.asi.agi.tool.AgiTool;
import uno.anahata.asi.agi.tool.AgiToolParam;
import uno.anahata.asi.agi.tool.AgiToolkit;
import uno.anahata.asi.agi.tool.ToolExecutionStatus;
import uno.anahata.asi.agi.tool.schema.SchemaProvider;
import uno.anahata.asi.agi.tool.spi.java.JavaMethodTool;
import uno.anahata.asi.agi.tool.spi.java.JavaMethodToolCall;
import uno.anahata.asi.agi.tool.spi.java.JavaObjectToolkit;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises provider argument binding without a provider, IDE or database.
 */
class GraphQLArgumentBindingTest {

    private JavaMethodTool method;
    private BindingToolkit toolkit;
    private TestMessage message;

    @BeforeEach
    void setUp() throws Exception {
        Agi agi = new Agi(new AgiConfig(new MockAsiContainer("graphql-binding-test"), "graphql-binding-test")) {
            @Override
            public void autoSave(String reason) {
                /* No session persistence in this fixture. */ }
        };
        JavaObjectToolkit wrapper = new JavaObjectToolkit(agi.getToolManager(), BindingToolkit.class);
        wrapper.initialize();
        toolkit = (BindingToolkit) wrapper.getToolkitInstance();
        method = wrapper.getAllTools().stream().filter(tool -> tool.getName().equals("BindingToolkit.mutate")).findFirst().orElseThrow();
        message = new TestMessage(agi);
    }

    @Test
    void advertisedVariablesContractIsObjectAndRejectsStringsBeforeHandler() throws Exception {
        var parameter = method.getParameters().stream().filter(p -> p.getName().equals("variables")).findFirst().orElseThrow();
        assertEquals("object", SchemaProvider.OBJECT_MAPPER.readTree(parameter.getJsonSchema()).path("type").asText());
        for (String invalid : List.of("{}", "{\"sql\":\"private-value\"}")) {
            JavaMethodToolCall call = method.createCall(message, "invalid-string", Map.of("document", "mutation { run }", "variables", invalid));
            assertEquals(ToolExecutionStatus.FAILED, call.getResponse().getStatus());
            var diagnostic = SchemaProvider.OBJECT_MAPPER.readTree(call.getResponse().getErrors());
            assertEquals("ARGUMENT_BINDING", diagnostic.path("phase").asText());
            assertEquals("variables", diagnostic.path("argument").asText());
            assertEquals("object", diagnostic.path("expected").asText());
            assertEquals("string", diagnostic.path("received").asText());
            assertEquals("NOT_STARTED", diagnostic.path("effects").asText());
            assertFalse(call.getResponse().getErrors().contains("private-value"));
            call.getResponse().execute();
            assertEquals(0, toolkit.invocations.get());
        }
    }

    @Test
    void objectVariablesKeepNestedExplicitNullsAndSharedMapperPolicy() throws Exception {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("explicit", null);
        variables.put("nested", Collections.singletonMap("explicit", null));
        JavaMethodToolCall call = method.createCall(message, "object", Map.of("document", "mutation { run }", "variables", variables));
        assertNotEquals(ToolExecutionStatus.FAILED, call.getResponse().getStatus());
        var json = SchemaProvider.OBJECT_MAPPER.valueToTree(call.getArgs());
        assertTrue(json.path("variables").has("explicit"));
        assertTrue(json.path("variables").path("explicit").isNull());
        assertTrue(json.path("variables").path("nested").has("explicit"));
        assertFalse(SchemaProvider.OBJECT_MAPPER.valueToTree(Collections.singletonMap("ordinary", null)).has("ordinary"));
        assertEquals(0, toolkit.invocations.get());
    }

    @Test
    void editedStringVariablesAreAlsoRejectedBeforeHandler() {
        JavaMethodToolCall call = method.createCall(message, "edited", Map.of("document", "mutation { run }", "variables", Map.of()));
        call.setModifiedArgument("variables", "{\"sql\":\"private-value\"}");
        call.getResponse().execute();
        assertEquals(ToolExecutionStatus.FAILED, call.getResponse().getStatus());
        assertTrue(call.getResponse().getErrors().contains("ARGUMENT_BINDING"));
        assertTrue(call.getResponse().getErrors().contains("NOT_STARTED"));
        assertFalse(call.getResponse().getErrors().contains("private-value"));
        assertEquals(0, toolkit.invocations.get());
    }

    /**
     * Synthetic command-enabled fixture; invocation count excludes all JDBC/IDE
     * APIs.
     */
    @AgiToolkit("GraphQL binding fixture")
    public static class BindingToolkit extends GraphQLToolkit {

        private final AtomicInteger invocations = new AtomicInteger();

        @Override
        protected boolean supportsMutations() {
            return true;
        }

        @Override
        protected String getSchemaSdl() {
            return "type Query { greeting: String } type Mutation { run: Boolean }";
        }

        @Override
        protected RuntimeWiring buildRuntimeWiring() {
            return RuntimeWiring.newRuntimeWiring().build();
        }

        @Override
        @AgiTool("Exercises binding before GraphQL execution")
        public GraphQLExecutionInfo mutate(
                @AgiToolParam("GraphQL document") String document,
                @AgiToolParam(value = "GraphQL variables object", required = false) Map<String, Object> variables) {
            invocations.incrementAndGet();
            return super.mutate(document, variables);
        }
    }

    private static class TestMessage extends AbstractModelMessage<TestResponse> {

        TestMessage(Agi agi) {
            super(agi, "binding-fixture");
        }
    }

    private static class TestResponse extends Response<TestMessage> {

        @Override
        public List<TestMessage> getCandidates() {
            return List.of();
        }

        @Override
        public ResponseUsageMetadata getUsageMetadata() {
            return null;
        }

        @Override
        public Optional<String> getPromptFeedback() {
            return Optional.empty();
        }

        @Override
        public int getTotalTokenCount() {
            return 0;
        }
    }
}
