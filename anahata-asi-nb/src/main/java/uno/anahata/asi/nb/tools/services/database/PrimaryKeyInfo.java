/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.nb.tools.services.database;

import lombok.Builder;
import lombok.Data;

/**
 * A JDBC primary-key component with its complete table identity and sequence.
 * Composite keys retain the driver's key sequence instead of collapsing to a
 * name.
 *
 * @author anahata
 */
@Data
@Builder
public class PrimaryKeyInfo {

    /**
     * Catalog reported by JDBC, possibly null.
     */
    private String catalog;
    /**
     * Schema reported by JDBC, possibly null.
     */
    private String schema;
    /**
     * Exact table name reported by JDBC.
     */
    private String tableName;
    /**
     * Column participating in the primary key.
     */
    private String columnName;
    /**
     * One-based position in a composite primary key, possibly unknown.
     */
    private Integer keySequence;
    /**
     * Constraint name, possibly absent in the driver.
     */
    private String keyName;
}
