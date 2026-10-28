/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.nb.tools.services.database;

import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * One row admitted to the bounded SQL result, with positional cells.
 * <p>
 * Indices restart at zero for each query result; no offset is applied by this
 * row DTO. Cell order follows the JDBC column positions rather than labels,
 * so duplicate aliases remain distinct. The object is disconnected and does
 * not provide access to a cursor.
 * </p>
 */
@Data
@Builder
public class SqlRowInfo {

    /**
     * Zero-based position among rows admitted to this command's result, not a persistent row
     * identifier or a database ordering guarantee.
     */
    private int index;
    /**
     * Cells in ascending one-based JDBC column order. The producer supplies an empty list
     * when cells were not selected; absence of extracted cells does not establish that the
     * row has no columns.
     */
    private List<SqlCellInfo> cells;
}
