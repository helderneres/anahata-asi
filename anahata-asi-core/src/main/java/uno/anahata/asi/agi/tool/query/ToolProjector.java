/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.agi.tool.query;

import java.util.function.Function;

/**
 * Transforms an internal source into a public information model.
 * This {@link Function} specialization supplies a descriptive domain type;
 * it does not interpret a GraphQL selection set or schedule source work.
 * Implementations must exclude live JDBC, IDE or other internal handles
 * from information that is returned to the model.
 *
 * @param <E> internal source type
 * @param <R> projected information type
 * @author anahata
 */
@FunctionalInterface
public interface ToolProjector<E, R> extends Function<E, R> {

    /**
     * Projects a source using the implementation's null and failure policy.
     * Implementations are responsible for the returned value's ownership and
     * transport suitability; the interface itself performs no validation.
     *
     * @param entity source entity; null handling is implementation-specific
     * @return projected information; whether null is valid is implementation-specific
     */
    @Override
    R apply(E entity);
}
