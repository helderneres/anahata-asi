/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.agi.tool.execution;

/**
 * Identifies a stable toolkit execution profile, independently of
 * authorization.
 */
public interface ExecutionMode {

    /**
     * @return stable, non-localized profile name, never null
     */
    String getName();
}
