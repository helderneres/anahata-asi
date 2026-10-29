/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.nb.tools.services.database;

import java.util.Map;
import java.util.WeakHashMap;
import javax.swing.SwingUtilities;
import org.netbeans.api.db.explorer.ConnectionManager;
import org.netbeans.api.db.explorer.DatabaseConnection;
import org.netbeans.api.db.explorer.DatabaseException;

/**
 * Executes explicit IDE lifecycle commands without opening or waiting for
 * dialogs. The public synchronous connect API is used only outside the event
 * dispatch thread. Its normal false return means configuration requires
 * interaction; absence of a handle is never inferred to be a terminal
 * connection failure. A synchronous driver or credential-store call can still
 * block: the request deadline is cooperative and cannot preempt connection
 * establishment. JDBC statement query timeouts do not govern this lifecycle
 * API.
 *
 * <p>
 * The weak-key in-flight registry is process-local because IDE registrations
 * are shared across toolkit sessions. It prevents concurrent lifecycle commands
 * for the same handle, without serializing unrelated database SQL. It contains
 * no credentials and is never serialized with a session. It does not track or
 * claim ownership of connection attempts started elsewhere in the IDE.</p>
 */
final class DatabaseConnectionCommands {

    /**
     * Short-held monitor protecting only in-flight identity, never JDBC work.
     */
    private static final Map<DatabaseConnection, Boolean> IN_FLIGHT = new WeakHashMap<>();

    /**
     * Prevents instantiation of this explicit command helper.
     */
    private DatabaseConnectionCommands() {
    }

    /**
     * Starts one lifecycle command or reports its current observable condition.
     * An existing in-flight command returns immediately without retrying it. No
     * JDBC connection is closed directly; disconnect ownership stays with IDE.
     *
     * @param manager the IDE manager already selected by the resolver
     * @param registered preflight-pinned IDE registration
     * @param connect requested connected state
     * @return safe state snapshot; success is true only for confirmed requested
     * state
     */
    static ConnectionCommandInfo execute(ConnectionManager manager, DatabaseConnection registered, boolean connect) {
        synchronized (IN_FLIGHT) {
            boolean observed = registered.getJDBCConnection() != null;
            if (IN_FLIGHT.containsKey(registered)) {
                return outcome(ConnectionCommandStatus.PENDING, observed,
                        "A lifecycle command is in progress. Inspect connection state before issuing another command.");
            }
            if (observed == connect) {
                return outcome(ConnectionCommandStatus.SUCCEEDED, observed, "The requested state is observed.");
            }
            if (SwingUtilities.isEventDispatchThread()) {
                return outcome(ConnectionCommandStatus.REQUIRES_INTERACTION, observed,
                        "Run this command in a background tool invocation or use IDE Services; no lifecycle operation was started.");
            }
            IN_FLIGHT.put(registered, connect);
        }
        try {
            if (connect) {
                if (!manager.connect(registered)) {
                    return outcome(ConnectionCommandStatus.REQUIRES_INTERACTION, registered.getJDBCConnection() != null,
                            "Complete connection configuration in IDE Services, then inspect state. No connection dialog was opened.");
                }
            } else {
                manager.disconnect(registered);
            }
            boolean observed = registered.getJDBCConnection() != null;
            return outcome(observed == connect ? ConnectionCommandStatus.SUCCEEDED : ConnectionCommandStatus.UNKNOWN,
                    observed, observed == connect ? "The requested state is observed."
                            : "The IDE returned without confirming the requested state. Inspect Services before retrying.");
        } catch (DatabaseException failure) {
            return outcome(ConnectionCommandStatus.FAILED, registered.getJDBCConnection() != null,
                    "The IDE reported a connection failure. Inspect local IDE diagnostics before retrying.");
        } catch (RuntimeException failure) {
            return outcome(ConnectionCommandStatus.UNKNOWN, registered.getJDBCConnection() != null,
                    "The lifecycle operation could not be confirmed. Inspect IDE Services before retrying.");
        } finally {
            synchronized (IN_FLIGHT) {
                IN_FLIGHT.remove(registered);
            }
        }
    }

    /**
     * Captures public state without copying a driver message or IDE identity.
     *
     * @param status authored operational outcome
     * @param connected one captured handle-presence observation used for all
     * outcome fields
     * @param message authored guidance without connection configuration
     * @return public DTO without handles or credentials
     */
    private static ConnectionCommandInfo outcome(ConnectionCommandStatus status, boolean connected, String message) {
        return ConnectionCommandInfo.builder().status(status).success(status == ConnectionCommandStatus.SUCCEEDED)
                .connected(connected).message(message).build();
    }
}
