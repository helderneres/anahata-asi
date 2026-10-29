/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.nb.tools.services.database;

import java.util.Map;
import uno.anahata.asi.agi.tool.execution.ResultLoadingPolicy;
import uno.anahata.asi.agi.tool.graphql.GraphQLRequestContext;
import uno.anahata.asi.agi.tool.graphql.SafeGraphQLException;
import uno.anahata.asi.nb.tools.services.database.policy.DatabaseExecutionPolicies;

/**
 * Applies effective loading values at the native GraphQL argument boundary.
 * This helper grants no permission and cannot connect or execute database work.
 */
final class DatabasePolicySupport {

    /**
     * Prevents construction of this argument-boundary utility.
     */
    private DatabasePolicySupport() {
    }

    /**
     * Resolves a page size without confusing an omitted argument with explicit
     * null. Policy absence of a maximum preserves the GraphQL Int
     * representation; actual acquisition and response budgets remain
     * independent operational safeguards.
     *
     * @param arguments native-coerced resource arguments
     * @param request immutable policy snapshot for this document
     * @param mutation whether this resource is a command result
     * @param resource schema resource name
     * @return positive page size within any effective maximum
     * @throws SafeGraphQLException if the argument is null, invalid or exceeds
     * policy
     */
    static int pageLimit(Map<String, Object> arguments, GraphQLRequestContext request,
            boolean mutation, String resource) {
        ResultLoadingPolicy loading = request.getExecutionPolicy(DatabaseExecutionPolicies.class)
                .getLoadingPolicy(mutation, resource);
        Object value = arguments.containsKey("limit") ? arguments.get("limit") : loading.getDefaultPageSize();
        if (!(value instanceof Integer limit) || limit <= 0) {
            throw new SafeGraphQLException("InvalidArgument", "limit must be a positive GraphQL Int; omit it to use the configured default.");
        }
        Integer maximum = loading.getMaxPageSize();
        if (maximum != null && limit > maximum) {
            throw new SafeGraphQLException("InvalidArgument", "limit exceeds the configured page maximum of " + maximum + ".");
        }
        return limit;
    }
}
