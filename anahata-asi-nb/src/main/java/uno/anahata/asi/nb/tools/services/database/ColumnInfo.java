/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.nb.tools.services.database;

import lombok.Builder;
import lombok.Data;

/**
 * A disconnected description of one column reported by JDBC table metadata.
 * <p>
 * The producer copies column attributes without determining primary-key
 * membership. The GraphQL {@code primaryKey} field is resolved separately,
 * only when selected, using the column's private table source. Unknown JDBC
 * flags and sizes remain {@code null} instead of becoming false or zero.
 * </p>
 *
 * @author anahata
 */
@Data
@Builder
public class ColumnInfo {

    /**
     * Column identifier from JDBC {@code COLUMN_NAME}, as reported by the driver.
     */
    private String columnName;
    /**
     * Driver-specific SQL type name from {@code TYPE_NAME}; not a {@link java.sql.Types}
     * code.
     */
    private String dataType;
    /**
     * JDBC {@code COLUMN_SIZE}, or {@code null} for SQL NULL. Units depend on the SQL type,
     * such as numeric precision or character length; this is not uniformly a byte count.
     */
    private Integer columnSize;
    /**
     * Whether JDBC reports {@code IS_NULLABLE} as YES or NO; {@code null} when the flag is
     * absent or unknown.
     */
    private Boolean nullable;
    /**
     * Whether JDBC reports {@code IS_AUTOINCREMENT} as YES or NO; {@code null} when the flag
     * is absent or unknown.
     */
    private Boolean autoIncrement;

}
