/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.nb.tools.services.database;

import lombok.Builder;
import lombok.Data;

/** Outcome of one SQL command; affectedRows is the driver's update count. */
@Data
@Builder
public class SqlStatementInfo {
    private boolean success;
    private int affectedRows;
    private Long elapsedTimeMs;
}
