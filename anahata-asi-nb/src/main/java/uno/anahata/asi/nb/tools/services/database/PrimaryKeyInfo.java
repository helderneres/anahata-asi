/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.nb.tools.services.database;

import lombok.Builder;
import lombok.Data;

/**
 * One column component of a JDBC-reported primary key.
 * <p>
 * A composite key yields separate components. Keep the owning connection,
 * catalog, schema and table identity together, and use {@code keySequence}
 * to interpret column order; neither a table name nor a constraint name is
 * assumed globally unique. This object contains copied values only and owns
 * no JDBC resources.
 * </p>
 *
 * @author anahata
 */
@Data
@Builder
public class PrimaryKeyInfo {

    /**
     * Owning table catalog reported in JDBC {@code TABLE_CAT}, possibly {@code null}.
     */
    private String catalog;
    /**
     * Owning table schema reported in JDBC {@code TABLE_SCHEM}, possibly {@code null}.
     */
    private String schema;
    /**
     * Owning table identifier from JDBC {@code TABLE_NAME}, without case normalization.
     */
    private String tableName;
    /**
     * Column identifier participating in this key component.
     */
    private String columnName;
    /**
     * One-based position from JDBC {@code KEY_SEQ}, or {@code null} when SQL NULL is
     * reported. The list itself is not resorted by this DTO.
     */
    private Integer keySequence;
    /**
     * Primary-key constraint name from JDBC {@code PK_NAME}, possibly {@code null}; not
     * assumed globally unique.
     */
    private String keyName;
}
