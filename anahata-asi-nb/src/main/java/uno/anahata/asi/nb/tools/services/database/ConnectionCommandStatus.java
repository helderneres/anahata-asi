/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.nb.tools.services.database;

/**
 * Operational connection outcomes, independent of execution-policy permissions.
 */
public enum ConnectionCommandStatus {
    /**
     * The requested state was observed; this is not a later liveness guarantee.
     */
    SUCCEEDED,
    /**
     * Another explicit lifecycle command for the same registration is in
     * progress.
     */
    PENDING,
    /**
     * Complete configuration or use IDE Services; no dialog was opened by this
     * command.
     */
    REQUIRES_INTERACTION,
    /**
     * The IDE lifecycle API reported a confirmed command failure.
     */
    FAILED,
    /**
     * The lifecycle API returned without confirming the requested observable
     * state.
     */
    UNKNOWN
}
