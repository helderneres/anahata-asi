/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.nb.tools.services.database.policy;

import java.io.Serializable;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import uno.anahata.asi.agi.tool.execution.MutationExecutionPolicy;
import uno.anahata.asi.agi.tool.execution.QueryExecutionPolicy;
import uno.anahata.asi.agi.tool.execution.ResultLoadingPolicy;

/**
 * Immutable effective values captured for one execution. Nested policies and
 * resource maps contain only immutable copies, never the editable
 * configuration. A mode changes acquisition allowances without changing
 * authorization or target.
 */
public final class DatabaseExecutionPolicies implements Serializable {

    private static final long serialVersionUID = 1L;
    private final DatabaseExecutionMode mode;
    private final EffectiveQuery query;
    private final EffectiveMutation mutation;
    private final Map<String, ResultLoadingPolicy> queryResources;
    private final Map<String, ResultLoadingPolicy> mutationResources;

    DatabaseExecutionPolicies(DatabaseExecutionMode mode, PolicyValues query, PolicyValues mutation,
            Map<String, ResultLoadingPolicy> queryResources, Map<String, ResultLoadingPolicy> mutationResources) {
        this.mode = mode;
        this.query = new EffectiveQuery(query.timeout, query.loading);
        this.mutation = new EffectiveMutation(mutation.timeout, mutation.loading);
        this.queryResources = Collections.unmodifiableMap(new LinkedHashMap<>(queryResources));
        this.mutationResources = Collections.unmodifiableMap(new LinkedHashMap<>(mutationResources));
    }

    /**
     * @return selected stable profile
     */
    public DatabaseExecutionMode getMode() {
        return mode;
    }

    /**
     * @return immutable query policy
     */
    public QueryExecutionPolicy getQueryPolicy() {
        return query;
    }

    /**
     * @return immutable mutation policy; this does not authorize commands
     */
    public MutationExecutionPolicy getMutationPolicy() {
        return mutation;
    }

    /**
     * Returns the single additional request allowance for the chosen channel.
     *
     * @param command true for Mutation, false for Query
     * @return positive allowance, or null for no additional timeout
     */
    public Duration getTimeout(boolean command) {
        return command ? mutation.getTimeout() : query.getTimeout();
    }

    /**
     * Resolves already-validated resource loading values without reading
     * config.
     *
     * @param command true for Mutation, false for Query
     * @param resource stable resolver resource name, or null for operation
     * defaults
     * @return immutable non-null loading policy
     */
    public ResultLoadingPolicy getLoadingPolicy(boolean command, String resource) {
        ResultLoadingPolicy fallback = command ? mutation.getResultLoadingPolicy() : query.getLoadingPolicy();
        return (command ? mutationResources : queryResources).getOrDefault(resource, fallback);
    }

    /**
     * Immutable query value; no mutation interface is implemented.
     */
    private static final class EffectiveQuery implements QueryExecutionPolicy, Serializable {

        private static final long serialVersionUID = 1L;
        private final Duration timeout;
        private final ResultLoadingPolicy loading;

        EffectiveQuery(Duration timeout, ResultLoadingPolicy loading) {
            this.timeout = timeout;
            this.loading = loading;
        }

        @Override
        public Duration getTimeout() {
            return timeout;
        }

        @Override
        public ResultLoadingPolicy getLoadingPolicy() {
            return loading;
        }
    }

    /**
     * Immutable command value; loading controls returned results only.
     */
    private static final class EffectiveMutation implements MutationExecutionPolicy, Serializable {

        private static final long serialVersionUID = 1L;
        private final Duration timeout;
        private final ResultLoadingPolicy loading;

        EffectiveMutation(Duration timeout, ResultLoadingPolicy loading) {
            this.timeout = timeout;
            this.loading = loading;
        }

        @Override
        public Duration getTimeout() {
            return timeout;
        }

        @Override
        public ResultLoadingPolicy getResultLoadingPolicy() {
            return loading;
        }
    }
}
