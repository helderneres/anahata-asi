/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.nb.tools.services.database;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.ArgumentCaptor;
import org.netbeans.api.db.explorer.ConnectionManager;
import org.netbeans.api.db.explorer.DatabaseConnection;
import org.netbeans.api.db.explorer.JDBCDriver;
import uno.anahata.asi.agi.tool.ToolManager;
import uno.anahata.asi.agi.message.RagMessage;
import uno.anahata.asi.agi.tool.graphql.GraphQLExecutionInfo;
import uno.anahata.asi.agi.tool.spi.java.JavaObjectToolkit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Contract tests for the selection-driven GraphQL database toolkit.
 *
 * <p>
 * All database access is mocked. The tests assert which JDBC operations occur,
 * as well as the returned data, so response projection cannot accidentally hide
 * eager metadata loading.</p>
 */
public class DatabaseGraphQLTest {

    /**
     * Static mock scope isolates all discovery from the real NetBeans
     * singleton.
     */
    private MockedStatic<ConnectionManager> managerStatic;
    /**
     * Mock registry shared by connection and listener assertions.
     */
    private ConnectionManager manager;
    /**
     * Real toolkit whose embedded engine is exercised by each contract test.
     */
    private Database database;
    /**
     * Registered IDE connection retaining a private mocked JDBC handle.
     */
    private DatabaseConnection registered;
    /**
     * NetBeans-owned handle that must never be closed by metadata discovery.
     */
    private Connection connection;
    /**
     * JDBC metadata mock exposes the operations performed by each selection.
     */
    private DatabaseMetaData metadata;

    /**
     * Creates a real toolkit against mock IDE and JDBC boundaries for each
     * query.
     */
    @BeforeEach
    void setUp() throws Exception {
        managerStatic = mockStatic(ConnectionManager.class);
        manager = mock(ConnectionManager.class);
        managerStatic.when(ConnectionManager::getDefault).thenReturn(manager);
        connection = mock(Connection.class);
        metadata = mock(DatabaseMetaData.class);
        when(connection.getMetaData()).thenReturn(metadata);
        registered = registeredConnection("ANAHATA", connection);
        when(manager.getConnections()).thenReturn(new DatabaseConnection[]{registered});
        database = new Database();
        database.initialize();
    }

    /**
     * Releases the thread-local static mock so later database suites are
     * isolated.
     */
    @AfterEach
    void tearDown() {
        managerStatic.close();
    }

    /**
     * The actual tool registry must discover inherited GraphQL and preserve SQL
     * tools.
     */
    @Test
    void inheritedGraphQLToolsAreRegisteredByTheRealJavaObjectToolkit() throws Exception {
        JavaObjectToolkit toolkit = new JavaObjectToolkit(mock(ToolManager.class), Database.class);
        List<String> names = toolkit.getAllTools().stream().map(tool -> tool.getName()).toList();
        assertTrue(names.contains("Database.schema"), names.toString());
        assertTrue(names.contains("Database.query"), names.toString());
        assertTrue(names.contains("Database.mutate"), names.toString());
        assertEquals(3, names.size(), names.toString());
    }

    /**
     * Lifecycle recovery rebuilds the engine while retaining one listener
     * registration.
     */
    @Test
    void initializeAndRebindKeepTheEngineAndOneConnectionListener() {
        database.initialize();
        database.rebind();
        verify(manager, times(1)).addConnectionListener(database);
        assertTrue(database.schema().contains("type TableInfo"));
        GraphQLExecutionInfo result = database.query("{ connections { items { displayName } } }", Map.of());
        assertTrue(result.getErrors().isEmpty(), result.getErrors().stream()
                .map(error -> error.getCategory() + ": " + error.getMessage()).toList().toString());
        assertEquals("ANAHATA", firstItem(result, "connections").get("displayName"));
    }

    /**
     * Exercises variable filters and ensures discovery never reads credentials.
     */
    @Test
    void connectionNamesAndFiltersUseVariablesWithoutExposingCredentials() throws Exception {
        DatabaseConnection inactive = registeredConnection("anahata archive", null);
        DatabaseConnection other = registeredConnection("Other", mock(Connection.class));
        when(manager.getConnections()).thenReturn(new DatabaseConnection[]{registered, inactive, other});
        GraphQLExecutionInfo result = successful("""
                query Connections($name: String!, $connected: Boolean!) {
                  connections(nameContains: $name, connected: $connected, offset: 0, limit: 1) {
                    items { displayName connected schema driverClass }
                    offset limit hasNextPage totalCount
                  }
                }
                """, Map.of("name", "AnAhAtA", "connected", false));
        Map<String, Object> page = object(result.getData().get("connections"));
        assertEquals("anahata archive", firstItem(result, "connections").get("displayName"));
        assertEquals(false, page.get("hasNextPage"));
        assertEquals(1, page.get("limit"));
        verify(metadata, never()).getTables(any(), any(), any(), any());
        verify(connection, never()).getMetaData();
        verify(registered, never()).getPassword();
        verify(registered, never()).getUser();
        verify(registered, never()).getDatabaseURL();
        verify(inactive, never()).getPassword();
        verify(inactive, never()).getUser();
        verify(inactive, never()).getDatabaseURL();
    }

    /**
     * A shallow table selection must perform only table discovery.
     */
    @Test
    void tableNamesDoNotLoadColumnsKeysOrRelationships() throws Exception {
        ResultSet tables = tables(table("catalog", "public", "users"));
        when(metadata.getTables(any(), any(), any(), any())).thenReturn(tables);
        GraphQLExecutionInfo result = successful("""
                { connection(name: "ANAHATA") { tables(limit: 10) { items { tableName } } } }
                """);
        assertEquals("users", firstTable(result).get("tableName"));
        verify(metadata, never()).getColumns(any(), any(), any(), any());
        verify(metadata, never()).getPrimaryKeys(any(), any(), any());
        verify(metadata, never()).getImportedKeys(any(), any(), any());
        verify(metadata, never()).getExportedKeys(any(), any(), any());
        verify(tables).close();
        verify(connection, never()).close();
        verify(connection, never()).createStatement();
    }

    /**
     * Column attributes alone must not trigger the independent PK operation.
     */
    @Test
    void columnNamesDoNotLoadPrimaryKeys() throws Exception {
        ResultSet tables = tables(table("catalog", "public", "users"));
        ResultSet columns = rows(column("id"));
        when(metadata.getTables(any(), any(), any(), any())).thenReturn(tables);
        when(metadata.getColumns("catalog", "public", "users", "%")).thenReturn(columns);
        GraphQLExecutionInfo result = successful("""
                { connection(name: "ANAHATA") {
                  tables(limit: 10) { items { tableName columns { columnName } } }
                } }
                """);
        assertEquals("id", object(list(firstTable(result).get("columns")).get(0)).get("columnName"));
        verify(metadata).getColumns("catalog", "public", "users", "%");
        verify(metadata, never()).getPrimaryKeys(any(), any(), any());
        verify(metadata, never()).getImportedKeys(any(), any(), any());
        verify(metadata, never()).getExportedKeys(any(), any(), any());
        verify(tables).close();
        verify(columns).close();
        verify(connection, never()).close();
    }

    /**
     * One request shares PK metadata between column flags and table key
     * components.
     */
    @Test
    void selectedColumnPrimaryKeyLoadsKeysOnceAndSharesThemWithTablePrimaryKeys() throws Exception {
        ResultSet tables = tables(table("catalog", "public", "users"));
        ResultSet columns = rows(column("id"), column("name"));
        ResultSet keys = rows(primaryKey("catalog", "public", "users", "id"));
        when(metadata.getTables(any(), any(), any(), any())).thenReturn(tables);
        when(metadata.getColumns("catalog", "public", "users", "%")).thenReturn(columns);
        when(metadata.getPrimaryKeys("catalog", "public", "users")).thenReturn(keys);
        GraphQLExecutionInfo result = successful("""
                { connection(name: "ANAHATA") { tables(limit: 1) { items {
                  columns { columnName primaryKey }
                  primaryKeys { catalog schema tableName columnName keySequence keyName }
                } } } }
                """);
        List<?> returnedColumns = list(firstTable(result).get("columns"));
        assertEquals(true, object(returnedColumns.get(0)).get("primaryKey"));
        assertEquals(false, object(returnedColumns.get(1)).get("primaryKey"));
        assertEquals("id", object(list(firstTable(result).get("primaryKeys")).get(0)).get("columnName"));
        verify(metadata, times(1)).getPrimaryKeys("catalog", "public", "users");
        verify(keys).close();
        verify(columns).close();
        verify(connection, never()).close();
    }

    /**
     * A runtime directive excludes both the PK field and its JDBC dependency.
     */
    @Test
    void directivesCanSuppressTheConditionalPrimaryKeyDependency() throws Exception {
        when(metadata.getTables(any(), any(), any(), any())).thenAnswer(invocation -> tables(table(null, null, "users")));
        when(metadata.getColumns("", "", "users", "%")).thenAnswer(invocation -> rows(column("id")));
        successful("""
                query Columns($includePk: Boolean!) {
                  connection(name: "ANAHATA") { tables(limit: 1) { items {
                    columns { columnName primaryKey @include(if: $includePk) }
                  } } }
                }
                """, Map.of("includePk", false));
        verify(metadata, never()).getPrimaryKeys(any(), any(), any());
    }

    /**
     * Request caching must not preserve metadata across later queries.
     */
    @Test
    void primaryKeysAreCachedOnlyForTheirOwnRequest() throws Exception {
        when(metadata.getTables(any(), any(), any(), any()))
                .thenAnswer(invocation -> tables(table(null, null, "users")));
        when(metadata.getPrimaryKeys("", "", "users"))
                .thenAnswer(invocation -> rows(primaryKey(null, null, "users", "id")));
        String query = "{ connection(name: \"ANAHATA\") { tables(limit: 1) { items { primaryKeys { columnName } } } } }";
        successful(query);
        successful(query);
        verify(metadata, times(2)).getPrimaryKeys("", "", "users");
        verify(metadata, never()).getColumns(any(), any(), any(), any());
    }

    /**
     * Imported relationships preserve key identities without loading other
     * children.
     */
    @Test
    void importedRelationshipsDoNotLoadExportedRelationshipsOrColumns() throws Exception {
        ResultSet keys = rows(relationship());
        when(metadata.getTables(any(), any(), any(), any())).thenAnswer(invocation -> tables(table("catalog", "public", "users")));
        when(metadata.getImportedKeys("catalog", "public", "users")).thenReturn(keys);
        GraphQLExecutionInfo result = successful("""
                { connection(name: "ANAHATA") { tables(limit: 1) { items {
                  importedRelationships { primaryKeyCatalog primaryKeySchema primaryKeyTable
                    primaryKeyColumn foreignKeyCatalog foreignKeySchema foreignKeyTable
                    foreignKeyColumn keySequence updateRule deleteRule foreignKeyName
                    primaryKeyName deferrability }
                } } } }
                """);
        Map<String, Object> relation = object(list(firstTable(result).get("importedRelationships")).get(0));
        assertEquals("accounts", relation.get("primaryKeyTable"));
        assertEquals("users", relation.get("foreignKeyTable"));
        assertEquals("account_id", relation.get("foreignKeyColumn"));
        verify(metadata).getImportedKeys("catalog", "public", "users");
        verify(metadata, never()).getExportedKeys(any(), any(), any());
        verify(metadata, never()).getPrimaryKeys(any(), any(), any());
        verify(metadata, never()).getColumns(any(), any(), any(), any());
        verify(keys).close();
        verify(connection, never()).close();
    }

    /**
     * Exported relationships perform their own operation independently.
     */
    @Test
    void exportedRelationshipsDoNotLoadImportedRelationships() throws Exception {
        ResultSet keys = rows(relationship());
        when(metadata.getTables(any(), any(), any(), any())).thenAnswer(invocation -> tables(table(null, null, "users")));
        when(metadata.getExportedKeys("", "", "users")).thenReturn(keys);
        successful("""
                { connection(name: "ANAHATA") { tables(limit: 1) { items {
                  exportedRelationships { primaryKeyTable foreignKeyTable }
                } } } }
                """);
        verify(metadata).getExportedKeys("", "", "users");
        verify(metadata, never()).getImportedKeys(any(), any(), any());
        verify(metadata, never()).getPrimaryKeys(any(), any(), any());
        verify(metadata, never()).getColumns(any(), any(), any(), any());
        verify(keys).close();
    }

    /**
     * Discarded and lookahead rows cannot cause child JDBC operations.
     */
    @Test
    void jdbcTablePaginationSkipsOffsetStopsAtLookaheadAndResolvesOnlyReturnedChildren() throws Exception {
        ResultSet tables = tables(table("catalog", "public", "discarded"),
                table("catalog", "public", "returned"), table("catalog", "public", "lookahead"),
                table("catalog", "public", "unvisited"));
        ResultSet columns = rows(column("id"));
        when(metadata.getTables(any(), any(), any(), any())).thenReturn(tables);
        when(metadata.getColumns("catalog", "public", "returned", "%")).thenReturn(columns);
        GraphQLExecutionInfo result = successful("""
                { connection(name: "ANAHATA") { tables(offset: 1, limit: 1) {
                  items { tableName columns { columnName } }
                  offset limit hasNextPage totalCount
                } } }
                """);
        assertEquals("returned", firstTable(result).get("tableName"));
        Map<String, Object> page = tablePage(result);
        assertEquals(true, page.get("hasNextPage"));
        assertNull(page.get("totalCount"));
        assertEquals(1, page.get("offset"));
        assertEquals(1, list(page.get("items")).size());
        verify(tables, times(3)).next();
        verify(metadata).getColumns("catalog", "public", "returned", "%");
        verify(metadata, never()).getColumns(eq("catalog"), eq("public"), eq("discarded"), any());
        verify(metadata, never()).getColumns(eq("catalog"), eq("public"), eq("lookahead"), any());
        verify(metadata, never()).getColumns(eq("catalog"), eq("public"), eq("unvisited"), any());
        verify(tables).close();
        verify(connection, never()).close();
    }

    /**
     * A final page reports completion without a separate full-count traversal.
     */
    @Test
    void lastTablePageHasNoNextPageAndNoCountScan() throws Exception {
        ResultSet tables = tables(table(null, null, "users"));
        when(metadata.getTables(any(), any(), any(), any())).thenReturn(tables);
        GraphQLExecutionInfo result = successful("""
                { connection(name: "ANAHATA") { tables(limit: 1) {
                  items { tableName } hasNextPage totalCount
                } } }
                """);
        assertEquals(false, tablePage(result).get("hasNextPage"));
        assertNull(tablePage(result).get("totalCount"));
        verify(tables, times(2)).next();
        verify(tables).close();
    }

    /**
     * Catalog, schema, table pattern, and types reach native JDBC parameters.
     */
    @Test
    void tableFiltersArePushedToJdbcWithGraphQLVariables() throws Exception {
        when(metadata.getTables(any(), any(), any(), any())).thenAnswer(invocation -> tables());
        successful("""
                query Tables($catalog: String!, $schema: String!, $pattern: String!) {
                  connection(name: "ANAHATA") { tables(catalog: $catalog, schema: $schema,
                    tableNamePattern: $pattern, types: ["VIEW"], limit: 10) {
                    items { catalog schema tableName }
                  } }
                }
                """, Map.of("catalog", "catalog-a", "schema", "tenant_%", "pattern", "invoice%"));
        verify(metadata).getTables(eq("catalog-a"), eq("tenant_%"), eq("invoice%"), aryEq(new String[]{"VIEW"}));
        verify(metadata, never()).getColumns(any(), any(), any(), any());
    }

    /**
     * Catalog and schema distinguish identically named tables and their cached
     * keys.
     */
    @Test
    void homonymousTablesKeepCatalogAndSchemaForEveryChild() throws Exception {
        when(metadata.getTables(any(), any(), any(), any())).thenAnswer(invocation -> tables(
                table("catalog-a", "alpha", "users"), table("catalog-b", "beta", "users")));
        when(metadata.getColumns("catalog-a", "alpha", "users", "%")).thenAnswer(invocation -> rows(column("alpha_id")));
        when(metadata.getColumns("catalog-b", "beta", "users", "%")).thenAnswer(invocation -> rows(column("beta_id")));
        when(metadata.getPrimaryKeys("catalog-a", "alpha", "users"))
                .thenAnswer(invocation -> rows(primaryKey("catalog-a", "alpha", "users", "alpha_id")));
        when(metadata.getPrimaryKeys("catalog-b", "beta", "users"))
                .thenAnswer(invocation -> rows(primaryKey("catalog-b", "beta", "users", "beta_id")));
        GraphQLExecutionInfo result = successful("""
                { connection(name: "ANAHATA") { tables(limit: 2) { items {
                  catalog schema tableName columns { columnName primaryKey }
                  primaryKeys { catalog schema columnName }
                } } } }
                """);
        List<?> items = list(tablePage(result).get("items"));
        assertEquals("catalog-a", object(items.get(0)).get("catalog"));
        assertEquals("beta", object(items.get(1)).get("schema"));
        assertEquals("alpha_id", object(list(object(items.get(0)).get("columns")).get(0)).get("columnName"));
        assertEquals("beta_id", object(list(object(items.get(1)).get("columns")).get(0)).get("columnName"));
        verify(metadata).getPrimaryKeys("catalog-a", "alpha", "users");
        verify(metadata).getPrimaryKeys("catalog-b", "beta", "users");
    }

    /**
     * Invalid page bounds fail before a metadata ResultSet is opened.
     */
    @Test
    void invalidPaginationFailsBeforeOpeningJdbcResults() throws Exception {
        for (String args : List.of("limit: 0", "limit: 101", "offset: -1")) {
            GraphQLExecutionInfo result = database.query("{ connection(name: \"ANAHATA\") { tables(" + args
                    + ") { items { tableName } } } }", Map.of());
            assertFalse(result.getErrors().isEmpty(), args);
        }
        verify(metadata, never()).getTables(any(), any(), any(), any());
    }

    /**
     * Inactive discovery preserves connection attributes and never opens a GUI.
     */
    @Test
    void inactiveConnectionReturnsPartialDataAndActionableErrorWithoutShowingDialog() throws Exception {
        when(registered.getJDBCConnection()).thenReturn(null);
        GraphQLExecutionInfo result = database.query("""
                { connection(name: "ANAHATA") { displayName connected metadata { databaseProductName } } }
                """, Map.of());
        assertFalse(result.getErrors().isEmpty());
        Map<String, Object> returned = object(result.getData().get("connection"));
        assertEquals("ANAHATA", returned.get("displayName"));
        assertEquals(false, returned.get("connected"));
        assertNull(returned.get("metadata"));
        verify(manager, never()).showConnectionDialog(any());
        verify(metadata, never()).getTables(any(), any(), any(), any());
        verify(connection, never()).createStatement();
    }

    /**
     * A child failure leaves sibling data available and redacts sensitive
     * driver text.
     */
    @Test
    void metadataFailurePreservesOtherFieldsAndRedactsDriverError() throws Exception {
        String sensitive = "jdbc:secret://host/private?password=hidden user=admin";
        when(metadata.getTables(any(), any(), any(), any())).thenAnswer(invocation -> tables(table(null, null, "users")));
        when(metadata.getColumns("", "", "users", "%")).thenThrow(new SQLException(sensitive));
        GraphQLExecutionInfo result = database.query("""
                { connection(name: "ANAHATA") { displayName tables(limit: 1) { items { tableName columns { columnName } } } } }
                """, Map.of());
        assertFalse(result.getErrors().isEmpty());
        assertEquals("users", firstTable(result).get("tableName"));
        assertNull(firstTable(result).get("columns"));
        String errors = result.getErrors().toString();
        assertFalse(errors.contains("password=hidden"), errors);
        assertFalse(errors.contains("jdbc:secret"), errors);
        verify(connection, never()).close();
    }

    /**
     * Driver unknowns remain nullable scalars instead of false or zero
     * defaults.
     */
    @Test
    void jdbcUnknownBooleansAndNullColumnSizeRemainGraphQLNull() throws Exception {
        Map<String, Object> unknown = row("COLUMN_NAME", "unknown", "TYPE_NAME", null,
                "COLUMN_SIZE", null, "IS_NULLABLE", "", "IS_AUTOINCREMENT", "");
        when(metadata.getTables(any(), any(), any(), any())).thenAnswer(invocation -> tables(table(null, null, "users")));
        when(metadata.getColumns("", "", "users", "%")).thenAnswer(invocation -> rows(unknown));
        GraphQLExecutionInfo result = successful("""
                { connection(name: "ANAHATA") { tables(limit: 1) { items {
                  catalog schema columns { columnName dataType columnSize nullable autoIncrement }
                } } } }
                """);
        Map<String, Object> column = object(list(firstTable(result).get("columns")).get(0));
        assertNull(firstTable(result).get("catalog"));
        assertNull(firstTable(result).get("schema"));
        assertNull(column.get("dataType"));
        assertNull(column.get("columnSize"));
        assertNull(column.get("nullable"));
        assertNull(column.get("autoIncrement"));
        verify(metadata, never()).getPrimaryKeys(any(), any(), any());
    }

    /**
     * Resolver-owned table rows close when extraction fails, while JDBC stays
     * open.
     */
    @Test
    void resultSetsCloseEvenWhenMetadataExtractionFails() throws Exception {
        ResultSet tables = tables(table(null, null, "users"));
        when(metadata.getTables(any(), any(), any(), any())).thenReturn(tables);
        doThrow(new SQLException("private database details")).when(tables).getString("TABLE_NAME");
        GraphQLExecutionInfo result = database.query("{ connection(name: \"ANAHATA\") { tables(limit: 1) { items { tableName } } } }", Map.of());
        assertFalse(result.getErrors().isEmpty());
        verify(tables).close();
        verify(connection, never()).close();
    }

    /**
     * Missing connections stay nullable and credential fields fail schema
     * validation.
     */
    @Test
    void unknownConnectionAndCredentialFieldsCannotEscapeTheSchema() throws Exception {
        GraphQLExecutionInfo unknown = database.query("{ connection(name: \"missing\") { displayName } }", Map.of());
        assertEquals("ConnectionNotFound", unknown.getErrors().getFirst().getCategory());
        assertNull(unknown.getData().get("connection"));
        GraphQLExecutionInfo credentials = database.query("{ connection(name: \"ANAHATA\") { user password url } }", Map.of());
        assertFalse(credentials.getErrors().isEmpty());
        verify(registered, never()).getPassword();
        verify(registered, never()).getUser();
        verify(registered, never()).getDatabaseURL();
        verify(manager, never()).showConnectionDialog(any());
    }

    /**
     * Confirms a metadata parent translates its scalar selection to JDBC calls.
     */
    @Test
    void metadataSelectionLoadsOnlyTheChosenScalarDespiteItsAlias() throws Exception {
        when(metadata.getDatabaseProductName()).thenReturn("MockDB");
        GraphQLExecutionInfo result = successful("""
                { connection(name: "ANAHATA") { metadata { product: databaseProductName } } }
                """);
        Map<String, Object> returned = object(object(result.getData().get("connection")).get("metadata"));
        assertEquals("MockDB", returned.get("product"));
        verify(metadata).getDatabaseProductName();
        verify(metadata, never()).getDatabaseProductVersion();
        verify(metadata, never()).getDriverName();
        verify(metadata, never()).getDriverVersion();
        verify(metadata, never()).getTables(any(), any(), any(), any());
        verify(connection, never()).close();
    }

    /**
     * Fragments and aliases must retain the conditional PK dependency.
     */
    @Test
    void fragmentAndAliasSelectionResolvePrimaryKeyMembership() throws Exception {
        when(metadata.getTables(any(), any(), any(), any())).thenAnswer(invocation -> tables(table(null, null, "users")));
        when(metadata.getColumns("", "", "users", "%")).thenAnswer(invocation -> rows(column("id")));
        when(metadata.getPrimaryKeys("", "", "users")).thenAnswer(invocation -> rows(primaryKey(null, null, "users", "id")));
        GraphQLExecutionInfo result = successful("""
                query ColumnFlags {
                  connection(name: "ANAHATA") { tables(limit: 1) { items { columns { ...Flags } } } }
                }
                fragment Flags on ColumnInfo { label: columnName belongs: primaryKey }
                """);
        Map<String, Object> returned = object(list(firstTable(result).get("columns")).get(0));
        assertEquals("id", returned.get("label"));
        assertEquals(true, returned.get("belongs"));
        verify(metadata, times(1)).getPrimaryKeys("", "", "users");
    }

    /**
     * Child lookup must escape literal schema/table names and push its filter.
     */
    @Test
    void columnFilterUsesLiteralSchemaAndTableIdentityWithoutPatternCollisions() throws Exception {
        when(metadata.getSearchStringEscape()).thenReturn("\\");
        when(metadata.getTables(any(), any(), any(), any()))
                .thenAnswer(invocation -> tables(table("catalog", "tenant_1", "order_items")));
        when(metadata.getColumns("catalog", "tenant\\_1", "order\\_items", "id%"))
                .thenAnswer(invocation -> rows(column("id")));
        when(metadata.getPrimaryKeys("catalog", "tenant_1", "order_items"))
                .thenAnswer(invocation -> rows(primaryKey("catalog", "tenant_1", "order_items", "id")));
        successful("""
                { connection(name: "ANAHATA") { tables(limit: 1) { items {
                  columns(columnNamePattern: "id%") { columnName primaryKey }
                } } } }
                """);
        verify(metadata).getColumns("catalog", "tenant\\_1", "order\\_items", "id%");
        verify(metadata).getPrimaryKeys("catalog", "tenant_1", "order_items");
    }

    /**
     * A failed PK read remains cached so other selected fields do not retry
     * JDBC.
     */
    @Test
    void primaryKeyFailureIsSharedAcrossColumnsAndTableWithinTheRequest() throws Exception {
        when(metadata.getTables(any(), any(), any(), any())).thenAnswer(invocation -> tables(table(null, null, "users")));
        when(metadata.getColumns("", "", "users", "%")).thenAnswer(invocation -> rows(column("id"), column("name")));
        when(metadata.getPrimaryKeys("", "", "users"))
                .thenThrow(new SQLException("jdbc:private://host?password=secret"));
        GraphQLExecutionInfo result = database.query("""
                { connection(name: "ANAHATA") { tables(limit: 1) { items {
                  columns { columnName primaryKey } primaryKeys { columnName }
                } } } }
                """, Map.of());
        assertFalse(result.getErrors().isEmpty());
        List<?> returned = list(firstTable(result).get("columns"));
        assertEquals("id", object(returned.get(0)).get("columnName"));
        assertNull(object(returned.get(0)).get("primaryKey"));
        assertNull(firstTable(result).get("primaryKeys"));
        verify(metadata, times(1)).getPrimaryKeys("", "", "users");
        assertFalse(result.getErrors().toString().contains("password=secret"));
    }

    /**
     * PK ResultSets close even when a selected key component cannot be read.
     */
    @Test
    void primaryKeyResultSetClosesOnRowReadFailureAndKeepsColumnsPartial() throws Exception {
        ResultSet keys = rows(primaryKey(null, null, "users", "id"));
        doThrow(new SQLException("private key details")).when(keys).getString("COLUMN_NAME");
        when(metadata.getTables(any(), any(), any(), any())).thenAnswer(invocation -> tables(table(null, null, "users")));
        when(metadata.getColumns("", "", "users", "%")).thenAnswer(invocation -> rows(column("id")));
        when(metadata.getPrimaryKeys("", "", "users")).thenReturn(keys);
        GraphQLExecutionInfo result = database.query("""
                { connection(name: "ANAHATA") { tables(limit: 1) { items {
                  columns { columnName } primaryKeys { columnName }
                } } } }
                """, Map.of());
        assertFalse(result.getErrors().isEmpty());
        assertEquals("id", object(list(firstTable(result).get("columns")).get(0)).get("columnName"));
        assertNull(firstTable(result).get("primaryKeys"));
        verify(keys).close();
        verify(connection, never()).close();
    }

    /**
     * The automatic RAG snapshot carries connection counts without private
     * data.
     */
    @Test
    void ragContextIncludesCountsWithoutConnectionNamesUrlsOrUsers() throws Exception {
        RagMessage message = mock(RagMessage.class);
        database.populateMessage(message);
        ArgumentCaptor<String> texts = ArgumentCaptor.forClass(String.class);
        verify(message, atLeastOnce()).addTextPart(texts.capture());
        String context = String.join("\n", texts.getAllValues());
        assertTrue(context.contains("1"), context);
        assertFalse(context.contains("ANAHATA"), context);
        assertFalse(context.contains("jdbc:"), context);
        verify(registered, never()).getPassword();
        verify(registered, never()).getUser();
        verify(registered, never()).getDatabaseURL();
    }

    /**
     * Selecting only a metadata type name cannot justify obtaining JDBC
     * metadata.
     */
    @Test
    void metadataTypeNameNeedsNoJdbcMetadataOperation() throws Exception {
        GraphQLExecutionInfo result = successful("""
                { connection(name: "ANAHATA") { metadata { __typename } } }
                """);
        Map<String, Object> returned = object(object(result.getData().get("connection")).get("metadata"));
        assertEquals("DatabaseMetadataInfo", returned.get("__typename"));
        verify(connection, never()).getMetaData();
    }

    /**
     * Known page arguments and an unavailable count do not require scanning
     * tables.
     */
    @Test
    void tablePageArgumentsAndNullableCountNeedNoJdbcMetadata() throws Exception {
        GraphQLExecutionInfo result = successful("""
                { connection(name: "ANAHATA") { tables(offset: 3, limit: 7) { offset limit totalCount } } }
                """);
        GraphQLExecutionInfo defaults = successful("""
                { connection(name: "ANAHATA") { tables { offset limit totalCount } } }
                """);
        assertEquals(0, tablePage(defaults).get("offset"));
        assertEquals(25, tablePage(defaults).get("limit"));
        assertEquals(3, tablePage(result).get("offset"));
        assertEquals(7, tablePage(result).get("limit"));
        assertNull(tablePage(result).get("totalCount"));
        verify(connection, never()).getMetaData();
        verify(metadata, never()).getTables(any(), any(), any(), any());
    }

    /**
     * Page existence can be checked without extracting unselected table
     * attributes.
     */
    @Test
    void hasNextPageOnlySelectionDoesNotReadTableAttributes() throws Exception {
        ResultSet tables = tables(table(null, null, "users"), table(null, null, "accounts"));
        when(metadata.getTables(any(), any(), any(), any())).thenReturn(tables);
        GraphQLExecutionInfo result = successful("""
                { connection(name: "ANAHATA") { tables(limit: 1) { hasNextPage } } }
                """);
        assertEquals(true, tablePage(result).get("hasNextPage"));
        verify(tables, times(2)).next();
        verify(tables, never()).getString(anyString());
        verify(metadata, never()).getColumns(any(), any(), any(), any());
        verify(tables).close();
    }

    /**
     * Excess child rows report a partial error and close the cursor instead of
     * truncating.
     */
    @Test
    void excessiveColumnMetadataFailsWithClosedResultSet() throws Exception {
        ResultSet columns = mock(ResultSet.class);
        when(columns.next()).thenReturn(true);
        when(columns.getString("COLUMN_NAME")).thenReturn("id");
        when(metadata.getTables(any(), any(), any(), any())).thenAnswer(invocation -> tables(table(null, null, "users")));
        when(metadata.getColumns("", "", "users", "%")).thenReturn(columns);
        GraphQLExecutionInfo result = database.query("""
                { connection(name: "ANAHATA") { tables(limit: 1) { items { tableName columns { columnName } } } } }
                """, Map.of());
        assertFalse(result.getErrors().isEmpty());
        assertEquals("users", firstTable(result).get("tableName"));
        assertNull(firstTable(result).get("columns"));
        verify(columns, times(1001)).next();
        verify(columns).close();
        verify(connection, never()).close();
        verify(metadata, never()).getPrimaryKeys(any(), any(), any());
    }

    /**
     * A missing driver table identity cannot become an unconstrained key
     * lookup.
     */
    @Test
    void missingTableNameRejectsKeyAndRelationshipLookupsBeforeJdbcCalls() throws Exception {
        ResultSet tables = tables(table(null, null, null));
        when(metadata.getTables(any(), any(), any(), any())).thenReturn(tables);
        GraphQLExecutionInfo result = database.query("""
                { connection(name: "ANAHATA") { tables(limit: 1) { items {
                  tableName columns { columnName } primaryKeys { columnName }
                  importedRelationships { primaryKeyTable }
                  exportedRelationships { foreignKeyTable }
                } } } }
                """, Map.of());
        assertFalse(result.getErrors().isEmpty());
        assertNull(firstTable(result).get("tableName"));
        assertNull(firstTable(result).get("primaryKeys"));
        assertNull(firstTable(result).get("importedRelationships"));
        assertNull(firstTable(result).get("exportedRelationships"));
        assertNull(firstTable(result).get("columns"));
        verify(metadata, never()).getColumns(any(), any(), any(), any());
        verify(metadata, never()).getPrimaryKeys(any(), any(), any());
        verify(metadata, never()).getImportedKeys(any(), any(), any());
        verify(metadata, never()).getExportedKeys(any(), any(), any());
        verify(tables).close();
        verify(connection, never()).close();
    }

    /**
     * Executes a query without variables and asserts its successful public
     * envelope.
     *
     * @param query executable GraphQL document
     * @return data and an empty error list
     */
    private GraphQLExecutionInfo successful(String query) {
        return successful(query, Map.of());
    }

    /**
     * Executes the real engine with variables and requires a complete
     * successful result.
     *
     * @param query executable GraphQL document
     * @param variables runtime argument values
     * @return the public result envelope
     */
    private GraphQLExecutionInfo successful(String query, Map<String, Object> variables) {
        GraphQLExecutionInfo result = database.query(query, variables);
        assertTrue(result.getErrors().isEmpty(), result.getErrors().stream()
                .map(error -> error.getCategory() + ": " + error.getMessage()).toList().toString());
        assertNotNull(result.getData());
        return result;
    }

    /**
     * Builds a safe discovery fixture without stubbing credential access.
     *
     * @param name registered display name
     * @param jdbc existing connection, or null when inactive
     * @return the mocked NetBeans handle
     */
    private static DatabaseConnection registeredConnection(String name, Connection jdbc) {
        DatabaseConnection result = mock(DatabaseConnection.class);
        JDBCDriver driver = mock(JDBCDriver.class);
        when(driver.getClassName()).thenReturn("org.mock.Driver");
        when(result.getDisplayName()).thenReturn(name);
        when(result.getJDBCConnection()).thenReturn(jdbc);
        when(result.getSchema()).thenReturn("public");
        when(result.getJDBCDriver()).thenReturn(driver);
        return result;
    }

    /**
     * Describes one complete identity as reported by JDBC table metadata.
     *
     * @param catalog reported catalog, possibly null
     * @param schema reported schema, possibly null
     * @param name literal table name
     * @return named JDBC columns for one metadata row
     */
    private static Map<String, Object> table(String catalog, String schema, String name) {
        return row("TABLE_CAT", catalog, "TABLE_SCHEM", schema, "TABLE_NAME", name,
                "TABLE_TYPE", "TABLE", "REMARKS", null);
    }

    /**
     * Supplies ordinary column attributes without any primary-key information.
     *
     * @param name reported column name
     * @return named JDBC column metadata values
     */
    private static Map<String, Object> column(String name) {
        return row("COLUMN_NAME", name, "TYPE_NAME", "INTEGER", "COLUMN_SIZE", 11,
                "IS_NULLABLE", "NO", "IS_AUTOINCREMENT", "NO");
    }

    /**
     * Supplies one ordered key component with its full table identity.
     *
     * @param catalog reported catalog
     * @param schema reported schema
     * @param table literal table name
     * @param column key component name
     * @return named primary-key metadata values
     */
    private static Map<String, Object> primaryKey(String catalog, String schema, String table, String column) {
        return row("TABLE_CAT", catalog, "TABLE_SCHEM", schema, "TABLE_NAME", table,
                "COLUMN_NAME", column, "KEY_SEQ", 1, "PK_NAME", "pk_users");
    }

    /**
     * Supplies a complete FK/PK component pair including JDBC relationship
     * rules.
     *
     * @return named foreign-key metadata values
     */
    private static Map<String, Object> relationship() {
        return row("PKTABLE_CAT", "catalog", "PKTABLE_SCHEM", "public", "PKTABLE_NAME", "accounts",
                "PKCOLUMN_NAME", "id", "FKTABLE_CAT", "catalog", "FKTABLE_SCHEM", "public",
                "FKTABLE_NAME", "users", "FKCOLUMN_NAME", "account_id", "KEY_SEQ", 1,
                "UPDATE_RULE", 0, "DELETE_RULE", 1, "FK_NAME", "fk_users_account",
                "PK_NAME", "pk_accounts", "DEFERRABILITY", 7);
    }

    /**
     * Allows SQL NULL fixtures, which immutable Map factories cannot represent.
     *
     * @param pairs alternating JDBC column names and nullable values
     * @return insertion-ordered metadata values
     */
    private static Map<String, Object> row(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            result.put((String) pairs[index], pairs[index + 1]);
        }
        return result;
    }

    /**
     * Gives table fixtures the same cursor and closure behavior as other JDBC
     * rows.
     *
     * @param rows table metadata records in driver order
     * @return a resolver-owned mock ResultSet
     * @throws SQLException if JDBC stubbing fails
     */
    @SafeVarargs
    private static ResultSet tables(Map<String, Object>... rows) throws SQLException {
        return rows(rows);
    }

    /**
     * Simulates a forward-only cursor, primitive getters, and JDBC wasNull
     * semantics. The state is test-local and used by the engine on the calling
     * thread.
     *
     * @param data ordered metadata records with named columns
     * @return a mock ResultSet whose calls remain verifiable
     * @throws SQLException if JDBC stubbing fails
     */
    @SafeVarargs
    private static ResultSet rows(Map<String, Object>... data) throws SQLException {
        ResultSet result = mock(ResultSet.class);
        AtomicInteger cursor = new AtomicInteger(-1);
        boolean[] wasNull = {false};
        when(result.next()).thenAnswer(invocation -> cursor.incrementAndGet() < data.length);
        when(result.getString(anyString())).thenAnswer(invocation -> {
            Object value = data[cursor.get()].get(invocation.getArgument(0, String.class));
            wasNull[0] = value == null;
            return value == null ? null : value.toString();
        });
        when(result.getInt(anyString())).thenAnswer(invocation -> {
            Object value = data[cursor.get()].get(invocation.getArgument(0, String.class));
            wasNull[0] = value == null;
            return value == null ? 0 : ((Number) value).intValue();
        });
        when(result.getShort(anyString())).thenAnswer(invocation -> {
            Object value = data[cursor.get()].get(invocation.getArgument(0, String.class));
            wasNull[0] = value == null;
            return value == null ? (short) 0 : ((Number) value).shortValue();
        });
        when(result.wasNull()).thenAnswer(invocation -> wasNull[0]);
        return result;
    }

    /**
     * Asserts that a public result node is serializable object data.
     *
     * @param value selected GraphQL node
     * @return its string-keyed map representation
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value) {
        assertInstanceOf(Map.class, value);
        return (Map<String, Object>) value;
    }

    /**
     * Asserts the public envelope contains a list rather than an internal
     * source.
     *
     * @param value selected GraphQL node
     * @return its ordered result items
     */
    private static List<?> list(Object value) {
        assertInstanceOf(List.class, value);
        return (List<?>) value;
    }

    /**
     * Reads the first item from a root connection page.
     *
     * @param result successful public envelope
     * @param pageField root page field name
     * @return the first selected connection object
     */
    private static Map<String, Object> firstItem(GraphQLExecutionInfo result, String pageField) {
        return object(list(object(result.getData().get(pageField)).get("items")).get(0));
    }

    /**
     * Reads the nested table page in the tested connection selection.
     *
     * @param result public envelope containing a connection
     * @return selected table page fields
     */
    private static Map<String, Object> tablePage(GraphQLExecutionInfo result) {
        return object(object(result.getData().get("connection")).get("tables"));
    }

    /**
     * Reads the first returned table, excluding discarded JDBC rows.
     *
     * @param result public envelope containing a table page
     * @return selected table fields
     */
    private static Map<String, Object> firstTable(GraphQLExecutionInfo result) {
        return object(list(tablePage(result).get("items")).get(0));
    }
}
