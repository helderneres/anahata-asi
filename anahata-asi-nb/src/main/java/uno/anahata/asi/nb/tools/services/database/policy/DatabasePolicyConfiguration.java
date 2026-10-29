/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.nb.tools.services.database.policy;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import uno.anahata.asi.agi.tool.execution.ResultLoadingPolicy;

/**
 * Small copy-on-write configuration facade. Each put copies supplied partial
 * values and publishes a complete immutable state. Resolve captures that state
 * once, composes common defaults, mode, operation/resource and authorized
 * session layers, then validates every effective loading policy before domain
 * effects.
 *
 * <p>
 * Later layers replace specified properties rather than selecting their
 * minimum. Omission inherits; explicit null removes maximum or timeout.
 * Defaults remain mandatory. Editing caller-owned configurations after a put
 * cannot change this facade. There is no model-visible configuration mutation
 * or authorization inferred from a selected mode.</p>
 *
 * <p>
 * JSON uses explicit mappings for put methods. Session adjustments require a
 * trusted Java caller and are intentionally excluded from JSON import/export;
 * Java/Kryo session persistence retains the already-authorized values. No
 * mapper or application permission is changed by this class.</p>
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.NONE,
        getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public final class DatabasePolicyConfiguration implements Serializable {

    private static final long serialVersionUID = 1L;
    private volatile State state = new State(DatabaseExecutionMode.EXPLORATION,
            PolicyValues.empty(), PolicyValues.empty(), Map.of(), PolicyValues.empty(), PolicyValues.empty());

    /**
     * @return current stable mode
     */
    public DatabaseExecutionMode getMode() {
        return state.mode;
    }

    /**
     * Selects a profile without changing execution permission or database
     * target.
     *
     * @param mode non-null profile
     * @return this configuration
     */
    @JsonProperty("mode")
    public synchronized DatabasePolicyConfiguration putMode(DatabaseExecutionMode mode) {
        State current = state;
        state = new State(Objects.requireNonNull(mode, "mode"), current.query, current.mutation,
                current.resources, current.sessionQuery, current.sessionMutation);
        return this;
    }

    /**
     * @return detached query configuration; edit and publish it with
     * putQueryPolicy
     */
    public DatabaseQueryPolicy getQueryPolicy() {
        return DatabaseQueryPolicy.from(state.query);
    }

    /**
     * Publishes copied operation-level query overrides.
     *
     * @param policy non-null partial policy
     * @return this configuration
     */
    @JsonProperty("queryPolicy")
    public synchronized DatabasePolicyConfiguration putQueryPolicy(DatabaseQueryPolicy policy) {
        State current = state;
        state = new State(current.mode, Objects.requireNonNull(policy, "policy").snapshot(), current.mutation,
                current.resources, current.sessionQuery, current.sessionMutation);
        return this;
    }

    /**
     * @return detached command configuration; edits require putMutationPolicy
     */
    public DatabaseMutationPolicy getMutationPolicy() {
        return DatabaseMutationPolicy.from(state.mutation);
    }

    /**
     * Publishes copied command-level overrides, without granting command
     * permission.
     *
     * @param policy non-null partial policy
     * @return this configuration
     */
    @JsonProperty("mutationPolicy")
    public synchronized DatabasePolicyConfiguration putMutationPolicy(DatabaseMutationPolicy policy) {
        State current = state;
        state = new State(current.mode, current.query, Objects.requireNonNull(policy, "policy").snapshot(),
                current.resources, current.sessionQuery, current.sessionMutation);
        return this;
    }

    /**
     * Replaces one resource's loading overrides for either operation channel.
     *
     * @param resource non-blank stable resolver resource name
     * @param loadingPolicy non-null partial loading configuration
     * @return this configuration
     */
    public synchronized DatabasePolicyConfiguration putResourceLoadingPolicy(String resource, DatabaseLoadingPolicy loadingPolicy) {
        if (resource == null || resource.isBlank()) {
            throw new IllegalArgumentException("resource must not be blank");
        }
        State current = state;
        Map<String, PolicyValues.Loading> resources = new LinkedHashMap<>(current.resources);
        resources.put(resource, Objects.requireNonNull(loadingPolicy, "loadingPolicy").snapshot());
        state = new State(current.mode, current.query, current.mutation,
                resources, current.sessionQuery, current.sessionMutation);
        return this;
    }

    /**
     * Publishes session query adjustments at an explicit trusted Java boundary.
     * The flag represents authorization already decided by the caller; it is
     * not a substitute for the toolkit's ordinary command permission checks.
     *
     * @param policy non-null partial adjustments
     * @param authorized true only after caller authorization of configuration
     * edits
     * @return this configuration
     * @throws SecurityException if configuration adjustment was not authorized
     */
    public synchronized DatabasePolicyConfiguration putSessionQueryPolicy(DatabaseQueryPolicy policy, boolean authorized) {
        requireAuthorized(authorized);
        State current = state;
        state = new State(current.mode, current.query, current.mutation, current.resources,
                Objects.requireNonNull(policy, "policy").snapshot(), current.sessionMutation);
        return this;
    }

    /**
     * Publishes authorized session command-policy adjustments, not command
     * permission.
     *
     * @param policy non-null partial adjustments
     * @param authorized true only after caller authorization of configuration
     * edits
     * @return this configuration
     * @throws SecurityException if configuration adjustment was not authorized
     */
    public synchronized DatabasePolicyConfiguration putSessionMutationPolicy(DatabaseMutationPolicy policy, boolean authorized) {
        requireAuthorized(authorized);
        State current = state;
        state = new State(current.mode, current.query, current.mutation, current.resources,
                current.sessionQuery, Objects.requireNonNull(policy, "policy").snapshot());
        return this;
    }

    /**
     * Captures and validates one deeply immutable policy set before execution.
     * All resource specializations are checked, including currently unselected
     * ones.
     *
     * @return independent effective values; no editable reference is retained
     * @throws IllegalArgumentException if composed defaults/maximums conflict
     */
    public DatabaseExecutionPolicies resolve() {
        State captured = state;
        PolicyValues query = resolve(captured, false, PolicyValues.Loading.empty());
        PolicyValues mutation = resolve(captured, true, PolicyValues.Loading.empty());
        Map<String, ResultLoadingPolicy> queryResources = new LinkedHashMap<>();
        Map<String, ResultLoadingPolicy> mutationResources = new LinkedHashMap<>();
        captured.resources.forEach((name, resource) -> {
            queryResources.put(name, resolve(captured, false, resource).loading);
            mutationResources.put(name, resolve(captured, true, resource).loading);
        });
        return new DatabaseExecutionPolicies(captured.mode, query, mutation, queryResources, mutationResources);
    }

    /**
     * @return detached JSON configuration without session-only adjustments
     */
    @JsonAnyGetter
    public Map<String, Object> configurationProperties() {
        State captured = state;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", captured.mode);
        result.put("queryPolicy", DatabaseQueryPolicy.from(captured.query));
        result.put("mutationPolicy", DatabaseMutationPolicy.from(captured.mutation));
        Map<String, DatabaseLoadingPolicy> resources = new LinkedHashMap<>();
        captured.resources.forEach((name, loading) -> resources.put(name, DatabaseLoadingPolicy.from(loading)));
        result.put("resourceLoadingPolicies", resources);
        return result;
    }

    /**
     * Publishes the complete resource map coherently during JSON configuration.
     */
    @JsonProperty("resourceLoadingPolicies")
    private synchronized void readResourceLoadingPolicies(Map<String, DatabaseLoadingPolicy> resources) {
        Objects.requireNonNull(resources, "resourceLoadingPolicies");
        Map<String, PolicyValues.Loading> copied = new LinkedHashMap<>();
        resources.forEach((name, policy) -> {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("resource must not be blank");
            }
            copied.put(name, Objects.requireNonNull(policy, "resource policy").snapshot());
        });
        State current = state;
        state = new State(current.mode, current.query, current.mutation,
                copied, current.sessionQuery, current.sessionMutation);
    }

    private static PolicyValues resolve(State state, boolean mutation, PolicyValues.Loading resource) {
        Duration timeout = Duration.ofSeconds(10);
        PolicyValues.Loading loading = new PolicyValues.Loading(true, 25, true, 100);
        if (mutation) {
            loading = loading.overlay(new PolicyValues.Loading(true, 100, true, 1_000));
        }
        if (state.mode == DatabaseExecutionMode.EXTENDED_WORK) {
            timeout = null;
            loading = loading.overlay(new PolicyValues.Loading(false, null, true, null));
        }
        PolicyValues operation = mutation ? state.mutation : state.query;
        if (operation.timeoutSpecified) {
            timeout = operation.timeout;
        }
        loading = loading.overlay(operation.loading).overlay(resource);
        PolicyValues session = mutation ? state.sessionMutation : state.sessionQuery;
        if (session.timeoutSpecified) {
            timeout = session.timeout;
        }
        loading = loading.overlay(session.loading).validated();
        PolicyValues.validateTimeout(timeout);
        return new PolicyValues(true, timeout, loading);
    }

    private static void requireAuthorized(boolean authorized) {
        if (!authorized) {
            throw new SecurityException("Session policy adjustment requires prior authorization");
        }
    }

    /**
     * One immutable, atomically published configuration generation.
     */
    private static final class State implements Serializable {

        private static final long serialVersionUID = 1L;
        final DatabaseExecutionMode mode;
        final PolicyValues query;
        final PolicyValues mutation;
        final Map<String, PolicyValues.Loading> resources;
        final PolicyValues sessionQuery;
        final PolicyValues sessionMutation;

        State(DatabaseExecutionMode mode, PolicyValues query, PolicyValues mutation,
                Map<String, PolicyValues.Loading> resources, PolicyValues sessionQuery, PolicyValues sessionMutation) {
            this.mode = mode;
            this.query = query;
            this.mutation = mutation;
            this.resources = Collections.unmodifiableMap(new LinkedHashMap<>(resources));
            this.sessionQuery = sessionQuery;
            this.sessionMutation = sessionMutation;
        }
    }
}
