/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.nb.tools.services.database;

import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingEnvironmentImpl;
import graphql.schema.PropertyDataFetcher;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.TypeRuntimeWiring;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.netbeans.api.db.explorer.ConnectionManager;
import org.netbeans.api.db.explorer.DatabaseConnection;
import org.netbeans.api.db.explorer.JDBCDriver;
import uno.anahata.asi.agi.tool.graphql.GraphQLRequestContext;
import uno.anahata.asi.agi.tool.graphql.GraphQLToolkit;
import uno.anahata.asi.agi.tool.graphql.SafeGraphQLException;
import uno.anahata.asi.agi.tool.query.FilterMatcher;
import uno.anahata.asi.agi.tool.query.ToolFilter;
import uno.anahata.asi.agi.tool.query.ToolProjector;

/**
 * Selection-driven NetBeans/JDBC wiring for the Database toolkit.
 * <p>
 * Sources are request-local wrappers: only their concrete Info values can reach
 * GraphQL output. NetBeans-owned connections are never closed here. Independent
 * metadata operations are field fetchers; existing Info attributes use the
 * native property fetcher. Pagination stops after one look-ahead row.</p>
 *
 * @author anahata
 */
@Slf4j
final class DatabaseGraphQL {

    /**
     * Public discovery contract, intentionally excluding URLs and credentials.
     */
    static final String SCHEMA = """
            type Query {
              connections(nameContains: String, connected: Boolean, offset: Int = 0, limit: Int = 25): DatabaseConnectionPage!
              connection(name: String, id: ID): DatabaseConnectionInfo
            }
            type DatabaseConnectionPage {
              items: [DatabaseConnectionInfo!]!
              offset: Int!
              limit: Int!
              hasNextPage: Boolean!
              totalCount: Int
            }
            type DatabaseConnectionInfo {
              id: ID!
              displayName: String
              driverClass: String
              schema: String
              connected: Boolean!
              metadata: DatabaseMetadataInfo
              tables(catalog: String, schema: String, tableNamePattern: String = "%", types: [String!] = ["TABLE", "VIEW"], offset: Int = 0, limit: Int = 25): TablePage
            }
            type DatabaseMetadataInfo {
              databaseProductName: String
              databaseProductVersion: String
              driverName: String
              driverVersion: String
            }
            type TablePage {
              items: [TableInfo!]!
              offset: Int!
              limit: Int!
              hasNextPage: Boolean!
              totalCount: Int
            }
            type TableInfo {
              catalog: String
              schema: String
              tableName: String
              tableType: String
              remarks: String
              columns(columnNamePattern: String = "%"): [ColumnInfo!]
              primaryKeys: [PrimaryKeyInfo!]
              importedRelationships: [TableRelationshipInfo!]
              exportedRelationships: [TableRelationshipInfo!]
            }
            type ColumnInfo {
              columnName: String
              dataType: String
              columnSize: Int
              nullable: Boolean
              primaryKey: Boolean
              autoIncrement: Boolean
            }
            type PrimaryKeyInfo {
              catalog: String
              schema: String
              tableName: String
              columnName: String
              keySequence: Int
              keyName: String
            }
            type TableRelationshipInfo {
              primaryKeyCatalog: String
              primaryKeySchema: String
              primaryKeyTable: String
              primaryKeyColumn: String
              foreignKeyCatalog: String
              foreignKeySchema: String
              foreignKeyTable: String
              foreignKeyColumn: String
              keySequence: Int
              updateRule: Int
              deleteRule: Int
              foreignKeyName: String
              primaryKeyName: String
              deferrability: Int
            }
            """ + DatabaseCommands.SCHEMA;

    /**
     * Maximum skipped rows, preventing an unbounded JDBC offset scan.
     */
    private static final int MAX_OFFSET = 10_000;
    /**
     * Per-child row ceiling; excess metadata reports an error without
     * truncating.
     */
    private static final int MAX_CHILD_ROWS = 1_000;
    /**
     * Total metadata rows traversed per request, including skipped rows.
     */
    private static final int MAX_REQUEST_ROWS = 10_000;
    /**
     * Maximum length accepted for individual JDBC filters.
     */
    private static final int MAX_FILTER_LENGTH = 1_024;
    /**
     * Maximum number of table type filters passed to a JDBC driver.
     */
    private static final int MAX_TABLE_TYPES = 16;
    /**
     * Typed conversion of an IDE handle to safe, credential-free attributes.
     */
    private static final ToolProjector<DatabaseConnection, DatabaseConnectionInfo> CONNECTION_PROJECTOR = DatabaseGraphQL::connectionInfo;

    /**
     * Utility class: no toolkit or persisted state is held in this wiring.
     */
    private DatabaseGraphQL() {
    }

    /**
     * Builds operation fetchers while delegating ordinary Info fields to
     * getters.
     *
     * @return the immutable schema wiring for a newly initialized engine
     */
    static RuntimeWiring buildRuntimeWiring() {
        return DatabaseCommands.wire(RuntimeWiring.newRuntimeWiring()
                .type(TypeRuntimeWiring.newTypeWiring("Query")
                        .dataFetcher("connections", DatabaseGraphQL::connections)
                        .dataFetcher("connection", DatabaseGraphQL::connection))
                .type(TypeRuntimeWiring.newTypeWiring("DatabaseConnectionInfo")
                        .defaultDataFetcher(DatabaseGraphQL::infoField)
                        .dataFetcher("metadata", DatabaseGraphQL::connectionMetadata)
                        .dataFetcher("tables", DatabaseGraphQL::tables))
                .type(TypeRuntimeWiring.newTypeWiring("TableInfo")
                        .defaultDataFetcher(DatabaseGraphQL::infoField)
                        .dataFetcher("columns", DatabaseGraphQL::columns)
                        .dataFetcher("primaryKeys", environment -> primaryKeys(environment.getSource(), environment))
                        .dataFetcher("importedRelationships", environment -> relationships(environment, true))
                        .dataFetcher("exportedRelationships", environment -> relationships(environment, false)))
                .type(TypeRuntimeWiring.newTypeWiring("ColumnInfo")
                        .defaultDataFetcher(DatabaseGraphQL::infoField)
                        .dataFetcher("primaryKey", DatabaseGraphQL::columnPrimaryKey))
        ).build();
    }

    /**
     * Resolves already-obtained DTO fields without fetching additional
     * metadata.
     *
     * @param environment the selected scalar field and private source
     * @return the corresponding public Info property
     * @throws Exception if the native property fetcher cannot read the property
     */
    private static Object infoField(DataFetchingEnvironment environment) throws Exception {
        InfoSource<?> source = environment.getSource();
        return PropertyDataFetcher.fetching(environment.getFieldDefinition().getName())
                .get(DataFetchingEnvironmentImpl.newDataFetchingEnvironment(environment).source(source.getInfo()).build());
    }

    /**
     * Filters the small registered-connection snapshot before paginating it.
     *
     * @param environment connection filters and bounded page arguments
     * @return a stable-by-display-name in-memory page without credentials
     */
    private static MetadataPageInfo<ConnectionSource> connections(DataFetchingEnvironment environment) {
        PageArguments page = pageArguments(environment);
        String nameContains = filter(environment.getArgument("nameContains"));
        Boolean connected = environment.getArgument("connected");
        Predicate<DatabaseConnection> nameMatches = new ToolFilter<>(DatabaseConnections::displayName,
                FilterMatcher.containsIgnoreCase()).predicate(nameContains);
        GraphQLRequestContext.from(environment).checkBudget();
        DatabaseConnection[] registered = ConnectionManager.getDefault().getConnections();
        List<ConnectionSource> filtered = new ArrayList<>();
        if (registered != null) {
            for (DatabaseConnection connection : registered) {
                GraphQLRequestContext.from(environment).checkBudget();
                if (nameContains != null && !nameMatches.test(connection)) {
                    continue;
                }
                boolean active = connection.getJDBCConnection() != null;
                if (connected == null || connected == active) {
                    filtered.add(connectionSource(connection));
                }
            }
        }
        filtered.sort(Comparator.comparing(source -> source.getInfo().getDisplayName(), Comparator.nullsFirst(Comparator.naturalOrder())));
        int start = Math.min(page.getOffset(), filtered.size());
        int end = start + Math.min(page.getLimit(), filtered.size() - start);
        return MetadataPageInfo.<ConnectionSource>builder()
                .items(List.copyOf(filtered.subList(start, end)))
                .offset(page.getOffset()).limit(page.getLimit())
                .hasNextPage(end < filtered.size()).totalCount(filtered.size()).build();
    }

    /**
     * Resolves an exact, unambiguous display name without connecting the
     * database.
     *
     * @param environment required display name
     * @return a safe source, or null when no registered connection matches
     */
    private static ConnectionSource connection(DataFetchingEnvironment environment) {
        String name = environment.getArgument("name");
        String id = environment.getArgument("id");
        if ((name == null) == (id == null)) {
            throw new SafeGraphQLException("InvalidArgument", "Specify exactly one connection id or name.");
        }
        GraphQLRequestContext.from(environment).checkBudget();
        return connectionSource(DatabaseConnections.find(id == null ? name : id));
    }

    /**
     * Copies only connection attributes suitable for discovery payloads.
     *
     * @param connection the IDE-owned handle retained privately for child
     * fetchers
     * @return safe Info attributes and their private handle
     */
    static ConnectionSource connectionSource(DatabaseConnection connection) {
        return new ConnectionSource(CONNECTION_PROJECTOR.apply(connection), connection);
    }

    /**
     * Projects cheap IDE attributes without exposing URL, user, or credentials.
     *
     * @param connection the registered connection handle
     * @return concrete attributes suitable for explicit discovery
     */
    private static DatabaseConnectionInfo connectionInfo(DatabaseConnection connection) {
        JDBCDriver driver = connection.getJDBCDriver();
        return DatabaseConnectionInfo.builder()
                .id(DatabaseConnections.id(connection)).displayName(DatabaseConnections.displayName(connection))
                .driverClass(driver == null ? null : driver.getClassName())
                .schema(connection.getSchema()).connected(connection.getJDBCConnection() != null).build();
    }

    /**
     * Pushes down a combined metadata selection to the necessary JDBC calls
     * only.
     *
     * @param environment selected metadata scalars
     * @return populated selected fields, leaving unselected properties
     * untouched
     */
    private static DatabaseMetadataInfo connectionMetadata(DataFetchingEnvironment environment) {
        ConnectionSource source = environment.getSource();
        if (!environment.getSelectionSet().contains("databaseProductName")
                && !environment.getSelectionSet().contains("databaseProductVersion")
                && !environment.getSelectionSet().contains("driverName")
                && !environment.getSelectionSet().contains("driverVersion")) {
            return DatabaseMetadataInfo.builder().build();
        }
        DatabaseMetaData metadata = metadata(source, environment);
        return metadataOperation(environment, () -> {
            DatabaseMetadataInfo.DatabaseMetadataInfoBuilder builder = DatabaseMetadataInfo.builder();
            if (environment.getSelectionSet().contains("databaseProductName")) {
                builder.databaseProductName(metadata.getDatabaseProductName());
            }
            if (environment.getSelectionSet().contains("databaseProductVersion")) {
                builder.databaseProductVersion(metadata.getDatabaseProductVersion());
            }
            if (environment.getSelectionSet().contains("driverName")) {
                builder.driverName(metadata.getDriverName());
            }
            if (environment.getSelectionSet().contains("driverVersion")) {
                builder.driverVersion(metadata.getDriverVersion());
            }
            return builder.build();
        });
    }

    /**
     * Passes JDBC filters directly and reads at most offset plus limit plus
     * one. Child sources exist only for returned rows, so discarded tables do
     * no work.
     *
     * @param environment selected table page and JDBC filters
     * @return a bounded page with a nullable count
     */
    private static MetadataPageInfo<TableSource> tables(DataFetchingEnvironment environment) {
        ConnectionSource connection = environment.getSource();
        PageArguments page = pageArguments(environment);
        String catalog = filter(environment.getArgument("catalog"));
        String schema = filter(environment.getArgument("schema"));
        String pattern = filter(environment.getArgument("tableNamePattern"));
        List<String> types = environment.getArgument("types");
        if (types != null && types.size() > MAX_TABLE_TYPES) {
            throw new SafeGraphQLException("At most " + MAX_TABLE_TYPES + " table types can be requested.");
        }
        if (types != null) {
            types.forEach(DatabaseGraphQL::filter);
        }
        boolean selectedItems = environment.getSelectionSet().contains("items");
        if (!selectedItems && !environment.getSelectionSet().contains("hasNextPage")) {
            return MetadataPageInfo.<TableSource>builder().items(List.of()).offset(page.getOffset()).limit(page.getLimit())
                    .hasNextPage(false).totalCount(null).build();
        }
        DatabaseMetaData metadata = metadata(connection, environment);
        return metadataOperation(environment, () -> {
            List<TableSource> items = new ArrayList<>();
            boolean hasNext = false;
            try (ResultSet rows = metadata.getTables(catalog, schema, pattern, types == null ? null : types.toArray(String[]::new))) {
                int skipped = 0;
                int returned = 0;
                while (next(rows, environment)) {
                    if (skipped < page.getOffset()) {
                        skipped++;
                        continue;
                    }
                    if (returned == page.getLimit()) {
                        hasNext = true;
                        break;
                    }
                    returned++;
                    if (selectedItems) {
                        TableInfo info = TableInfo.builder()
                                .catalog(rows.getString("TABLE_CAT")).schema(rows.getString("TABLE_SCHEM"))
                                .tableName(rows.getString("TABLE_NAME")).tableType(rows.getString("TABLE_TYPE"))
                                .remarks(rows.getString("REMARKS")).build();
                        items.add(new TableSource(info, connection));
                    }
                }
            }
            return MetadataPageInfo.<TableSource>builder().items(items).offset(page.getOffset()).limit(page.getLimit())
                    .hasNextPage(hasNext).totalCount(null).build();
        });
    }

    /**
     * Loads columns without resolving primary keys unless their field is
     * selected.
     *
     * @param environment exact parent table identity
     * @return bounded column sources carrying the same private parent identity
     */
    private static List<ColumnSource> columns(DataFetchingEnvironment environment) {
        TableSource table = environment.getSource();
        TableInfo identity = tableIdentity(table);
        DatabaseMetaData metadata = metadata(table.getConnection(), environment);
        String columnPattern = filter(environment.getArgument("columnNamePattern"));
        return metadataOperation(environment, () -> {
            List<ColumnSource> columns = new ArrayList<>();
            String escape = metadata.getSearchStringEscape();
            String pattern = exactNamePattern(identity.getTableName(), escape);
            String schemaPattern = exactNamePattern(exactQualifier(identity.getSchema()), escape);
            try (ResultSet rows = metadata.getColumns(exactQualifier(identity.getCatalog()), schemaPattern, pattern, columnPattern)) {
                while (next(rows, environment)) {
                    checkChildCapacity(columns.size());
                    Integer size = nullableInteger(rows, "COLUMN_SIZE");
                    Boolean nullable = jdbcBoolean(rows.getString("IS_NULLABLE"));
                    Boolean autoIncrement = jdbcBoolean(rows.getString("IS_AUTOINCREMENT"));
                    ColumnInfo info = ColumnInfo.builder().columnName(rows.getString("COLUMN_NAME"))
                            .dataType(rows.getString("TYPE_NAME")).columnSize(size)
                            .nullable(nullable).autoIncrement(autoIncrement).build();
                    columns.add(new ColumnSource(info, table));
                }
            }
            return columns;
        });
    }

    /**
     * Resolves column membership using a cache shared with Table.primaryKeys.
     *
     * @param environment selected primary-key flag and its column source
     * @return whether this exact table's reported key includes the column
     */
    private static boolean columnPrimaryKey(DataFetchingEnvironment environment) {
        ColumnSource column = environment.getSource();
        return primaryKeys(column.getTable(), environment).stream()
                .anyMatch(key -> Objects.equals(key.getColumnName(), column.getInfo().getColumnName()));
    }

    /**
     * Shares complete primary-key metadata across aliases and column
     * membership.
     *
     * @param table parent table with complete JDBC identity
     * @param environment request cache and operation budget
     * @return immutable key components for this request only
     */
    private static List<PrimaryKeyInfo> primaryKeys(TableSource table, DataFetchingEnvironment environment) {
        TableInfo identity = tableIdentity(table);
        TableIdentity cacheKey = new TableIdentity(table.getConnection().getDatabaseConnection(),
                identity.getCatalog(), identity.getSchema(), identity.getTableName());
        return GraphQLRequestContext.from(environment).getOrCompute(cacheKey, () -> {
            DatabaseMetaData metadata = metadata(table.getConnection(), environment);
            return metadataOperation(environment, () -> {
                List<PrimaryKeyInfo> keys = new ArrayList<>();
                try (ResultSet rows = metadata.getPrimaryKeys(exactQualifier(identity.getCatalog()), exactQualifier(identity.getSchema()), identity.getTableName())) {
                    while (next(rows, environment)) {
                        checkChildCapacity(keys.size());
                        keys.add(PrimaryKeyInfo.builder().catalog(rows.getString("TABLE_CAT"))
                                .schema(rows.getString("TABLE_SCHEM")).tableName(rows.getString("TABLE_NAME"))
                                .columnName(rows.getString("COLUMN_NAME")).keySequence(nullableInteger(rows, "KEY_SEQ"))
                                .keyName(rows.getString("PK_NAME")).build());
                    }
                }
                return List.copyOf(keys);
            });
        });
    }

    /**
     * Fetches only the selected direction of foreign-key relationships.
     *
     * @param environment the complete source identity and request budget
     * @param imported whether to fetch referencing or referenced relationships
     * @return bounded JDBC relationship components with both table identities
     */
    private static List<TableRelationshipInfo> relationships(DataFetchingEnvironment environment, boolean imported) {
        TableSource table = environment.getSource();
        TableInfo identity = tableIdentity(table);
        DatabaseMetaData metadata = metadata(table.getConnection(), environment);
        return metadataOperation(environment, () -> {
            List<TableRelationshipInfo> relationships = new ArrayList<>();
            try (ResultSet rows = imported
                    ? metadata.getImportedKeys(exactQualifier(identity.getCatalog()), exactQualifier(identity.getSchema()), identity.getTableName())
                    : metadata.getExportedKeys(exactQualifier(identity.getCatalog()), exactQualifier(identity.getSchema()), identity.getTableName())) {
                while (next(rows, environment)) {
                    checkChildCapacity(relationships.size());
                    relationships.add(TableRelationshipInfo.builder()
                            .primaryKeyCatalog(rows.getString("PKTABLE_CAT")).primaryKeySchema(rows.getString("PKTABLE_SCHEM"))
                            .primaryKeyTable(rows.getString("PKTABLE_NAME")).primaryKeyColumn(rows.getString("PKCOLUMN_NAME"))
                            .foreignKeyCatalog(rows.getString("FKTABLE_CAT")).foreignKeySchema(rows.getString("FKTABLE_SCHEM"))
                            .foreignKeyTable(rows.getString("FKTABLE_NAME")).foreignKeyColumn(rows.getString("FKCOLUMN_NAME"))
                            .keySequence(nullableInteger(rows, "KEY_SEQ")).updateRule(nullableInteger(rows, "UPDATE_RULE"))
                            .deleteRule(nullableInteger(rows, "DELETE_RULE")).foreignKeyName(rows.getString("FK_NAME"))
                            .primaryKeyName(rows.getString("PK_NAME")).deferrability(nullableInteger(rows, "DEFERRABILITY")).build());
                }
            }
            return relationships;
        });
    }

    /**
     * Obtains metadata from an already-active IDE connection without a dialog.
     *
     * @param source private IDE handle
     * @param environment request-local metadata cache
     * @return shared metadata for this connection within the request
     */
    private static DatabaseMetaData metadata(ConnectionSource source, DataFetchingEnvironment environment) {
        MetadataCacheKey key = new MetadataCacheKey(source.getDatabaseConnection());
        return GraphQLRequestContext.from(environment).getOrCompute(key, () -> {
            Connection connection = source.getDatabaseConnection().getJDBCConnection();
            if (connection == null) {
                throw new SafeGraphQLException("ConnectionInactive", "Connection is inactive. Connect explicitly in the IDE Services tab or with mutate() connect before querying metadata.");
            }
            return metadataOperation(environment, connection::getMetaData);
        });
    }

    /**
     * Accounts for JDBC operations and converts driver errors to safe messages.
     *
     * @param <T> the returned metadata shape
     * @param environment the cooperative time and operation budget
     * @param operation a read-only JDBC metadata operation
     * @return the operation result
     */
    private static <T> T metadataOperation(DataFetchingEnvironment environment, SqlSupplier<T> operation) {
        GraphQLRequestContext context = GraphQLRequestContext.from(environment);
        context.consumeMetadataOperation();
        try {
            T result = operation.get();
            context.checkBudget();
            return result;
        } catch (SQLException exception) {
            log.warn("JDBC metadata discovery failed; detailed error retained in the local IDE log.", exception);
            throw new SafeGraphQLException("DatabaseMetadataFailure", "Database metadata is unavailable. Check the IDE connection, driver support, and database permissions.");
        }
    }

    /**
     * Validates paging independently of GraphQL validation for runtime
     * variables.
     *
     * @param environment offset and limit arguments
     * @return a bounded immutable page request
     */
    private static PageArguments pageArguments(DataFetchingEnvironment environment) {
        Integer offset = environment.getArgument("offset");
        Integer limit = environment.getArgument("limit");
        int requestedOffset = offset == null ? 0 : offset;
        int requestedLimit = limit == null ? GraphQLToolkit.DEFAULT_PAGE_LIMIT : limit;
        if (requestedOffset < 0 || requestedOffset > MAX_OFFSET) {
            throw new SafeGraphQLException("InvalidArgument", "offset must be between 0 and " + MAX_OFFSET + ".");
        }
        if (requestedLimit < 1 || requestedLimit > GraphQLToolkit.MAX_PAGE_LIMIT) {
            throw new SafeGraphQLException("InvalidArgument", "limit must be between 1 and " + GraphQLToolkit.MAX_PAGE_LIMIT + ".");
        }
        return new PageArguments(requestedOffset, requestedLimit);
    }

    /**
     * Bounds individual filters before passing them to driver metadata methods.
     *
     * @param value a JDBC pattern, identity, or connection-name filter
     * @return the original value without modifying JDBC wildcard semantics
     */
    private static String filter(String value) {
        if (value != null && value.length() > MAX_FILTER_LENGTH) {
            throw new SafeGraphQLException("Metadata filters must contain at most " + MAX_FILTER_LENGTH + " characters.");
        }
        return value;
    }

    /**
     * Requires a literal table name before any exact-table child metadata call.
     * JDBC null patterns can broaden discovery, so a missing driver identity is
     * reported as a partial field failure instead of being forwarded to JDBC.
     *
     * @param table the table source reported by getTables
     * @return its validated complete identity, retaining nullable qualifiers
     */
    private static TableInfo tableIdentity(TableSource table) {
        TableInfo identity = table.getInfo();
        if (identity.getTableName() == null) {
            throw new SafeGraphQLException("MetadataIdentityUnavailable",
                    "The JDBC driver did not report a table name; child metadata cannot be resolved reliably.");
        }
        return identity;
    }

    /**
     * Distinguishes an absent catalog/schema from JDBC's unconstrained null.
     *
     * @param qualifier the table identity reported by the driver
     * @return the exact no-catalog/no-schema sentinel or reported identity
     */
    private static String exactQualifier(String qualifier) {
        return qualifier == null ? "" : qualifier;
    }

    /**
     * Escapes literal schema/table identities for JDBC pattern parameters.
     *
     * @param name the complete identity reported by getTables
     * @param escape the driver's native search escape, possibly unsupported
     * @return an exact pattern, or an actionable error for unsupported escaping
     */
    private static String exactNamePattern(String name, String escape) {
        if (escape == null || escape.isEmpty()) {
            if (name.contains("%") || name.contains("_")) {
                throw new SafeGraphQLException("The JDBC driver does not support escaping this table identity for column discovery.");
            }
            return name;
        }
        return name.replace(escape, escape + escape).replace("%", escape + "%").replace("_", escape + "_");
    }

    /**
     * Advances a ResultSet with cooperative deadline checks around the driver.
     *
     * @param rows the resolver-owned metadata rows
     * @param environment current request deadline
     * @return whether a next row exists
     * @throws SQLException if the JDBC driver cannot advance
     */
    private static boolean next(ResultSet rows, DataFetchingEnvironment environment) throws SQLException {
        GraphQLRequestContext context = GraphQLRequestContext.from(environment);
        context.checkBudget();
        boolean hasNext = rows.next();
        if (hasNext) {
            context.getOrCompute(MetadataRowBudget.class, MetadataRowBudget::new).consume();
        }
        context.checkBudget();
        return hasNext;
    }

    /**
     * Fails explicitly instead of returning a silently incomplete child list.
     *
     * @param size the number of already-collected child metadata rows
     */
    private static void checkChildCapacity(int size) {
        if (size >= MAX_CHILD_ROWS) {
            throw new SafeGraphQLException("Selected table metadata exceeds " + MAX_CHILD_ROWS + " rows. Narrow the table selection.");
        }
    }

    /**
     * Preserves JDBC SQL NULL instead of the primitive getter's zero default.
     *
     * @param rows current metadata row
     * @param column integer-valued JDBC metadata column
     * @return the nullable integer value
     * @throws SQLException if the column cannot be read
     */
    private static Integer nullableInteger(ResultSet rows, String column) throws SQLException {
        int value = rows.getInt(column);
        return rows.wasNull() ? null : value;
    }

    /**
     * Preserves unspecified or driver-specific boolean text as an unknown
     * value.
     *
     * @param value the JDBC YES/NO representation, possibly empty or null
     * @return true, false, or unknown
     */
    private static Boolean jdbcBoolean(String value) {
        if ("YES".equalsIgnoreCase(value)) {
            return true;
        }
        if ("NO".equalsIgnoreCase(value)) {
            return false;
        }
        return null;
    }

    /**
     * A checked supplier restricted to JDBC metadata reads.
     */
    @FunctionalInterface
    private interface SqlSupplier<T> {

        /**
         * Performs one bounded metadata operation.
         *
         * @return extracted metadata
         * @throws SQLException if the driver cannot complete the operation
         */
        T get() throws SQLException;
    }

    /**
     * Private source composition retains concrete DTOs as the public model.
     */
    @Getter
    @RequiredArgsConstructor
    private static class InfoSource<T> {

        /**
         * DTO properties consumed by the native GraphQL property fetcher.
         */
        private final T info;
    }

    /**
     * A registered IDE handle that is never a GraphQL output field.
     */
    @Getter
    static final class ConnectionSource extends InfoSource<DatabaseConnectionInfo> {

        /**
         * IDE-owned connection identity for lazy metadata access.
         */
        private final DatabaseConnection databaseConnection;

        /**
         * Associates safe public attributes with a private connection handle.
         *
         * @param info safe discovered attributes
         * @param databaseConnection the IDE handle, retained only for this
         * request
         */
        private ConnectionSource(DatabaseConnectionInfo info, DatabaseConnection databaseConnection) {
            super(info);
            this.databaseConnection = databaseConnection;
        }
    }

    /**
     * A paginated table and the connection required by selected child fields.
     */
    @Getter
    private static final class TableSource extends InfoSource<TableInfo> {

        /**
         * The connection that reported this complete table identity.
         */
        private final ConnectionSource connection;

        /**
         * Associates a returned table with its private owning connection.
         *
         * @param info complete catalog/schema/table identity
         * @param connection the parent request source
         */
        private TableSource(TableInfo info, ConnectionSource connection) {
            super(info);
            this.connection = connection;
        }
    }

    /** Column attributes and the private identity needed only for selected PK lookup. */
    @Getter
    private static final class ColumnSource extends InfoSource<ColumnInfo> {
        private final TableSource table;

        private ColumnSource(ColumnInfo info, TableSource table) {
            super(info);
            this.table = table;
        }
    }

    /**
     * Validated pagination values, used before opening any ResultSet.
     */
    @Value
    private static class PageArguments {

        /**
         * Zero-based skipped row count.
         */
        int offset;
        /**
         * Maximum returned item count.
         */
        int limit;
    }

    /**
     * Shared request-local row ceiling, independent of the number of fields.
     */
    private static final class MetadataRowBudget {

        /**
         * Traversed metadata rows across all parent and child ResultSets.
         */
        private final AtomicInteger rows = new AtomicInteger();

        /**
         * Reserves one row before extracting any attributes from it.
         */
        private void consume() {
            if (rows.incrementAndGet() > MAX_REQUEST_ROWS) {
                throw new SafeGraphQLException("LimitExceeded", "GraphQL metadata row limit reached; reduce offsets, page size, or nested fields.");
            }
        }
    }

    /**
     * Type-distinct cache key for the metadata of one registered connection.
     */
    @Value
    private static class MetadataCacheKey {

        /**
         * IDE-owned handle identity; never used as a public GraphQL value.
         */
        DatabaseConnection connection;
    }

    /**
     * Complete identity prevents PK collisions across catalogs or schemas.
     */
    @Value
    private static class TableIdentity {

        /**
         * IDE-owned connection that reported the table.
         */
        DatabaseConnection connection;
        /**
         * Catalog reported by the JDBC driver.
         */
        String catalog;
        /**
         * Schema reported by the JDBC driver.
         */
        String schema;
        /**
         * Literal table name reported by the JDBC driver.
         */
        String tableName;
    }
}
