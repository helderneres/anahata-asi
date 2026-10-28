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
 * Output-only GraphQL scalar for one already-converted SQL cell value.
 * <p>
 * Serialization accepts strings, numbers, booleans and null, without wrapping
 * a row or table in opaque JSON. The command producer, rather than this
 * scalar, converts supported JDBC values and applies text, binary and output
 * budgets. Composite or unsupported objects are rejected here; input values
 * and literals are always rejected.
 * </p>
 */
final class SqlValueScalar {

    /** Prevents construction of the scalar holder. */
    private SqlValueScalar() {
    }
    /**
     * Shared output scalar used by the embedded Database schema.
     * No JDBC object conversion, truncation or input coercion is performed by this type.
     */
    static final GraphQLScalarType TYPE = GraphQLScalarType.newScalar().name("SqlValue")
            .description("One portable SQL cell scalar; tabular structure remains typed and positional.")
            .coercing(new Coercing<Object, Object>() {
                /**
                 * Returns an already-portable cell value without changing its scalar type.
                 *
                 * @param value the converted cell scalar, possibly null
                 * @param context the execution context, not inspected by this coercion
                 * @param locale the requested locale, not used to reformat values
                 * @return the same string, number, boolean or null
                 * @throws CoercingSerializeException if the value is not a supported scalar
                 */
                @Override
                public Object serialize(Object value, GraphQLContext context, Locale locale) {
                    if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
                        return value;
                    }
                    throw new CoercingSerializeException("Unsupported SQL cell value.");
                }

                /**
                 * Rejects variable input because SqlValue is an output-only scalar.
                 *
                 * @param value the input value, not accepted
                 * @param context the execution context, not inspected
                 * @param locale the requested locale, not inspected
                 * @return no value; this method always throws
                 * @throws CoercingParseValueException for every input
                 */
                @Override
                public Object parseValue(Object value, GraphQLContext context, Locale locale) {
                    throw new CoercingParseValueException("SqlValue is output-only.");
                }

                /**
                 * Rejects literal input because SqlValue is an output-only scalar.
                 *
                 * @param value the input literal, not accepted
                 * @param variables the coerced operation variables, not inspected
                 * @param context the execution context, not inspected
                 * @param locale the requested locale, not inspected
                 * @return no value; this method always throws
                 * @throws CoercingParseLiteralException for every literal
                 */
                @Override
                public Object parseLiteral(Value<?> value, CoercedVariables variables, GraphQLContext context, Locale locale) {
                    throw new CoercingParseLiteralException("SqlValue is output-only.");
                }
            }).build();
}
