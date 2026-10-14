/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.nb.tools.services.database;

import graphql.schema.Coercing;
import graphql.GraphQLContext;
import graphql.execution.CoercedVariables;
import graphql.language.Value;
import java.util.Locale;
import graphql.schema.CoercingSerializeException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.GraphQLScalarType;

/**
 * Output-only scalar for one JDBC cell, preserving numbers, booleans and null.
 */
final class SqlValueScalar {

    private SqlValueScalar() {
    }
    static final GraphQLScalarType TYPE = GraphQLScalarType.newScalar().name("SqlValue")
            .description("One portable SQL cell scalar; tabular structure remains typed and positional.")
            .coercing(new Coercing<Object, Object>() {
                @Override
                public Object serialize(Object value, GraphQLContext context, Locale locale) {
                    if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
                        return value;
                    }
                    throw new CoercingSerializeException("Unsupported SQL cell value.");
                }

                @Override
                public Object parseValue(Object value, GraphQLContext context, Locale locale) {
                    throw new CoercingParseValueException("SqlValue is output-only.");
                }

                @Override
                public Object parseLiteral(Value<?> value, CoercedVariables variables, GraphQLContext context, Locale locale) {
                    throw new CoercingParseLiteralException("SqlValue is output-only.");
                }
            }).build();
}
