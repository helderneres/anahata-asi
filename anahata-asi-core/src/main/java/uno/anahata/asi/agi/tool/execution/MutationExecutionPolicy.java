/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.agi.tool.execution;

/**
 * Read contract for commands. Loading applies only to returned results and
 * never reduces the scope of UPDATE, DELETE, DDL or another requested effect.
 */
public interface MutationExecutionPolicy extends ExecutionPolicy {

    /**
     * @return non-null result loading policy when resolved
     */
    ResultLoadingPolicy getResultLoadingPolicy();
}
