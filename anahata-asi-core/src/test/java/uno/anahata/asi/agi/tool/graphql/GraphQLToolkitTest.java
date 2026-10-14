/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.agi.tool.graphql;

import java.beans.PropertyChangeSupport;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import graphql.schema.idl.RuntimeWiring;
import uno.anahata.ai.tool.MockAsiContainer;
import uno.anahata.asi.agi.Agi;
import uno.anahata.asi.agi.AgiConfig;
import uno.anahata.asi.agi.tool.AgiToolkit;
import uno.anahata.asi.agi.tool.ToolPermission;
import uno.anahata.asi.agi.tool.spi.java.JavaObjectToolkit;

/**
 * Exercises the real embedded engine and Java toolkit registration rather than
 * testing a projection mock. Rejected queries must perform no resolver work.
 *
 * @author anahata
 */
public class GraphQLToolkitTest {

    /**
     * Verifies inherited schema/query annotations through actual tool
     * registration.
     *
     * @throws Exception When framework registration fails.
     */
    @Test
    public void inheritedToolsUseTheExistingRegistrationAndPermission() throws Exception {
        MockAsiContainer container = new MockAsiContainer("graphql-test");
        AgiConfig config = new AgiConfig(container, "graphql-test-session");
        config.getToolClasses().add(TestToolkit.class);
        Agi agi = new Agi(config);
        JavaObjectToolkit wrapper = new JavaObjectToolkit(agi.getToolManager(), TestToolkit.class);
        assertEquals(List.of("TestToolkit.query", "TestToolkit.schema"), wrapper.getAllTools().stream()
                .map(tool -> tool.getName()).sorted().toList());
        wrapper.getAllTools().forEach(tool -> assertEquals(ToolPermission.PROMPT, tool.getPermission()));
        wrapper.initialize();
        TestToolkit toolkit = (TestToolkit) wrapper.getToolkitInstance();
        assertTrue(toolkit.schema().contains("type Query"));
        assertEquals("hello", toolkit.query("{ message }", null).getData().get("message"));
    }

    /**
     * Simulates transient loss and verifies parent and engine lifecycle
     * recovery.
     *
     * @throws Exception When reflection or engine reconstruction fails.
     */
    @Test
    public void initializeAndRebindRebuildTheTransientEngine() throws Exception {
        TestToolkit toolkit = toolkit();
        assertEquals(1, toolkit.query("{ revision }", Map.of()).getData().get("revision"));
        toolkit.revision = 2;
        assertEquals(1, toolkit.query("{ revision }", Map.of()).getData().get("revision"));
        Field engine = GraphQLToolkit.class.getDeclaredField("graphQL");
        assertTrue(Modifier.isTransient(engine.getModifiers()));
        engine.setAccessible(true);
        engine.set(toolkit, null);
        PropertyChangeSupport oldSupport = toolkit.getPropertyChangeSupport();
        toolkit.rebind();
        assertNotSame(oldSupport, toolkit.getPropertyChangeSupport());
        assertEquals(2, toolkit.query("{ revision }", Map.of()).getData().get("revision"));
        assertNotNull(toolkit.schema());
    }

    /**
     * Allows abbreviated query syntax, variables and fragment-based selection.
     */
    @Test
    public void executesShorthandVariablesAndFragments() {
        TestToolkit toolkit = toolkit();
        assertEquals("hello", toolkit.query("{ message }", null).getData().get("message"));
        GraphQLExecutionInfo variables = toolkit.query("query($text:String) { message(text:$text) }", Map.of("text", "selected"));
        assertTrue(variables.getErrors().isEmpty());
        assertEquals("selected", variables.getData().get("message"));
        GraphQLExecutionInfo fragment = toolkit.query("{ ...Fields } fragment Fields on Query { message(text:\"mutation\") }", Map.of());
        assertTrue(fragment.getErrors().isEmpty());
        assertEquals("mutation", fragment.getData().get("message"));
    }

    /**
     * Rejects all non-query definitions and ambiguous operations before
     * resolvers.
     */
    @Test
    public void rejectsMutationsSubscriptionsAndMultipleOperations() {
        TestToolkit toolkit = toolkit();
        List<String> documents = List.of(
                "mutation { message }",
                "subscription { message }",
                "query Good { message } mutation Bad { message }",
                "query A { message } query B { revision }",
                "{ message } query Another { revision }",
                "type Query { message: String }");
        for (String document : documents) {
            GraphQLExecutionInfo result = toolkit.query(document, Map.of());
            assertNull(result.getData(), document);
            assertFalse(result.getErrors().isEmpty(), document);
        }
        assertEquals(0, toolkit.fetches.get());
    }

    /**
     * Rejects malformed input and unknown schema fields without resolver work.
     */
    @Test
    public void rejectsSyntaxSchemaAndDocumentSizeViolations() {
        TestToolkit toolkit = toolkit();
        for (String document : List.of("", "{", "{ unknown }", " ".repeat(20_001))) {
            assertFalse(toolkit.query(document, Map.of()).getErrors().isEmpty());
        }
        assertFalse(toolkit.query(null, Map.of()).getErrors().isEmpty());
        assertEquals(0, toolkit.fetches.get());
    }

    /**
     * Demonstrates pre-execution depth enforcement using native
     * instrumentation.
     */
    @Test
    public void rejectsExcessiveDepthBeforeFetching() {
        TestToolkit toolkit = new TestToolkit();
        toolkit.maxDepth = 2;
        toolkit.initialize();
        GraphQLExecutionInfo result = toolkit.query("{ nodes { children { label } } }", Map.of());
        assertFalse(result.getErrors().isEmpty());
        assertEquals(0, toolkit.fetches.get());
    }

    /**
     * Applies coerced variable limits to complexity before any data is fetched.
     */
    @Test
    public void estimatesPagedFanOutFromVariables() {
        TestToolkit toolkit = new TestToolkit();
        toolkit.maxComplexity = 20;
        toolkit.initialize();
        GraphQLExecutionInfo allowed = toolkit.query("query($n:Int) { expensive(limit:$n) { label } }", Map.of("n", 2));
        assertTrue(allowed.getErrors().isEmpty());
        assertEquals(1, toolkit.fetches.get());
        GraphQLExecutionInfo rejected = toolkit.query("query($n:Int) { expensive(limit:$n) { label } }", Map.of("n", 100));
        assertFalse(rejected.getErrors().isEmpty());
        assertEquals(1, toolkit.fetches.get());
    }

    /**
     * Counts independent aliases instead of allowing a shallow wide query
     * bypass.
     */
    @Test
    public void rejectsExcessiveAliasedFieldsBeforeFetching() {
        TestToolkit toolkit = new TestToolkit();
        toolkit.maxComplexity = 5;
        toolkit.initialize();
        GraphQLExecutionInfo result = toolkit.query("{ a:message b:message c:message d:message e:message f:message }", Map.of());
        assertFalse(result.getErrors().isEmpty());
        assertEquals(0, toolkit.fetches.get());
    }

    /**
     * Multiple huge nested fan-outs cannot overflow the native integer cost
     * sum.
     */
    @Test
    public void rejectsCombinedNestedFanOutWithoutIntegerOverflow() {
        TestToolkit toolkit = toolkit();
        String subtree = "nodes { children { children { children { children { children { label } } } } } }";
        GraphQLExecutionInfo result = toolkit.query("{ a:" + subtree + " b:" + subtree + " }", Map.of());
        assertFalse(result.getErrors().isEmpty());
        assertEquals(0, toolkit.fetches.get());
    }

    /**
     * Retains successful siblings while removing sensitive exception content.
     */
    @Test
    public void partialResponseSanitizesDriverFailuresAndPreservesNullableFields() {
        TestToolkit toolkit = toolkit();
        GraphQLExecutionInfo result = toolkit.query("{ message exploded nullable }", Map.of());
        assertEquals("hello", result.getData().get("message"));
        assertTrue(result.getData().containsKey("exploded"));
        assertNull(result.getData().get("exploded"));
        assertNull(result.getData().get("nullable"));
        assertEquals(1, result.getErrors().size());
        GraphQLErrorInfo error = result.getErrors().get(0);
        assertEquals(List.of("exploded"), error.getPath());
        assertFalse(error.getMessage().contains("jdbc:"));
        assertFalse(error.getMessage().contains("password"));
        assertFalse(error.getMessage().contains("IllegalStateException"));
    }

    /**
     * Safe domain failures remain actionable and never return exception
     * objects.
     */
    @Test
    public void preservesExplicitlySafeDomainErrors() {
        GraphQLExecutionInfo result = toolkit().query("{ safeFailure }", Map.of());
        assertEquals("Connect the source in the IDE and retry.", result.getErrors().get(0).getMessage());
    }

    /**
     * Demonstrates that returned data/errors are standard serializable Java
     * values.
     *
     * @throws Exception When serialization encounters an internal model.
     */
    @Test
    public void responseIsSerializableWithoutEngineTypes() throws Exception {
        GraphQLExecutionInfo result = toolkit().query("{ message exploded }", Map.of());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(result);
        }
        assertTrue(bytes.size() > 0);
    }

    /** Existing GraphQL subclasses do not opt into Database command behavior. */
    @Test
    public void readOnlyToolkitRemainsQueryOnlyAfterRebind() throws Exception {
        MockAsiContainer container = new MockAsiContainer("graphql-opt-in-test");
        Agi agi = new Agi(new AgiConfig(container, "graphql-opt-in-session"));
        JavaObjectToolkit wrapper = new JavaObjectToolkit(agi.getToolManager(), TestToolkit.class);
        wrapper.initialize();
        wrapper.rebind();
        wrapper.postActivate();
        TestToolkit readOnly = (TestToolkit) wrapper.getToolkitInstance();
        assertFalse(readOnly.isCommandToolkit());
        assertEquals(List.of("TestToolkit.query", "TestToolkit.schema"), wrapper.getAllTools().stream().map(tool -> tool.getName()).sorted().toList());
        GraphQLExecutionInfo rejected = readOnly.mutate("mutation { message }", Map.of());
        assertEquals("MutationUnsupported", rejected.getErrors().getFirst().getCategory());
        assertEquals("hello", readOnly.query("{ message }", Map.of()).getData().get("message"));
        assertTrue(readOnly.query("{ __typename }", Map.of()).getErrors().isEmpty());
    }

    /** Selected nulls survive only the GraphQL response's local serializer. */
    @Test
    public void graphQLNullSerializationDoesNotChangeOrdinaryMapperPolicy() throws Exception {
        var mapper = uno.anahata.asi.agi.tool.schema.SchemaProvider.OBJECT_MAPPER;
        GraphQLExecutionInfo response = toolkit().query("{ nullable exploded }", Map.of());
        var json = mapper.readTree(mapper.writeValueAsString(response));
        assertTrue(json.get("data").has("nullable"));
        assertTrue(json.get("data").get("nullable").isNull());
        assertTrue(json.get("data").get("exploded").isNull());
        assertEquals(1, json.get("errors").size());
        assertFalse(response.toString().contains("@"));
        assertFalse(mapper.readTree(mapper.writeValueAsString(java.util.Collections.singletonMap("ordinary", null))).has("ordinary"));
    }

    /** Only a synthetic variable map is cloned; no session/toolkit is restored. */
    @Test
    public void nullVariablesSurviveTheExistingKryoMapSerializer() throws Exception {
        Map<String, Object> values = new java.util.LinkedHashMap<>();
        values.put("n", null);
        values.put("nested", java.util.Collections.singletonMap("inner", null));
        GraphQLVariables original = new GraphQLVariables(values);
        GraphQLVariables restored = uno.anahata.asi.persistence.kryo.KryoUtils.clone(original);
        assertEquals(original, restored);
        assertTrue(restored.containsKey("n"));
        restored.put("afterRestore", 21);
        var mapper = uno.anahata.asi.agi.tool.schema.SchemaProvider.OBJECT_MAPPER;
        var json = mapper.readTree(mapper.writeValueAsString(restored));
        assertTrue(json.get("n").isNull());
        assertTrue(json.get("nested").get("inner").isNull());
        assertEquals(21, json.get("afterRestore").intValue());
    }

    /**
     * Creates a freshly initialized isolated engine without an AGI session.
     *
     * @return The test toolkit.
     */
    private static TestToolkit toolkit() {
        TestToolkit toolkit = new TestToolkit();
        toolkit.initialize();
        return toolkit;
    }

    /**
     * A small concrete toolkit exercising native wiring and public inheritance.
     */
    @AgiToolkit("GraphQL infrastructure test toolkit")
    public static class TestToolkit extends GraphQLToolkit {

        /**
         * Counts operational fetches to prove validation happens first.
         */
        private final AtomicInteger fetches = new AtomicInteger();

        /**
         * Value captured in each rebuilt engine's wiring.
         */
        private int revision = 1;

        /**
         * Override for tests proving depth enforcement.
         */
        private int maxDepth = 12;

        /**
         * Override for tests proving variable-aware complexity enforcement.
         */
        private int maxComplexity = 10_000;

        /**
         * {@inheritDoc}
         * <p>
         * Allows isolated lifecycle tests to run before wrapper attachment.</p>
         */
        @Override
        public String getName() {
            return "TestToolkit";
        }

        /**
         * {@inheritDoc}
         * <p>
         * Uses nullable failure fields to exercise partial GraphQL
         * responses.</p>
         */
        @Override
        protected String getSchemaSdl() {
            return "type Query { message(text:String):String revision:Int exploded:String safeFailure:String nullable:Boolean "
                    + "nodes:[Node] expensive(limit:Int=25):[Node] } type Node { label:String children:[Node] }";
        }

        /**
         * {@inheritDoc}
         * <p>
         * Counts only root operational fetches; node attributes use
         * getters.</p>
         */
        @Override
        protected RuntimeWiring buildRuntimeWiring() {
            int boundRevision = revision;
            return RuntimeWiring.newRuntimeWiring().type("Query", type -> type
                    .dataFetcher("message", environment -> {
                        fetches.incrementAndGet();
                        String text = environment.getArgument("text");
                        return text == null ? "hello" : text;
                    })
                    .dataFetcher("revision", environment -> boundRevision)
                    .dataFetcher("exploded", environment -> {
                        throw new IllegalStateException("jdbc:secret user=admin password=hidden");
                    })
                    .dataFetcher("safeFailure", environment -> {
                        throw new SafeGraphQLException("Connect the source in the IDE and retry.");
                    })
                    .dataFetcher("nullable", environment -> null)
                    .dataFetcher("nodes", environment -> {
                        fetches.incrementAndGet();
                        return List.of(Map.of("label", "root", "children", List.of(Map.of("label", "child"))));
                    })
                    .dataFetcher("expensive", environment -> {
                        fetches.incrementAndGet();
                        return List.of(Map.of("label", "selected"));
                    })).build();
        }

        /**
         * {@inheritDoc}
         * <p>
         * Returns the depth selected by the individual test.</p>
         */
        @Override
        protected int getMaxQueryDepth() {
            return maxDepth;
        }

        /**
         * {@inheritDoc}
         * <p>
         * Returns the complexity selected by the individual test.</p>
         */
        @Override
        protected int getMaxQueryComplexity() {
            return maxComplexity;
        }
    }
}
