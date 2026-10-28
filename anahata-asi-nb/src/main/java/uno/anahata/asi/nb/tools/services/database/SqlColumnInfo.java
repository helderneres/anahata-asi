/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.nb.tools.services.database;

import lombok.Builder;
import lombok.Data;

/**
 * Selected attributes of one column in an executed SQL result.
 * <p>
 * Position identifies the column independently of names or labels, which
 * need not be unique. Each optional attribute is obtained only when its
 * GraphQL field is selected; a null DTO property may therefore mean either
 * unselected metadata or a driver-reported unknown value.
 * </p>
 */
@Data
@Builder
public class SqlColumnInfo {

    /**
     * One-based JDBC result-column position, preserved in the returned column order.
     */
    private int index;
    /**
     * Selected JDBC column label, normally reflecting a SQL alias; duplicates are retained
     * rather than used as unique keys.
     */
    private String label;
    /**
     * Selected underlying column name from {@link
     * java.sql.ResultSetMetaData#getColumnName(int)}, which can differ from the label or be
     * unavailable for expressions.
     */
    private String name;
    /**
     * Selected {@link java.sql.Types} numeric code from {@link
     * java.sql.ResultSetMetaData#getColumnType(int)}, or {@code null} if not obtained.
     */
    private Integer jdbcType;
    /**
     * Selected database-specific SQL type name, or {@code null} if not obtained.
     */
    private String typeName;
    /**
     * Selected JDBC nullability: true for {@code columnNullable}, false for {@code
     * columnNoNulls}, and null for unknown or unselected metadata.
     */
    private Boolean nullable;
}
