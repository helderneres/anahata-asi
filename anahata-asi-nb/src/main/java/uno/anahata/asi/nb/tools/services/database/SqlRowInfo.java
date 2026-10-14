/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.nb.tools.services.database;

import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * A zero-based result row containing cells in JDBC column order.
 */
@Data
@Builder
public class SqlRowInfo {

    private int index;
    private List<SqlCellInfo> cells;
}
