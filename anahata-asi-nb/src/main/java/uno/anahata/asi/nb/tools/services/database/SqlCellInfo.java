/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.nb.tools.services.database;

import lombok.Builder;
import lombok.Data;

/** A positional scalar cell; duplicate labels cannot overwrite its value. */
@Data
@Builder
public class SqlCellInfo {
    private int columnIndex;
    private Object value;
    private boolean truncated;
    private String encoding;
}
