/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.nb.tools.services.database.policy;

import uno.anahata.asi.agi.tool.execution.ExecutionMode;

/**
 * Stable profiles; enum values carry no session state or authorization.
 */
public enum DatabaseExecutionMode implements ExecutionMode {
    /**
     * Discovery defaults to 25/100 results; command returns use 100/1000, with
     * ten seconds.
     */
    EXPLORATION("Exploration"),
    /**
     * Retains defaults of 25/100 results, without an additional page ceiling or
     * timeout.
     */
    EXTENDED_WORK("Extended Work");
    private final String name;

    private DatabaseExecutionMode(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name();
    }
}
