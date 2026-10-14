/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.nb.tools.services.database;

import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * Selected, bounded SQL output. Unknown counts/continuity remain nullable.
 */
@Data
@Builder
public class SqlQueryInfo {

    private boolean success;
    private List<SqlColumnInfo> columns;
    private List<SqlRowInfo> rows;
    private Integer returnedRowCount;
    private int limit;
    private Boolean hasNextPage;
    private Integer totalCount;
    private Long elapsedTimeMs;
}
