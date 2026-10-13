/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.nb.tools.services.database;

import lombok.Builder;
import lombok.Data;

/**
 * One JDBC foreign-key component, preserving both table identities and rules.
 * The key sequence allows callers to reconstruct composite relationships.
 *
 * @author anahata
 */
@Data
@Builder
public class TableRelationshipInfo {

    /**
     * Referenced table catalog, possibly absent.
     */
    private String primaryKeyCatalog;
    /**
     * Referenced table schema, possibly absent.
     */
    private String primaryKeySchema;
    /**
     * Referenced table name.
     */
    private String primaryKeyTable;
    /**
     * Referenced column name.
     */
    private String primaryKeyColumn;
    /**
     * Referencing table catalog, possibly absent.
     */
    private String foreignKeyCatalog;
    /**
     * Referencing table schema, possibly absent.
     */
    private String foreignKeySchema;
    /**
     * Referencing table name.
     */
    private String foreignKeyTable;
    /**
     * Referencing column name.
     */
    private String foreignKeyColumn;
    /**
     * One-based position in the relationship, possibly unknown.
     */
    private Integer keySequence;
    /**
     * JDBC update-rule constant, possibly unknown.
     */
    private Integer updateRule;
    /**
     * JDBC delete-rule constant, possibly unknown.
     */
    private Integer deleteRule;
    /**
     * Foreign-key constraint name, possibly absent.
     */
    private String foreignKeyName;
    /**
     * Referenced primary-key constraint name, possibly absent.
     */
    private String primaryKeyName;
    /**
     * JDBC deferrability constant, possibly unknown.
     */
    private Integer deferrability;
}
