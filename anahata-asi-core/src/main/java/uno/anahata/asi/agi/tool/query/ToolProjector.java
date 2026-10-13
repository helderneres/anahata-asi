/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.agi.tool.query;

import java.util.function.Function;

/**
 * Transforms an internal source to a concrete public Info model. Selection and
 * execution remain the responsibility of native GraphQL resolvers.
 *
 * @param <E> Internal source type.
 * @param <R> Public information type.
 * @author anahata
 */
@FunctionalInterface
public interface ToolProjector<E, R> extends Function<E, R> {

    /**
     * {@inheritDoc}
     * <p>
     * Produces only transportable information, without internal handles.</p>
     */
    @Override
    R apply(E entity);
}
