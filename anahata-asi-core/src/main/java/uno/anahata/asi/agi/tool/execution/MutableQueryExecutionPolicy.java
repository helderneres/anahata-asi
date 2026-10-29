/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.agi.tool.execution;

/**
 * Manages query configuration; it does not grant execution permission.
 *
 * @param <A> implementation's own fluent type
 */
public interface MutableQueryExecutionPolicy<A extends MutableQueryExecutionPolicy<A>>
        extends QueryExecutionPolicy, MutableExecutionPolicy<A> {

    /**
     * Replaces result acquisition configuration without executing a query.
     *
     * @param loadingPolicy non-null loading configuration
     * @return this configuration instance
     */
    A putLoadingPolicy(ResultLoadingPolicy loadingPolicy);
}
