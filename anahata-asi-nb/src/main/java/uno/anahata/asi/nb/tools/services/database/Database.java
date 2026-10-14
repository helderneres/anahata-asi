/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.nb.tools.services.database;

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
 * Database Integration Toolkit.
 * <p>
 * Provides comprehensive database interaction capabilities, allowing the ASI to
 * list connections, inspect schemas, and execute queries natively within the
 * IDE. It monitors the IDE's ConnectionManager for real-time updates.
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
     * <p>
     * Provides the existing stable identifier for this IDE integration.</p>
     */
    @Override
    public String getId() {
        return "database";
    }

    /**
     * {@inheritDoc}
     * <p>
     * Preserves the existing user-facing Database toolkit name.</p>
     */
    @Override
    public String getName() {
        return "Database Integration";
    }

    /**
     * {@inheritDoc}
     * <p>
     * Describes the NetBeans/JDBC integration shared by GraphQL and SQL
     * tools.</p>
     */
    @Override
    public String getDescription() {
        return "Natively interacts with NetBeans Database Explorer and JDBC services.";
    }

    /**
     * {@inheritDoc}
     * <p>
     * Builds the GraphQL engine before registering the IDE listener once.</p>
     */
    @Override
    public void initialize() {
        super.initialize();
        syncListeners();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Restores transient GraphQL state and reconnects the IDE listener after
     * persistence.</p>
     */
    @Override
    public void rebind() {
        super.rebind();
        syncListeners();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Publishes metadata discovery without exposing JDBC or IDE objects.</p>
     */
    @Override
    protected String getSchemaSdl() {
        return DatabaseGraphQL.SCHEMA;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Wires independent JDBC operations to selected GraphQL fields.</p>
     */
    @Override
    protected RuntimeWiring buildRuntimeWiring() {
        return DatabaseGraphQL.buildRuntimeWiring();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Guides bounded discovery and keeps raw SQL tools subject to existing
     * permissions.</p>
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
     * Publishes aggregate counts only; connection names can embed sensitive
     * JDBC details.</p>
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
     * Explicit command channel using the existing Java tool permission.
     */
    @Override
    @AgiTool("Executes one GraphQL Mutation with explicit database commands. Raw SQL may have effects, including executeQuery. "
            + "Review document and variables before approval. Fields are serial, not one transaction; no rollback or automatic retry.")
    public GraphQLExecutionInfo mutate(
            @AgiToolParam("One GraphQL Mutation document; discover commands with schema().") String document,
            @AgiToolParam(value = "Variable values, including the exact connection id and SQL to authorize.", required = false) Map<String, Object> variables) {
        return super.mutate(document, variables);
    }

    @Override
    protected boolean supportsMutations() {
        return true;
    }

    @Override
    protected void validateSelection(List<GraphQLFieldInfo> fields, GraphQLRequestContext request, boolean mutation) {
        if (mutation) {
            DatabaseCommands.validate(fields, request);
        }
    }

    /**
     * SQL rows are bounded by the parent limit; cells/columns have a separate
     * ceiling.
     */
    @Override
    protected int fieldComplexity(graphql.analysis.FieldComplexityEnvironment field, int children, int maximum) {
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
     * Synchronizes the toolkit with the IDE's ConnectionManager.
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
     * Records IDE registry changes without querying or connecting database
     * servers.</p>
     */
    @Override
    public void connectionsChanged() {
        log.debug("Database connections updated in NetBeans Services tab.");
    }

}
