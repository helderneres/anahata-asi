/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.agi.tool.execution;

import java.time.Duration;

/**
 * Read contract for an execution's additional cooperative time allowance.
 */
public interface ExecutionPolicy {

    /**
     * @return positive allowance, or null for no additional policy timeout
     */
    Duration getTimeout();
}
