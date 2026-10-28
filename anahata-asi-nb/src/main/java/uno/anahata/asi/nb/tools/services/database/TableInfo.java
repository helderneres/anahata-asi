/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.nb.tools.services.database;

import lombok.Builder;
import lombok.Data;

/**
 * Copied identity and descriptive attributes of one JDBC table or view.
 * <p>
 * Catalog, schema and table name must be interpreted together with the
 * registered connection retained privately by the resolver. A table name
 * alone is not a complete identity across schemas or databases. This object
 * does not load columns, primary keys or relationships; those GraphQL fields
 * perform their own selected metadata operations.
 * </p>
 *
 * @author anahata
 */
@Data
@Builder
public class TableInfo {

    /**
     * JDBC {@code TABLE_CAT}; {@code null} when the driver does not report a catalog.
     */
    private String catalog;
    /**
     * JDBC {@code TABLE_NAME}, retained exactly as reported and interpreted with catalog,
     * schema and owning connection.
     */
    private String tableName;
    /**
     * Driver-reported {@code TABLE_TYPE}, such as TABLE or VIEW; not restricted to an
     * application-defined enumeration.
     */
    private String tableType;
    /**
     * JDBC {@code TABLE_SCHEM}; {@code null} when the driver does not report a schema.
     */
    private String schema;
    /**
     * Driver-reported {@code REMARKS}, possibly {@code null} or empty; no additional comment
     * lookup is performed.
     */
    private String remarks;

}
