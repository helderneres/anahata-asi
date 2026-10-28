/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
/**
 * Embeds GraphQL execution in the existing Anahata Java tool protocol.
 *
 * <p>{@link uno.anahata.asi.agi.tool.graphql.GraphQLToolkit} is the extension
 * point for schema and native resolver wiring. Queries and explicitly enabled
 * commands use separate channels, a fresh request context and portable response
 * DTOs. Domain implementations own source identity, effects and resource cleanup;
 * this package supplies no HTTP service or transaction manager.</p>
 *
 * <p>Execution may block and must be scheduled appropriately by callers.
 * Request budgets are cooperative, selected null values are preserved, and data
 * must be interpreted together with public errors. Response and variable model
 * constructors do not independently sanitize arbitrary application data.</p>
 */
package uno.anahata.asi.agi.tool.graphql;
