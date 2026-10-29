/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.agi.tool.execution;

import java.time.Duration;

/**
 * Manages editable configuration without executing domain work.
 *
 * @param <A> implementation's own fluent type
 */
public interface MutableExecutionPolicy<A extends MutableExecutionPolicy<A>> extends ExecutionPolicy {

    /**
     * Replaces the allowance; null explicitly removes an inherited allowance.
     *
     * @param timeout positive duration, or null
     * @return this configuration instance
     * @throws IllegalArgumentException if timeout is zero or negative
     */
    A putTimeout(Duration timeout);
}
