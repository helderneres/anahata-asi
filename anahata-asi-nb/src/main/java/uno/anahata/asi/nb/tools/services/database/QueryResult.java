/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.nb.tools.services.database;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

/**
 * Represents the result of a database query.
 * <p>
 * Safely wraps the execution of a {@link java.sql.ResultSet} into a
 * disconnected, memory-bound DTO. This prevents leaking open database cursors
 * to the ASI context and limits the maximum number of rows processed.
 * </p>
 *
 * @author anahata
 */
@Data
@Builder
public class QueryResult {

    private List<String> headers;
    private List<Map<String, Object>> rows;
    private int rowCount;
    private long executionTimeMs;

}
