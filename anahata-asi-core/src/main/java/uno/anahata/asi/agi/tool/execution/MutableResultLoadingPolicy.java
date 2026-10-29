/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.agi.tool.execution;

/**
 * Manages result loading configuration without acquiring results.
 *
 * @param <A> implementation's own fluent type
 */
public interface MutableResultLoadingPolicy<A extends MutableResultLoadingPolicy<A>> extends ResultLoadingPolicy {

    /**
     * Replaces the default, independently of the maximum.
     *
     * @param defaultPageSize mandatory positive default
     * @return this configuration instance
     * @throws IllegalArgumentException if the default is null or not positive
     */
    A putDefaultPageSize(Integer defaultPageSize);

    /**
     * Replaces the maximum; null explicitly removes an inherited maximum.
     *
     * @param maxPageSize positive maximum, or null
     * @return this configuration instance
     * @throws IllegalArgumentException if a non-null maximum is not positive
     */
    A putMaxPageSize(Integer maxPageSize);
}
