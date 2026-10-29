/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.agi.tool.graphql;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies request-local reuse and limits independent of any IDE or JDBC
 * driver.
 *
 * @author anahata
 */
public class GraphQLRequestContextTest {

    /**
     * Reuses successful and null loads only inside one request.
     */
    @Test
    public void memoizesValuesAndNullsPerRequest() {
        GraphQLRequestContext context = new GraphQLRequestContext(10, 10_000);
        AtomicInteger loads = new AtomicInteger();
        assertEquals("value", context.getOrCompute("table", () -> {
            loads.incrementAndGet();
            return "value";
        }));
        assertEquals("value", context.getOrCompute("table", () -> "unexpected"));
        assertNull(context.getOrCompute("nullable", () -> {
            loads.incrementAndGet();
            return null;
        }));
        assertNull(context.getOrCompute("nullable", () -> "unexpected"));
        assertEquals(2, loads.get());
        GraphQLRequestContext next = new GraphQLRequestContext(10, 10_000);
        assertEquals("new request", next.getOrCompute("table", () -> "new request"));
    }

    /**
     * A failed PK load is not repeated for every selected column or alias.
     */
    @Test
    public void cachesFailuresAsWellAsValues() {
        GraphQLRequestContext context = new GraphQLRequestContext(10, 10_000);
        AtomicInteger loads = new AtomicInteger();
        SafeGraphQLException failure = new SafeGraphQLException("Metadata unavailable.");
        for (int attempt = 0; attempt < 2; attempt++) {
            SafeGraphQLException result = assertThrows(SafeGraphQLException.class, () -> context.getOrCompute("primaryKeys", () -> {
                loads.incrementAndGet();
                throw failure;
            }));
            assertSame(failure, result);
        }
        assertEquals(1, loads.get());
    }

    /**
     * Nested loaders with colliding hashes remain valid without map-bin
     * recursion.
     */
    @Test
    public void allowsNestedLoadsWithDistinctCollidingKeys() {
        GraphQLRequestContext context = new GraphQLRequestContext(10, 10_000);
        assertEquals("FB".hashCode(), "Ea".hashCode());
        assertEquals("nested", context.getOrCompute("FB", () -> context.getOrCompute("Ea", () -> "nested")));
        assertEquals("nested", context.getOrCompute("Ea", () -> "unexpected"));
    }

    /**
     * Exhausted time and operation budgets stop work with authored safe
     * messages.
     */
    @Test
    public void enforcesMetadataAndDeadlineBudgets() {
        GraphQLRequestContext context = new GraphQLRequestContext(1, 10_000);
        context.consumeMetadataOperation();
        assertThrows(SafeGraphQLException.class, context::consumeMetadataOperation);
        GraphQLRequestContext expired = new GraphQLRequestContext(1, Duration.ofNanos(1), null);
        assertThrows(SafeGraphQLException.class, expired::checkBudget);
    }

    /**
     * A null deadline remains distinct from positive subsecond and huge
     * durations.
     */
    @Test
    public void supportsOptionalTimeoutWithoutNanoConversionOverflow() {
        Object snapshot = Map.of("policy", "immutable");
        GraphQLRequestContext unlimited = new GraphQLRequestContext(10, null, snapshot);
        assertNull(unlimited.getRemainingTimeout());
        assertSame(snapshot, unlimited.getExecutionPolicy(Object.class));
        assertThrows(ClassCastException.class, () -> unlimited.getExecutionPolicy(String.class));
        GraphQLRequestContext subsecond = new GraphQLRequestContext(10, Duration.ofMillis(500), null);
        assertTrue(subsecond.getRemainingTimeout().compareTo(Duration.ZERO) > 0);
        assertTrue(subsecond.getRemainingTimeout().compareTo(Duration.ofSeconds(1)) < 0);
        GraphQLRequestContext huge = new GraphQLRequestContext(10, Duration.ofSeconds(Long.MAX_VALUE), null);
        assertTrue(huge.getRemainingTimeout().compareTo(Duration.ofDays(365)) > 0);
        assertThrows(IllegalArgumentException.class, () -> new GraphQLRequestContext(10, Duration.ZERO, null));
        assertThrows(IllegalArgumentException.class, () -> new GraphQLRequestContext(10, Duration.ofNanos(-1), null));
        assertThrows(IllegalArgumentException.class, () -> new GraphQLRequestContext(0, null, null));
    }

    /**
     * Explicit operation allowances tighten one request's original deadline and
     * cannot extend it or leak into another request.
     */
    @Test
    public void operationalTimeoutRefinesOneSharedDeadline() {
        GraphQLRequestContext request = new GraphQLRequestContext(10, null, null);
        request.limitTimeout(Duration.ofSeconds(20));
        Duration before = request.getRemainingTimeout();
        request.limitTimeout(Duration.ofSeconds(30));
        assertTrue(request.getRemainingTimeout().compareTo(before) <= 0);
        request.limitTimeout(Duration.ofMillis(500));
        assertTrue(request.getRemainingTimeout().compareTo(Duration.ofMillis(500)) <= 0);
        assertNull(new GraphQLRequestContext(10, null, null).getRemainingTimeout());
        assertThrows(IllegalArgumentException.class, () -> request.limitTimeout(Duration.ZERO));
        assertThrows(NullPointerException.class, () -> request.limitTimeout(null));
    }

    /**
     * No deadline does not disable the caller's cancellation flag.
     */
    @Test
    public void noAdditionalDeadlineStillChecksInterruption() {
        GraphQLRequestContext request = new GraphQLRequestContext(10, null, null);
        Thread.currentThread().interrupt();
        try {
            assertThrows(SafeGraphQLException.class, request::checkBudget);
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

}
