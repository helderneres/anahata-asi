/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.nb.tools.services.database;

import lombok.Builder;
import lombok.Data;

/**
 * Status snapshot returned after an explicit IDE connect or disconnect command.
 * <p>
 * The snapshot contains no JDBC handle or credentials. A GraphQL
 * {@code connection} child is resolved separately from a private source. An
 * already-satisfied connection state also produces a successful result; this
 * object does not prove that a new physical connection was established.
 * Pending, interaction-required, failed and unknown outcomes remain distinct
 * from success. A false success flag alone must not be interpreted as a
 * terminal driver failure; inspect status and the authored guidance.
 * </p>
 */
@Data
@Builder
public class ConnectionCommandInfo {

    /**
     * Whether the command resolver completed with the requested connection
     * state. Already being in that state also counts as success.
     */
    private boolean success;
    /**
     * Operational outcome; separate from tool protocol status and permissions.
     */
    private ConnectionCommandStatus status;
    /**
     * Authored guidance containing no driver message, URL, user or credentials.
     */
    private String message;
    /**
     * Whether the registry supplied a non-null JDBC connection after the
     * command; a snapshot, not a liveness probe or promise about later
     * requests.
     */
    private boolean connected;
}
