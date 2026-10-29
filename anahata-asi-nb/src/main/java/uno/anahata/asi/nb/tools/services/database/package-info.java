/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
/**
 * Selection-driven database discovery and explicit JDBC commands for NetBeans.
 * <p>
 * The {@link uno.anahata.asi.nb.tools.services.database.Database} toolkit
 * exposes schema, Query, and Mutation tools through the existing Anahata Java
 * protocol. Public Info classes describe values; implementation sources retain
 * IDE handles and complete catalog/schema/table identities privately for
 * selected resolvers. The GraphQL schema, rather than every Java property,
 * defines response fields.
 * </p>
 * <p>
 * Metadata discovery uses already-active IDE connections, native JDBC filters,
 * bounded cursor traversal, and request-local metadata/key caches. Only
 * returned page items acquire child metadata. SQL commands have explicit
 * effects; selecting fewer result fields limits acquisition without suppressing
 * the selected command. Mutation roots are serial within one document, without
 * a shared transaction, automatic rollback, or retry. Driver failures can
 * produce partial responses.
 * </p>
 * <p>
 * Metadata and SQL can block in drivers and must run outside the Swing event
 * dispatch thread. Cooperative deadlines are not hard driver cancellation.
 * Resolvers close owned result sets, statements, and selected LOB resources;
 * NetBeans retains connection ownership. Explicit connect/disconnect commands
 * delegate lifecycle to the IDE outside the event dispatch thread, without
 * opening dialogs. Results distinguish confirmed completion, pending work and
 * required operator interaction; connection establishment may block beyond a
 * cooperative deadline.
 * </p>
 */
package uno.anahata.asi.nb.tools.services.database;
