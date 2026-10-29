/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.agi.tool.graphql;

import graphql.schema.DataFetchingEnvironment;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Holds memoized results, an execution-policy snapshot and cooperative budgets
 * for one GraphQL execution. A toolkit must not retain this context across
 * requests or expose it as response data. Keys and cached values remain private
 * to resolvers and may include complete connection and table identity.
 *
 * <p>
 * Concurrent callers share a future for each key. A winning supplier runs
 * synchronously on its caller's thread; other callers wait for that result.
 * Null results and runtime failures are cached for the request as well.</p>
 *
 * <p>
 * Budget checks cannot interrupt a blocking JDBC call, supplier or future wait.
 * Resolvers must check before and after source calls and while consuming rows.
 * Waiting on another load does not repeatedly check the deadline. A null
 * timeout removes only the additional request deadline, not interruption checks
 * or the actual metadata-operation budget.</p>
 *
 * @author anahata
 */
public class GraphQLRequestContext {

    /**
     * Request-local futures for successful values, nulls or runtime failures.
     */
    private final Map<Object, CompletableFuture<Object>> cache = new ConcurrentHashMap<>();

    /**
     * Number of actual metadata operations reserved by this request.
     */
    private final AtomicInteger metadataOperations = new AtomicInteger();

    /**
     * Maximum permitted metadata operations across all selected fields.
     */
    private final int maxMetadataOperations;

    /**
     * Monotonic origin used for elapsed-time subtraction across clock wrap.
     */
    private final long startedNanos = System.nanoTime();

    /**
     * Optional additional timeout; Duration avoids overflowing nano conversion.
     */
    private volatile Duration timeout;

    /**
     * Domain-owned immutable snapshot, never exposed as response data.
     */
    private final Object executionPolicy;

    /**
     * Creates a request with a positive millisecond deadline and no policy.
     *
     * @param maxMetadataOperations positive maximum actual metadata operations
     * @param maxDurationMillis positive cooperative time allowance in
     * milliseconds
     * @throws IllegalArgumentException if either limit is not positive
     */
    public GraphQLRequestContext(int maxMetadataOperations, long maxDurationMillis) {
        this(maxMetadataOperations, Duration.ofMillis(maxDurationMillis), null);
    }

    /**
     * Captures an immutable domain snapshot and optional request deadline. The
     * caller must supply a deeply immutable policy; this generic context cannot
     * copy or validate a toolkit-specific policy implementation. All selected
     * fields share this deadline rather than starting new timers.
     *
     * @param maxMetadataOperations positive maximum actual metadata operations
     * @param timeout positive duration, or null for no additional timeout
     * @param executionPolicy immutable domain policy snapshot, or null
     * @throws IllegalArgumentException if the operation ceiling or timeout is
     * not positive
     */
    public GraphQLRequestContext(int maxMetadataOperations, Duration timeout, Object executionPolicy) {
        if (maxMetadataOperations <= 0 || (timeout != null && (timeout.isZero() || timeout.isNegative()))) {
            throw new IllegalArgumentException("Positive metadata limit and positive timeout or null required.");
        }
        this.maxMetadataOperations = maxMetadataOperations;
        this.timeout = timeout;
        this.executionPolicy = executionPolicy;
    }

    /**
     * Obtains the request context installed by the query or mutation engine.
     *
     * @param environment non-null native resolver environment
     * @return installed context, or null if the environment has none
     * @throws NullPointerException if environment is null
     */
    public static GraphQLRequestContext from(DataFetchingEnvironment environment) {
        return environment.getGraphQlContext().get(GraphQLRequestContext.class);
    }

    /**
     * Retrieves the domain-owned immutable snapshot with an explicit type
     * check.
     *
     * @param <T> domain policy snapshot type
     * @param type expected non-null type
     * @return captured snapshot, or null when no policy was attached
     * @throws NullPointerException if type is null
     * @throws ClassCastException if the snapshot is not of the requested type
     */
    public <T> T getExecutionPolicy(Class<T> type) {
        return Objects.requireNonNull(type, "type").cast(executionPolicy);
    }

    /**
     * Checks interruption and deadline, then returns the remaining allowance.
     * Positive subsecond durations stay positive; JDBC adapters must round up
     * rather than converting them to zero, which JDBC interprets as unlimited.
     * Elapsed-time measurement has the monotonic clock's approximately 292-year
     * subtraction range; no duration-to-nanoseconds conversion occurs.
     *
     * @return positive remaining duration, or null for no additional timeout
     * @throws SafeGraphQLException if interrupted or the deadline has elapsed
     */
    public Duration getRemainingTimeout() {
        Duration effective = timeout;
        long elapsed = elapsedNanos();
        if (Thread.currentThread().isInterrupted()
                || (effective != null && effective.compareTo(Duration.ofNanos(elapsed)) <= 0)) {
            throw new SafeGraphQLException("LimitExceeded", "GraphQL execution time limit reached; reduce the selected fields or page size.");
        }
        return effective == null ? null : effective.minusNanos(elapsed);
    }

    /**
     * Tightens the shared request deadline using an explicit operation
     * allowance. This is a preflight operation: the duration is measured from
     * the original request start, never from this method call, and cannot
     * extend an existing deadline. Multiple explicit operation allowances
     * govern one document by their shortest value. This does not compose
     * configuration layers or modify the captured policy snapshot. Domain code
     * must call it before effects.
     *
     * @param allowance positive duration measured from the request start
     * @throws NullPointerException if allowance is null
     * @throws IllegalArgumentException if allowance is not positive
     * @throws SafeGraphQLException if the refined deadline has already elapsed
     */
    public synchronized void limitTimeout(Duration allowance) {
        Objects.requireNonNull(allowance, "allowance");
        if (allowance.isZero() || allowance.isNegative()) {
            throw new IllegalArgumentException("Positive timeout required.");
        }
        if (timeout == null || allowance.compareTo(timeout) < 0) {
            timeout = allowance;
        }
        checkBudget();
    }

    /**
     * Rejects further work once the deadline expires or the thread is
     * interrupted. Preserves the interrupt flag and does not preempt blocked
     * driver work.
     *
     * @throws SafeGraphQLException with category LimitExceeded when interrupted
     * or the deadline has elapsed
     */
    public void checkBudget() {
        getRemainingTimeout();
    }

    /**
     * Measures elapsed monotonic time without adding a potentially huge
     * timeout.
     *
     * @return nonnegative elapsed nanoseconds within the monotonic clock range
     */
    private long elapsedNanos() {
        long elapsed = System.nanoTime() - startedNanos;
        return elapsed < 0 ? Long.MAX_VALUE : elapsed;
    }

    /**
     * Reserves an actual metadata operation after checking the deadline.
     * Rejected reservations remain counted. Invoke immediately before new
     * driver work, not for a cached result.
     *
     * @throws SafeGraphQLException when time or metadata-operation budget is
     * exhausted
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
     * Other callers share that future, including null or a cached runtime
     * failure. Waiting uses {@link CompletableFuture#join()} without a timeout;
     * the deadline is checked before lookup, not continuously during the wait.
     *
     * <p>
     * The supplier must not recursively load the same key or introduce cyclic
     * key dependencies. It must arrange resource cleanup. Only runtime failures
     * are cached; an {@link Error} does not complete the shared future.</p>
     *
     * @param <T> result type shared by all users of this key
     * @param key non-null stable identity including every argument affecting
     * the result
     * @param supplier loader used only by the first caller for the key
     * @return cached result, including null
     * @throws SafeGraphQLException if the initial budget check fails
     * @throws NullPointerException if key or the winning caller's supplier is
     * null
     * @throws RuntimeException if the winning supplier fails; the failure is
     * cached
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
