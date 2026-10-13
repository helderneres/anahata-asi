/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.agi.tool.graphql;

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
        GraphQLRequestContext expired = new GraphQLRequestContext(1, 0);
        assertThrows(SafeGraphQLException.class, expired::checkBudget);
    }
}
