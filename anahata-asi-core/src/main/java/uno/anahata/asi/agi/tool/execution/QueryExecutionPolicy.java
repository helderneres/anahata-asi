/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.agi.tool.execution;

/**
 * Read contract for a query; reading this configuration does not authorize
 * work.
 */
public interface QueryExecutionPolicy extends ExecutionPolicy {

    /**
     * @return non-null result acquisition policy when resolved
     */
    ResultLoadingPolicy getLoadingPolicy();
}
