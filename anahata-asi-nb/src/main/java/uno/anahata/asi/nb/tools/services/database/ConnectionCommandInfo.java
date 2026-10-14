/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.nb.tools.services.database;

import lombok.Builder;
import lombok.Data;

/**
 * Public status of an explicit IDE connection command, without handles or
 * credentials.
 */
@Data
@Builder
public class ConnectionCommandInfo {

    /**
     * Whether the requested state was reached.
     */
    private boolean success;
    /**
     * Whether the registered connection is now active.
     */
    private boolean connected;
}
