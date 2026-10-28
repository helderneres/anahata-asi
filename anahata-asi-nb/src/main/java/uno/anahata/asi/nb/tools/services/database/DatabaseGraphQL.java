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
 * Selection-driven discovery wiring for IDE registrations and JDBC metadata.
 * <p>
 * Private request sources compose concrete Info values with the IDE-owned handle
 * and complete catalog/schema/table identity needed by selected children. Only
 * selected schema fields become response data. Expensive operations use native
 * field resolvers; attributes already acquired use the native property fetcher.
 * </p>
 * <p>
 * Metadata calls require an active connection and never open a connection dialog
 * or close the IDE connection. Resolver-owned result sets close in their callers.
 * JDBC calls may block and must run outside the Swing event dispatch thread;
 * cooperative checks cannot interrupt a driver blocked inside a metadata method.
 * </p>
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
     * Builds native discovery wiring and appends explicit command wiring.
     * <p>
     * Columns, primary keys, and each relationship direction are independent expensive
     * fields. Column primary-key membership shares one request-local key acquisition
     * with the selected table-level key list.
     * </p>
     *
     * @return executable resolver wiring for a newly initialized GraphQL engine
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
     * Filters and pages the small IDE registration snapshot in memory.
     * <p>
     * Name matching is case-insensitive against sanitized display labels. Results are
     * sorted by display name, nulls first, for this snapshot; registry changes and equal
     * labels do not provide a stable cross-request cursor. No JDBC metadata is loaded.
     * </p>
     *
     * @param environment optional name/state filters and zero-based offset/limit
     * @return a page with the filtered snapshot's known total count
     * @throws SafeGraphQLException if filters, paging, or the request budget are invalid
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
     * Resolves exactly one opaque id or original display name without connecting.
     *
     * @param environment exactly one of the nullable {@code id} or {@code name} arguments
     * @return a safe source retaining the matched IDE-owned registration privately
     * @throws SafeGraphQLException if arguments are invalid, no unique registration
     *     matches, or the cooperative request budget is exhausted
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
     * Associates cheap public attributes with the private IDE registration.
     *
     * @param connection the IDE-owned handle for selected child fetchers
     * @return a request source containing credential-free Info and the private handle
     */
    static ConnectionSource connectionSource(DatabaseConnection connection) {
        return new ConnectionSource(CONNECTION_PROJECTOR.apply(connection), connection);
    }

    /**
     * Copies cheap IDE attributes without requesting JDBC metadata.
     *
     * @param connection the registered connection handle
     * @return public discovery attributes, excluding URL, user, and password fields
     */
    private static DatabaseConnectionInfo connectionInfo(DatabaseConnection connection) {
        JDBCDriver driver = connection.getJDBCDriver();
        return DatabaseConnectionInfo.builder()
                .id(DatabaseConnections.id(connection)).displayName(DatabaseConnections.displayName(connection))
                .driverClass(driver == null ? null : driver.getClassName())
                .schema(connection.getSchema()).connected(connection.getJDBCConnection() != null).build();
    }

    /**
     * Combines selected database/driver descriptors in one bounded operation.
     * <p>
     * The parent selection determines which JDBC getters run. Selecting no descriptor
     * fields returns an empty Info without requesting connection metadata. Unselected
     * or driver-null attributes remain null, rather than implying an acquisition.
     * </p>
     *
     * @param environment selected descriptor fields and the private connection source
     * @return only the acquired metadata descriptors
     * @throws SafeGraphQLException if an active connection, metadata operation,
     *     or request budget is unavailable
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
     * Pushes discovery filters into JDBC and streams a bounded table page.
     * <p>
     * Catalog, schema pattern, table pattern, and types are passed to getTables without
     * rewriting their driver-defined null or wildcard semantics. Offset rows are skipped
     * and one look-ahead row determines continuation. Only returned rows get child
     * sources; total count stays unknown without a complete scan. Ordering across pages
     * depends on the driver and a stable catalog.
     * </p>
     * <p>
     * When neither items nor continuation is selected, paging is returned without JDBC
     * metadata access. Cursor traversal still consumes the request-wide row budget,
     * including skipped and look-ahead rows.
     * </p>
     *
     * @param environment selected page fields, JDBC filters, and parent connection
     * @return bounded items/continuation with a null total count
     * @throws SafeGraphQLException if arguments, identities, driver access, or budgets fail
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
     * Loads bounded columns for the exact parent table without loading keys.
     * <p>
     * Catalog/schema identities use explicit empty qualifiers when absent; literal
     * schema/table names are escaped for JDBC pattern parameters. The caller's column
     * pattern keeps native wildcard semantics. Nullable sizes and YES/NO/unknown flags
     * are retained. Primary keys are acquired only by their separate selected fields.
     * </p>
     *
     * @param environment the complete parent table identity and optional column pattern
     * @return column Info values with private parent identity for conditional PK lookup
     * @throws SafeGraphQLException if identity/escaping, driver access, or budgets fail
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
     * Resolves column membership using the cache shared with table primary keys.
     *
     * @param environment the selected primary-key flag and its private column source
     * @return whether the driver's key components include this column name
     * @throws SafeGraphQLException if the shared key acquisition or request budget fails
     */
    private static boolean columnPrimaryKey(DataFetchingEnvironment environment) {
        ColumnSource column = environment.getSource();
        return primaryKeys(column.getTable(), environment).stream()
                .anyMatch(key -> Objects.equals(key.getColumnName(), column.getInfo().getColumnName()));
    }

    /**
     * Loads one table's key components at most once in the current request.
     * <p>
     * The cache key includes the IDE handle, catalog, schema, and literal table name.
     * Aliases and column membership reuse the same result; the driver sequence number
     * is preserved without imposing a different component order.
     * </p>
     *
     * @param table the parent table and its complete private JDBC identity
     * @param environment the execution-local cache and operation budget
     * @return an immutable list of reported primary-key components
     * @throws SafeGraphQLException if identity, driver access, or budgets fail
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
     * Loads only the selected foreign-key direction for an exact parent table.
     * <p>
     * Imported keys describe foreign keys on the current table referencing another
     * primary-key table. Exported keys describe foreign keys on other tables referencing
     * the current primary-key table. Every returned component retains both identities
     * and the driver's sequence/rule values; the other direction is not queried.
     * </p>
     *
     * @param environment the private table source and request budget
     * @param imported {@code true} for imported keys, {@code false} for exported keys
     * @return bounded relationship components for the requested direction
     * @throws SafeGraphQLException if identity, driver access, or budgets fail
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
     * Obtains and caches metadata from an already-active IDE connection.
     * <p>
     * The first access acquires the JDBC metadata object; later selected fields share
     * it only within this request. The registration is never implicitly connected and
     * neither the JDBC connection nor this metadata object is owned by the resolver.
     * </p>
     *
     * @param source the private IDE registration
     * @param environment the execution-local cache and budget
     * @return shared metadata for this registration within the current request
     * @throws SafeGraphQLException if the connection is inactive or metadata/budgets fail
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
     * Reserves one operation unit around a group of JDBC metadata reads.
     * <p>
     * The supplied operation is checked before and after execution. SQLExceptions are
     * logged locally and replaced with an authored safe field failure. This wrapper
     * does not impose a hard driver timeout or close resources owned by the operation.
     * </p>
     *
     * @param <T> the extracted metadata result type
     * @param environment cooperative time and operation budgets
     * @param operation the JDBC reads whose owning caller closes any opened result set
     * @return the extracted result
     * @throws SafeGraphQLException if the driver fails or a request budget is exhausted
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
     * Validates zero-based paging before any result set is opened.
     *
     * @param environment offset and limit; null uses zero and 25 respectively
     * @return a page request with offset 0..10000 and limit 1..100
     * @throws SafeGraphQLException if either inclusive range is violated
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
     * Bounds a discovery filter without normalizing driver wildcard semantics.
     *
     * @param value an optional JDBC pattern, qualifier, or connection-name filter
     * @return the unchanged value, including null or an empty string
     * @throws SafeGraphQLException if the filter exceeds 1024 UTF-16 code units
     */
    private static String filter(String value) {
        if (value != null && value.length() > MAX_FILTER_LENGTH) {
            throw new SafeGraphQLException("Metadata filters must contain at most " + MAX_FILTER_LENGTH + " characters.");
        }
        return value;
    }

    /**
     * Requires a literal driver-reported table name before child discovery.
     * <p>
     * A missing name is a field failure, not an unconstrained JDBC lookup. Catalog and
     * schema may remain null in the Info; child callers translate absent qualifiers
     * with {@link #exactQualifier(String)} before invoking JDBC.
     * </p>
     *
     * @param table the private source produced from a returned table row
     * @return its identity with a non-null table name and possibly absent qualifiers
     * @throws SafeGraphQLException if the driver did not report a table name
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
     * Distinguishes an absent parent qualifier from unconstrained JDBC null.
     *
     * @param qualifier the catalog/schema reported by the driver, possibly null
     * @return an empty no-catalog/no-schema qualifier for null, or the unchanged value
     */
    private static String exactQualifier(String qualifier) {
        return qualifier == null ? "" : qualifier;
    }

    /**
     * Escapes literal schema/table names for JDBC pattern parameters.
     *
     * @param name a non-null literal identity reported by table discovery
     * @param escape the driver search escape, possibly null or empty
     * @return a pattern matching the literal identity without intentional wildcards
     * @throws SafeGraphQLException if wildcard characters require unsupported escaping
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
     * Advances resolver-owned metadata rows with cooperative budget checks.
     * <p>
     * Every existing row consumes the request-wide row ceiling, including rows later
     * skipped by pagination. This does not preempt a driver blocked in next().
     * </p>
     *
     * @param rows the resolver-owned metadata cursor
     * @param environment the request deadline and shared row counter
     * @return whether the cursor reached another row
     * @throws SQLException if the driver cannot advance
     * @throws SafeGraphQLException if time, interruption, or row budgets are exhausted
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
     * Rejects an oversized child collection instead of silently truncating it.
     *
     * @param size the number of child rows already collected
     * @throws SafeGraphQLException if another row would exceed the 1000-row ceiling
     */
    private static void checkChildCapacity(int size) {
        if (size >= MAX_CHILD_ROWS) {
            throw new SafeGraphQLException("Selected table metadata exceeds " + MAX_CHILD_ROWS + " rows. Narrow the table selection.");
        }
    }

    /**
     * Preserves SQL NULL instead of the primitive JDBC getter's zero default.
     *
     * @param rows the metadata cursor positioned at its current row
     * @param column the integer-valued JDBC metadata column name
     * @return the reported integer, or null when wasNull() reports SQL NULL
     * @throws SQLException if the column cannot be read
     */
    private static Integer nullableInteger(ResultSet rows, String column) throws SQLException {
        int value = rows.getInt(column);
        return rows.wasNull() ? null : value;
    }

    /**
     * Maps JDBC YES/NO text to a nullable Boolean.
     *
     * @param value driver text, possibly null, empty, or unrecognized
     * @return true for YES, false for NO ignoring case, or null for unknown text
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
     * Supplies a checked group of JDBC metadata reads to the budget wrapper.
     *
     * @param <T> the extracted result type
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
     * Composes concrete public Info with a private resolver source.
     *
     * @param <T> the public attribute container read by the native property fetcher
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
     * Associates a public connection Info with its request-local IDE handle.
     * <p>
     * The handle is not a schema output property and is never closed by discovery.
     * </p>
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
     * Carries a returned page item's complete identity and private parent registration.
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

    /**
     * Carries column attributes and the table identity used only for selected PK lookup.
     */
    @Getter
    private static final class ColumnSource extends InfoSource<ColumnInfo> {
        /**
         * Parent source used to share exact-table primary-key metadata within the request.
         */
        private final TableSource table;

        /**
         * Associates acquired column properties with the exact parent table.
         *
         * @param info acquired nullable column attributes
         * @param table the private table source needed for conditional key membership
         */
        private ColumnSource(ColumnInfo info, TableSource table) {
            super(info);
            this.table = table;
        }
    }

    /**
     * Validated immutable zero-based page values used before opening any cursor.
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
     * Tracks traversed metadata rows across selected parents, children, and aliases.
     */
    private static final class MetadataRowBudget {

        /**
         * Traversed metadata rows across all parent and child ResultSets.
         */
        private final AtomicInteger rows = new AtomicInteger();

        /**
         * Reserves one existing metadata row before extracting its attributes.
         *
         * @throws SafeGraphQLException if the 10000-row execution ceiling is exceeded
         */
        private void consume() {
            if (rows.incrementAndGet() > MAX_REQUEST_ROWS) {
                throw new SafeGraphQLException("LimitExceeded", "GraphQL metadata row limit reached; reduce offsets, page size, or nested fields.");
            }
        }
    }

    /**
     * Type-distinct request cache key for one IDE registration's metadata object.
     */
    @Value
    private static class MetadataCacheKey {

        /**
         * IDE-owned handle identity; never used as a public GraphQL value.
         */
        DatabaseConnection connection;
    }

    /**
     * Complete request-local key preventing PK collisions across catalogs and schemas.
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
