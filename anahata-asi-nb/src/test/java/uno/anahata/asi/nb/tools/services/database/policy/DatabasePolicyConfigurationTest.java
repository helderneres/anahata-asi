/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.nb.tools.services.database.policy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import uno.anahata.asi.agi.tool.execution.MutableMutationExecutionPolicy;
import uno.anahata.asi.agi.tool.execution.MutableQueryExecutionPolicy;
import uno.anahata.asi.agi.tool.execution.MutableResultLoadingPolicy;
import uno.anahata.asi.agi.tool.execution.ResultLoadingPolicy;
import uno.anahata.asi.agi.tool.schema.SchemaProvider;
import uno.anahata.asi.persistence.kryo.KryoUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Policy contracts with no IDE, provider or JDBC interaction.
 */
class DatabasePolicyConfigurationTest {

    @Test
    void fluentContractsPreserveOwnTypeAndInstance() {
        DatabaseLoadingPolicy loading = new DatabaseLoadingPolicy();
        MutableResultLoadingPolicy<DatabaseLoadingPolicy> loadingManager = loading;
        assertSame(loading, loadingManager.putDefaultPageSize(30).putMaxPageSize(60));
        Integer defaultSize = loading.getDefaultPageSize();
        assertEquals(Integer.valueOf(30), defaultSize);
        DatabaseQueryPolicy query = new DatabaseQueryPolicy();
        MutableQueryExecutionPolicy<DatabaseQueryPolicy> queryManager = query;
        assertSame(query, queryManager.putTimeout(Duration.ofMillis(1)).putLoadingPolicy(loading));
        DatabaseMutationPolicy mutation = new DatabaseMutationPolicy();
        MutableMutationExecutionPolicy<DatabaseMutationPolicy> mutationManager = mutation;
        assertSame(mutation, mutationManager.putTimeout(null).putResultLoadingPolicy(loading));
        assertEquals("EXPLORATION", DatabaseExecutionMode.EXPLORATION.getName());
        assertEquals("EXTENDED_WORK", DatabaseExecutionMode.EXTENDED_WORK.getName());
    }

    @Test
    void profilesKeepDefaultsDistinctFromMaximums() {
        DatabasePolicyConfiguration configuration = new DatabasePolicyConfiguration();
        DatabaseExecutionPolicies exploration = configuration.resolve();
        assertLoading(exploration.getLoadingPolicy(false, "tables"), 25, 100);
        assertLoading(exploration.getLoadingPolicy(true, "executeQuery"), 100, 1_000);
        assertEquals(Duration.ofSeconds(10), exploration.getTimeout(false));
        assertEquals(Duration.ofSeconds(10), exploration.getTimeout(true));
        configuration.putMode(DatabaseExecutionMode.EXTENDED_WORK);
        DatabaseExecutionPolicies extended = configuration.resolve();
        assertLoading(extended.getLoadingPolicy(false, "tables"), 25, null);
        assertLoading(extended.getLoadingPolicy(true, "executeQuery"), 100, null);
        assertNull(extended.getTimeout(false));
        assertNull(extended.getTimeout(true));
        assertLoading(exploration.getQueryPolicy().getLoadingPolicy(), 25, 100);
    }

    @Test
    void nestedAndPublishedValuesAreIndependentOfLaterEdits() {
        DatabaseLoadingPolicy loading = new DatabaseLoadingPolicy().putDefaultPageSize(40).putMaxPageSize(80);
        DatabaseQueryPolicy query = new DatabaseQueryPolicy().putLoadingPolicy(loading).putTimeout(Duration.ofSeconds(2));
        DatabasePolicyConfiguration configuration = new DatabasePolicyConfiguration().putQueryPolicy(query);
        DatabaseExecutionPolicies effective = configuration.resolve();
        loading.putDefaultPageSize(70);
        query.putTimeout(null).putLoadingPolicy(new DatabaseLoadingPolicy().putMaxPageSize(null));
        configuration.putMode(DatabaseExecutionMode.EXTENDED_WORK);
        assertLoading(effective.getQueryPolicy().getLoadingPolicy(), 40, 80);
        assertEquals(Duration.ofSeconds(2), effective.getTimeout(false));
        assertFalse(effective.getQueryPolicy() instanceof MutableQueryExecutionPolicy<?>);
        assertFalse(effective.getQueryPolicy().getLoadingPolicy() instanceof MutableResultLoadingPolicy<?>);
        assertLoading(configuration.resolve().getQueryPolicy().getLoadingPolicy(), 40, 80);
        configuration.getQueryPolicy().putTimeout(null);
        assertEquals(Duration.ofSeconds(2), configuration.resolve().getTimeout(false));
    }

    @Test
    void layersReplaceSpecifiedValuesInOrderInsteadOfTakingTheMinimum() {
        DatabasePolicyConfiguration configuration = new DatabasePolicyConfiguration()
                .putQueryPolicy(new DatabaseQueryPolicy().putTimeout(Duration.ofSeconds(20))
                        .putLoadingPolicy(new DatabaseLoadingPolicy().putDefaultPageSize(50).putMaxPageSize(80)))
                .putResourceLoadingPolicy("tables", new DatabaseLoadingPolicy().putMaxPageSize(500));
        assertLoading(configuration.resolve().getLoadingPolicy(false, "tables"), 50, 500);
        assertLoading(configuration.resolve().getLoadingPolicy(false, "connections"), 50, 80);
        configuration.putSessionQueryPolicy(new DatabaseQueryPolicy().putTimeout(null)
                .putLoadingPolicy(new DatabaseLoadingPolicy().putMaxPageSize(null)), true);
        assertLoading(configuration.resolve().getLoadingPolicy(false, "tables"), 50, null);
        assertNull(configuration.resolve().getTimeout(false));
        assertEquals(Duration.ofSeconds(10), configuration.resolve().getTimeout(true));
        assertLoading(configuration.resolve().getLoadingPolicy(true, "tables"), 100, 500);
    }

    @Test
    void omittedAndExplicitNullHaveDifferentMeanings() {
        DatabasePolicyConfiguration inherited = new DatabasePolicyConfiguration()
                .putQueryPolicy(new DatabaseQueryPolicy().putLoadingPolicy(new DatabaseLoadingPolicy()));
        assertEquals(Integer.valueOf(100), inherited.resolve().getLoadingPolicy(false, null).getMaxPageSize());
        inherited.putQueryPolicy(new DatabaseQueryPolicy().putLoadingPolicy(new DatabaseLoadingPolicy().putMaxPageSize(null)));
        assertNull(inherited.resolve().getLoadingPolicy(false, null).getMaxPageSize());
        assertThrows(IllegalArgumentException.class, () -> new DatabaseLoadingPolicy().putDefaultPageSize(null));
        assertThrows(IllegalArgumentException.class, () -> new DatabaseLoadingPolicy().putDefaultPageSize(0));
        assertThrows(IllegalArgumentException.class, () -> new DatabaseLoadingPolicy().putMaxPageSize(-1));
    }

    @Test
    void validatesCompleteCompositionBeforePublishingAnExecution() {
        DatabasePolicyConfiguration invalid = new DatabasePolicyConfiguration()
                .putQueryPolicy(new DatabaseQueryPolicy().putLoadingPolicy(new DatabaseLoadingPolicy().putMaxPageSize(10)));
        assertThrows(IllegalArgumentException.class, invalid::resolve);
        DatabasePolicyConfiguration invalidResource = new DatabasePolicyConfiguration()
                .putResourceLoadingPolicy("tables", new DatabaseLoadingPolicy().putDefaultPageSize(500));
        assertThrows(IllegalArgumentException.class, invalidResource::resolve);
        ResultLoadingPolicy invalidOpenImplementation = new ResultLoadingPolicy() {
            @Override
            public Integer getDefaultPageSize() {
                return null;
            }

            @Override
            public Integer getMaxPageSize() {
                return null;
            }
        };
        invalid.putQueryPolicy(new DatabaseQueryPolicy().putLoadingPolicy(invalidOpenImplementation));
        assertThrows(IllegalArgumentException.class, invalid::resolve);
    }

    @Test
    void timeoutAcceptsNullAndPositiveSubsecondValues() {
        DatabaseQueryPolicy query = new DatabaseQueryPolicy().putTimeout(Duration.ofNanos(1));
        assertEquals(Duration.ofNanos(1), new DatabasePolicyConfiguration().putQueryPolicy(query).resolve().getTimeout(false));
        assertThrows(IllegalArgumentException.class, () -> query.putTimeout(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> query.putTimeout(Duration.ofNanos(-1)));
        assertNull(new DatabasePolicyConfiguration().putQueryPolicy(query.putTimeout(null)).resolve().getTimeout(false));
    }

    @Test
    void sessionOverridesRequireExplicitAuthorizationAndRemainIsolated() {
        DatabasePolicyConfiguration first = new DatabasePolicyConfiguration();
        DatabaseQueryPolicy override = new DatabaseQueryPolicy().putTimeout(null)
                .putLoadingPolicy(new DatabaseLoadingPolicy().putMaxPageSize(null));
        assertThrows(SecurityException.class, () -> first.putSessionQueryPolicy(override, false));
        assertThrows(SecurityException.class, () -> first.putSessionMutationPolicy(new DatabaseMutationPolicy(), false));
        assertLoading(first.resolve().getLoadingPolicy(false, null), 25, 100);
        first.putSessionQueryPolicy(override, true);
        assertNull(first.resolve().getLoadingPolicy(false, null).getMaxPageSize());
        assertLoading(new DatabasePolicyConfiguration().resolve().getLoadingPolicy(false, null), 25, 100);
        assertLoading(first.resolve().getLoadingPolicy(true, null), 100, 1_000);
    }

    @Test
    void jsonMapsPutMethodsAndRetainsPresenceWithoutChangingSharedMapper() throws Exception {
        ObjectMapper mapper = SchemaProvider.OBJECT_MAPPER.copy();
        String sharedBefore = SchemaProvider.OBJECT_MAPPER.writeValueAsString(java.util.Map.of("marker", "value"));
        DatabasePolicyConfiguration configuration = mapper.readValue("""
                {"mode":"EXPLORATION","queryPolicy":{"timeout":null,"loadingPolicy":{"maxPageSize":null}},
                 "mutationPolicy":{"resultLoadingPolicy":{"defaultPageSize":150,"maxPageSize":2000}},
                 "resourceLoadingPolicies":{"tables":{"defaultPageSize":35}}}
                """, DatabasePolicyConfiguration.class);
        assertLoading(configuration.resolve().getLoadingPolicy(false, "tables"), 35, null);
        assertLoading(configuration.resolve().getLoadingPolicy(true, "executeQuery"), 150, 2_000);
        assertNull(configuration.resolve().getTimeout(false));
        String json = mapper.writeValueAsString(configuration);
        assertTrue(mapper.readTree(json).get("queryPolicy").get("timeout").isNull(), json);
        assertTrue(mapper.readTree(json).get("queryPolicy").get("loadingPolicy").get("maxPageSize").isNull(), json);
        DatabasePolicyConfiguration roundTrip = mapper.readValue(json, DatabasePolicyConfiguration.class);
        assertNull(roundTrip.resolve().getLoadingPolicy(false, "tables").getMaxPageSize());
        assertEquals("{}", mapper.writeValueAsString(new DatabaseLoadingPolicy()));
        assertEquals(sharedBefore, SchemaProvider.OBJECT_MAPPER.writeValueAsString(java.util.Map.of("marker", "value")));
    }

    @Test
    void jsonRejectsNullDefaultAndCannotImportSessionAuthorization() {
        ObjectMapper mapper = SchemaProvider.OBJECT_MAPPER.copy();
        assertThrows(Exception.class, () -> mapper.readValue("{\"defaultPageSize\":null}", DatabaseLoadingPolicy.class));
        assertThrows(Exception.class, () -> mapper.readValue("{\"sessionQueryPolicy\":{\"timeout\":null}}", DatabasePolicyConfiguration.class));
    }

    @Test
    void javaSerializationPreservesConfigurationAndEffectiveNestedCopies() throws Exception {
        DatabasePolicyConfiguration original = new DatabasePolicyConfiguration()
                .putMode(DatabaseExecutionMode.EXTENDED_WORK)
                .putQueryPolicy(new DatabaseQueryPolicy().putTimeout(Duration.ofMillis(1200)))
                .putResourceLoadingPolicy("tables", new DatabaseLoadingPolicy().putDefaultPageSize(500));
        DatabasePolicyConfiguration restored = javaRoundTrip(original, DatabasePolicyConfiguration.class);
        DatabaseExecutionPolicies snapshot = javaRoundTrip(restored.resolve(), DatabaseExecutionPolicies.class);
        original.putMode(DatabaseExecutionMode.EXPLORATION);
        assertEquals(DatabaseExecutionMode.EXTENDED_WORK, restored.getMode());
        assertLoading(snapshot.getLoadingPolicy(false, "tables"), 500, null);
        assertEquals(Duration.ofMillis(1200), snapshot.getTimeout(false));
    }

    @Test
    void kryoPersistsAuthorizedSessionValuesAndKeepsCopiesIndependent() {
        DatabasePolicyConfiguration original = new DatabasePolicyConfiguration()
                .putSessionQueryPolicy(new DatabaseQueryPolicy().putTimeout(Duration.ofSeconds(90))
                        .putLoadingPolicy(new DatabaseLoadingPolicy().putMaxPageSize(null)), true);
        DatabasePolicyConfiguration restored = KryoUtils.clone(original);
        assertNull(restored.resolve().getLoadingPolicy(false, "tables").getMaxPageSize());
        assertEquals(Duration.ofSeconds(90), restored.resolve().getTimeout(false));
        restored.putSessionQueryPolicy(new DatabaseQueryPolicy(), true);
        assertNull(original.resolve().getLoadingPolicy(false, "tables").getMaxPageSize());
        assertEquals(Integer.valueOf(100), restored.resolve().getLoadingPolicy(false, "tables").getMaxPageSize());
    }

    @Test
    void resolutionReadsOnePublishedConfigurationGeneration() throws Exception {
        DatabasePolicyConfiguration configuration = new DatabasePolicyConfiguration();
        DatabaseQueryPolicy first = new DatabaseQueryPolicy().putLoadingPolicy(
                new DatabaseLoadingPolicy().putDefaultPageSize(25).putMaxPageSize(50));
        DatabaseQueryPolicy second = new DatabaseQueryPolicy().putLoadingPolicy(
                new DatabaseLoadingPolicy().putDefaultPageSize(50).putMaxPageSize(100));
        configuration.putQueryPolicy(first);
        AtomicBoolean stop = new AtomicBoolean();
        CompletableFuture<Void> writer = CompletableFuture.runAsync(() -> {
            while (!stop.get()) {
                configuration.putQueryPolicy(first);
                configuration.putQueryPolicy(second);
            }
        });
        try {
            for (int i = 0; i < 2_000; i++) {
                ResultLoadingPolicy loading = configuration.resolve().getLoadingPolicy(false, "tables");
                assertEquals(loading.getDefaultPageSize() * 2, loading.getMaxPageSize().intValue());
            }
        } finally {
            stop.set(true);
            writer.get();
        }
    }

    private static void assertLoading(ResultLoadingPolicy policy, Integer expectedDefault, Integer expectedMaximum) {
        assertEquals(expectedDefault, policy.getDefaultPageSize());
        assertEquals(expectedMaximum, policy.getMaxPageSize());
    }

    private static <T> T javaRoundTrip(T original, Class<T> type) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(original);
        }
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return type.cast(input.readObject());
        }
    }
}
