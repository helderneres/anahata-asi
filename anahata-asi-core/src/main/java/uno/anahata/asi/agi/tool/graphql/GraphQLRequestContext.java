/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.agi.tool.graphql;

import graphql.schema.DataFetchingEnvironment;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Execution-local memoization and cooperative resource budget. The context is
 * never retained by a toolkit or returned as response data. Failed loads are
 * cached too, preventing repeated JDBC work for aliases and dependent fields.
 * <p>
 * Deadline checks cannot interrupt a JDBC driver blocked inside a metadata
 * call. Resolvers must check before and after calls and while iterating their
 * rows.
 * </p>
 *
 * @author anahata
 */
public class GraphQLRequestContext {

    /**
     * Request-local completed values or failures, safely shared across threads.
     */
    private final Map<Object, CompletableFuture<Object>> cache = new ConcurrentHashMap<>();

    /**
     * Number of metadata calls started by this request.
     */
    private final AtomicInteger metadataOperations = new AtomicInteger();

    /**
     * Maximum permitted metadata calls across all selected fields.
     */
    private final int maxMetadataOperations;

    /**
     * Cooperative deadline measured with the monotonic system clock.
     */
    private final long deadlineNanos;

    /**
     * Creates the state for one execution using the toolkit's configured
     * budget.
     *
     * @param maxMetadataOperations Maximum actual metadata operations.
     * @param maxDurationMillis Cooperative time allowance in milliseconds.
     */
    public GraphQLRequestContext(int maxMetadataOperations, long maxDurationMillis) {
        this.maxMetadataOperations = maxMetadataOperations;
        this.deadlineNanos = System.nanoTime() + maxDurationMillis * 1_000_000L;
    }

    /**
     * Obtains the context installed by the inherited query tool.
     *
     * @param environment The native graphql-java resolver environment.
     * @return The execution-local context.
     */
    public static GraphQLRequestContext from(DataFetchingEnvironment environment) {
        return environment.getGraphQlContext().get(GraphQLRequestContext.class);
    }

    /**
     * Enforces the cooperative deadline and honors thread interruption.
     *
     * @throws SafeGraphQLException When the execution must stop doing new work.
     */
    public void checkBudget() {
        if (Thread.currentThread().isInterrupted() || System.nanoTime() - deadlineNanos >= 0) {
            throw new SafeGraphQLException("LimitExceeded", "GraphQL execution time limit reached; reduce the selected fields or page size.");
        }
    }

    /**
     * Reserves one metadata operation immediately before calling the driver.
     *
     * @throws SafeGraphQLException When time or metadata-call budget is
     * exhausted.
     */
    public void consumeMetadataOperation() {
        checkBudget();
        if (metadataOperations.incrementAndGet() > maxMetadataOperations) {
            throw new SafeGraphQLException("LimitExceeded", "GraphQL metadata operation limit reached; reduce the selected fields or page size.");
        }
    }

    /**
     * Loads a value at most once per request, including a failed or null load.
     * Keys must include all private identity and metadata arguments that affect
     * the value, particularly connection, catalog, schema and table.
     *
     * @param <T> The cached value type.
     * @param key Complete identity of the operation.
     * @param supplier Operation executed only for the first use of the key.
     * @return The shared value.
     */
    @SuppressWarnings("unchecked")
    public <T> T getOrCompute(Object key, Supplier<T> supplier) {
        checkBudget();
        CompletableFuture<Object> loaded = new CompletableFuture<>();
        CompletableFuture<Object> existing = cache.putIfAbsent(key, loaded);
        CompletableFuture<Object> result = existing == null ? loaded : existing;
        if (existing == null) {
            try {
                loaded.complete(supplier.get());
            } catch (RuntimeException failure) {
                loaded.completeExceptionally(failure);
            }
        }
        try {
            return (T) result.join();
        } catch (CompletionException failure) {
            if (failure.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            throw failure;
        }
    }
}
