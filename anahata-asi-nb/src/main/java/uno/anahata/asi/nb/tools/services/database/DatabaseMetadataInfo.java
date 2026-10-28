/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.nb.tools.services.database;

import lombok.Builder;
import lombok.Data;

/**
 * Selected database-product and driver identification reported by JDBC.
 * <p>
 * Only requested attributes are obtained from {@link java.sql.DatabaseMetaData};
 * an unselected attribute remains {@code null} in this DTO. Driver-reported
 * nulls are also preserved. The object contains descriptive strings, not a
 * live metadata handle or a complete catalogue of database capabilities.
 * </p>
 *
 * @author anahata
 */
@Data
@Builder
public class DatabaseMetadataInfo {
    /**
     * Selected database product name from {@link
     * java.sql.DatabaseMetaData#getDatabaseProductName()}, or {@code null} if not populated.
     */
    private String databaseProductName;
    /**
     * Selected driver-reported database product version string, or {@code null} if not
     * populated; no semantic-version interpretation is applied.
     */
    private String databaseProductVersion;
    /**
     * Selected JDBC driver name from {@link java.sql.DatabaseMetaData#getDriverName()}, or
     * {@code null} if not populated.
     */
    private String driverName;
    /**
     * Selected driver-reported JDBC version string, or {@code null} if not populated; this is
     * distinct from the database product version.
     */
    private String driverVersion;
}
