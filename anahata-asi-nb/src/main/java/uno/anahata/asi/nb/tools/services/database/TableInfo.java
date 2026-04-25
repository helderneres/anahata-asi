/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.nb.tools.services.database;

import lombok.Builder;
import lombok.Data;

/**
 * Represents a database table or view.
 * <p>
 * Translates the generic {@link java.sql.ResultSet} from
 * {@code DatabaseMetaData.getTables()} into a structured and easily readable
 * DTO.
 * </p>
 *
 * @author anahata
 */
@Data
@Builder
public class TableInfo {

    private String tableName;
    private String tableType;
    private String schema;
    private String remarks;

}
