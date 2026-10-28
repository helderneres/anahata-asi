/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.nb.tools.services.database;

import graphql.analysis.FieldComplexityEnvironment;
import java.util.List;
import java.util.Map;
import org.netbeans.api.db.explorer.ConnectionListener;
import org.netbeans.api.db.explorer.ConnectionManager;
import org.netbeans.api.db.explorer.DatabaseConnection;
import uno.anahata.asi.agi.message.RagMessage;
import uno.anahata.asi.agi.tool.AgiTool;
import uno.anahata.asi.agi.tool.AgiToolParam;
import uno.anahata.asi.agi.tool.graphql.GraphQLExecutionInfo;
import uno.anahata.asi.agi.tool.graphql.GraphQLFieldInfo;
import uno.anahata.asi.agi.tool.graphql.GraphQLRequestContext;
import uno.anahata.asi.agi.tool.AgiToolkit;
import graphql.schema.idl.RuntimeWiring;
import uno.anahata.asi.agi.tool.graphql.GraphQLToolkit;

import lombok.extern.slf4j.Slf4j;

/**
 * NetBeans Database Explorer toolkit exposing an embedded GraphQL contract.
 * <p>
 * The inherited schema and query tools describe and resolve selected metadata;
 * {@link #mutate(String, Map)} performs explicit connection or raw SQL
 * commands governed by the tool permission. No HTTP endpoint is created. JDBC and IDE handles are retained in
 * private resolver sources rather than public response values.
 * </p>
 * <p>
 * Initialization and rebinding build the engine before registering this instance
 * as a connection listener. Metadata and SQL may block in a JDBC driver and must
 * be invoked outside the Swing event dispatch thread. Serial fields within one
 * Mutation neither serialize independent calls nor create a shared transaction.
 * </p>
 *
 * @author anahata
 */
@Slf4j
@AgiToolkit("Provides database connections, schema inspection, and SQL execution capabilities.")
public class Database extends GraphQLToolkit implements ConnectionListener {

    /**
     * Flag indicating if the toolkit is currently listening for global IDE
     * connection changes.
     */
    private transient boolean listening = false;

    /**
     * {@inheritDoc}
     *
     * @return the stable toolkit identifier {@code database}
     */
    @Override
    public String getId() {
        return "database";
    }

    /**
     * {@inheritDoc}
     *
     * @return the Database integration display name
     */
    @Override
    public String getName() {
        return "Database Integration";
    }

    /**
     * {@inheritDoc}
     *
     * @return a description of the IDE and JDBC integration
     */
    @Override
    public String getDescription() {
        return "Natively interacts with NetBeans Database Explorer and JDBC services.";
    }

    /**
     * {@inheritDoc}
     * <p>
     * Builds the transient executable schema and registers this toolkit instance for
     * IDE connection changes. Repeated registration is guarded by
     * {@link #syncListeners()}.
     * </p>
     */
    @Override
    public void initialize() {
        super.initialize();
        syncListeners();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Rebuilds transient GraphQL state and ensures this instance has an IDE connection
     * listener. It does not reconnect database sessions or load metadata eagerly.
     * </p>
     */
    @Override
    public void rebind() {
        super.rebind();
        syncListeners();
    }

    /**
     * {@inheritDoc}
     *
     * @return the metadata and explicit-command SDL used by this toolkit
     */
    @Override
    protected String getSchemaSdl() {
        return DatabaseGraphQL.SCHEMA;
    }

    /**
     * {@inheritDoc}
     *
     * @return wiring for selected JDBC operations and already-acquired Info properties
     */
    @Override
    protected RuntimeWiring buildRuntimeWiring() {
        return DatabaseGraphQL.buildRuntimeWiring();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Describes bounded discovery, complete destination identities, selection-driven
     * result acquisition, and raw SQL effects. Instructions do not replace the current
     * tool permission or validate SQL as read-only.
     * </p>
     *
     * @return the bounded-discovery and explicit-command instructions
     * @throws Exception as permitted by the inherited instruction-provider contract
     */
    @Override
    public List<String> getSystemInstructions() throws Exception {
        return List.of(
                "Database exposes only schema(), query() and mutate(). Discover the schema before issuing calls.",
                "Use query() for selection-driven metadata discovery. Pages default to 25, maximum 100; use complete catalog/schema/table identities.",
                "Use mutate() for explicit connect/disconnect and raw SQL commands, including executeQuery. Raw SQL can have effects and requires the configured command permission.",
                "Use discovered connection ids; no fallback destination or implicit connection dialog is allowed. Discover columns before composing SQL.",
                "Mutation fields execute serially, without a shared transaction, rollback or automatic retry. A failure after an effect does not undo it; verify before retrying.",
                "SQL rows/cells are positional; labels may repeat. Respect returnedRowCount, hasNextPage and cell truncation; totalCount may be unknown.",
                "Inspect both data and errors. Null, an empty list and a failed field have different meanings. Select only needed fields."
        );
    }

    /**
     * {@inheritDoc}
     * <p>
     * Adds registered and currently connected counts from the IDE registry. It does
     * not request JDBC metadata, open connections, or publish names and credentials.
     * </p>
     *
     * @param message the context message receiving aggregate connection counts
     * @throws Exception as permitted by the inherited context-provider contract
     */
    @Override
    public void populateMessage(RagMessage message) throws Exception {
        DatabaseConnection[] connections = ConnectionManager.getDefault().getConnections();
        int registered = connections == null ? 0 : connections.length;
        int connected = 0;
        if (connections != null) {
            for (DatabaseConnection connection : connections) {
                if (connection.getJDBCConnection() != null) {
                    connected++;
                }
            }
        }
        message.addTextPart("## Database Connections\nRegistered: " + registered + "; connected: " + connected
                + ". Use schema() and query() for explicit connection discovery.");
    }

    /**
     * Executes one explicit Mutation through the existing Java tool protocol.
     * <p>
     * Approval applies to the complete document and variables. Commands may have
     * side effects even when only {@code success} is selected. Raw SQL is not made
     * read-only by a method name or SELECT prefix. Fields execute serially without
     * a shared transaction, automatic rollback, or retry; inspect data and errors
     * before deciding whether another call is appropriate.
     * </p>
     *
     * @param document one Mutation operation, optionally using variables and fragments
     * @param variables JSON-compatible variable values, or {@code null} for none
     * @return selected command data and safe errors, including partial field failures
     */
    @Override
    @AgiTool("Executes one GraphQL Mutation with explicit database commands. Raw SQL may have effects, including executeQuery. "
            + "Review document and variables before approval. Fields are serial, not one transaction; no rollback or automatic retry.")
    public GraphQLExecutionInfo mutate(
            @AgiToolParam("One GraphQL Mutation document; discover commands with schema().") String document,
            @AgiToolParam(value = "Variable values, including the exact connection id and SQL to authorize.", required = false) Map<String, Object> variables) {
        return super.mutate(document, variables);
    }

    /**
     * Enables this reference implementation's explicit command channel.
     *
     * @return {@code true}; other GraphQL toolkits opt in independently
     */
    @Override
    protected boolean supportsMutations() {
        return true;
    }

    /**
     * Validates selected root command arguments and pins their destinations.
     * <p>
     * Queries need no additional domain preflight here. Command preflight performs no
     * SQL execution, connection dialog, or transaction management, but may inspect an
     * active connection's catalog and schema. Runtime failures remain possible after
     * preflight and do not undo earlier effects.
     * </p>
     *
     * @param fields native selected fields with coerced arguments
     * @param request the execution-local cache and cooperative budget
     * @param mutation whether this is the explicit Mutation channel
     * @throws uno.anahata.asi.agi.tool.graphql.SafeGraphQLException if command arguments,
     *     identities, or request budgets are invalid
     */
    @Override
    protected void validateSelection(List<GraphQLFieldInfo> fields, GraphQLRequestContext request, boolean mutation) {
        if (mutation) {
            DatabaseCommands.validate(fields, request);
        }
    }

    /**
     * Estimates SQL list cost without counting the parent row limit twice.
     * <p>
     * Rows are bounded at the SQL parent; columns and cells use a separate column
     * ceiling. Actual row, cell, text, and deadline budgets still apply during execution,
     * so acceptance of the estimate does not guarantee completion.
     * </p>
     *
     * @param field the native field definition and coerced arguments
     * @param children the accumulated child-field complexity
     * @param maximum the configured complexity rejection threshold
     * @return a cost capped at one above the threshold
     */
    @Override
    protected int fieldComplexity(FieldComplexityEnvironment field, int children, int maximum) {
        String parent = field.getParentType().getName();
        if ("SqlQueryInfo".equals(parent) && "rows".equals(field.getFieldDefinition().getName())) {
            return Math.min(maximum + 1, 1 + children);
        }
        if (("SqlQueryInfo".equals(parent) && "columns".equals(field.getFieldDefinition().getName()))
                || ("SqlRowInfo".equals(parent) && "cells".equals(field.getFieldDefinition().getName()))) {
            return (int) Math.min(maximum + 1L, 1L + DatabaseCommands.MAX_COLUMNS * (long) Math.max(1, children));
        }
        return super.fieldComplexity(field, children, maximum);
    }

    /**
     * Registers this instance with the IDE connection registry at most once.
     * <p>
     * Synchronization protects listener registration only. It does not lock JDBC
     * work or serialize commands from independent requests.
     * </p>
     */
    private synchronized void syncListeners() {
        if (!listening) {
            ConnectionManager.getDefault().addConnectionListener(this);
            listening = true;
            log.info("Database toolkit is now listening for IDE connection changes.");
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Records registry changes without querying, connecting, or disconnecting database
     * servers. The callback performs no JDBC work on the notifying thread.
     * </p>
     */
    @Override
    public void connectionsChanged() {
        log.debug("Database connections updated in NetBeans Services tab.");
    }

}
