/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.agi.tool.execution;

/**
 * Manages command configuration, independently of command authorization.
 *
 * @param <A> implementation's own fluent type
 */
public interface MutableMutationExecutionPolicy<A extends MutableMutationExecutionPolicy<A>>
        extends MutationExecutionPolicy, MutableExecutionPolicy<A> {

    /**
     * Replaces return acquisition configuration without changing command
     * effects.
     *
     * @param resultLoadingPolicy non-null result loading configuration
     * @return this configuration instance
     */
    A putResultLoadingPolicy(ResultLoadingPolicy resultLoadingPolicy);
}
