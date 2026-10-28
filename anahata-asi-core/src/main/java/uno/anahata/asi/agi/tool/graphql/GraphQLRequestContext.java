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
 * Holds memoized results and cooperative budgets for one GraphQL execution.
 * Each execution receives a new instance; a toolkit must not retain it across
 * requests or expose it as response data. Keys and cached values remain
 * private to resolvers and may include complete connection and table identity.
 *
 * <p>Concurrent callers share a future for each key. A winning supplier runs
 * synchronously on its caller's thread; other callers wait for that result.
 * Null results and runtime failures are cached for the request as well.</p>
 *
 * <p>Budget checks are cooperative. They cannot interrupt a blocking JDBC
 * call, supplier or future wait. Resolvers must check before and after source
 * calls and while consuming rows. In particular, waiting on another load does
 * not repeatedly check the deadline.</p>
 *
 * @author anahata
 */
public class GraphQLRequestContext {

    /**
     * Request-local futures for successful values, nulls or runtime failures.
     * Keys must have stable equality and hash codes for the lifetime of the request.
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
     * Creates budgets using the monotonic clock and zero consumed operations.
     * Arguments are configuration values and are stored without validation.
     * Callers must supply a positive operation ceiling and duration within the
     * range that can be converted safely to nanoseconds.
     *
     * @param maxMetadataOperations maximum actual metadata calls for the request
     * @param maxDurationMillis cooperative time allowance in milliseconds
     */
    public GraphQLRequestContext(int maxMetadataOperations, long maxDurationMillis) {
        this.maxMetadataOperations = maxMetadataOperations;
        this.deadlineNanos = System.nanoTime() + maxDurationMillis * 1_000_000L;
    }

    /**
     * Obtains the request context installed by the query or mutation engine.
     * The context lookup does not itself create or validate a context.
     *
     * @param environment non-null native resolver environment
     * @return installed request context, or null if the environment has none
     * @throws NullPointerException if environment is null
     */
    public static GraphQLRequestContext from(DataFetchingEnvironment environment) {
        return environment.getGraphQlContext().get(GraphQLRequestContext.class);
    }

    /**
     * Rejects further work once the deadline expires or the thread is interrupted.
     * This check preserves the thread's interrupt flag. It does not stop work
     * already blocked inside a driver or another component.
     *
     * @throws SafeGraphQLException with category LimitExceeded when the deadline
     *     has elapsed or the current thread has been interrupted
     */
    public void checkBudget() {
        if (Thread.currentThread().isInterrupted() || System.nanoTime() - deadlineNanos >= 0) {
            throw new SafeGraphQLException("LimitExceeded", "GraphQL execution time limit reached; reduce the selected fields or page size.");
        }
    }

    /**
     * Reserves an actual metadata call after checking the cooperative deadline.
     * The counter is incremented atomically. Calls that exceed the ceiling are
     * rejected and remain counted; a failed reservation is not rolled back.
     * Invoke this immediately before new driver work, not for a cache hit.
     *
     * @throws SafeGraphQLException when the time or metadata-call budget is exhausted
     */
    public void consumeMetadataOperation() {
        checkBudget();
        if (metadataOperations.incrementAndGet() > maxMetadataOperations) {
            throw new SafeGraphQLException("LimitExceeded", "GraphQL metadata operation limit reached; reduce the selected fields or page size.");
        }
    }

    /**
     * Returns the result of one successful or failed load for a request key.
     * The first caller installs a future and runs its supplier synchronously.
     * Other callers share that future, including a null result or a cached
     * {@link RuntimeException}, which is rethrown to each caller. Waiting uses
     * {@link CompletableFuture#join()} without a timeout; the deadline is checked
     * before lookup, not continuously during the load or wait.
     *
     * <p>The supplier must not recursively load the same key: it would wait on
     * its own unfinished future. It should also avoid cyclic dependencies between
     * keys and must arrange any needed resource cleanup itself. Only runtime
     * exceptions are cached by this implementation, so suppliers must not rely
     * on an {@link Error} completing the shared future.</p>
     *
     * @param <T> result type shared by all users of this key
     * @param key non-null, stable identity including every source argument affecting
     *     the result
     * @param supplier loader used only by the first caller for the key
     * @return cached result, including null
     * @throws SafeGraphQLException if the cooperative budget rejects the initial lookup
     * @throws NullPointerException if key is null, or the winning caller's supplier is
     *     null
     * @throws RuntimeException if the winning supplier fails; that failure is cached
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
