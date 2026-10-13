/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.agi.tool.query;

import java.util.Locale;

/**
 * Compares an actual property with a requested filter value without reflection.
 *
 * @param <V> Property and filter value type.
 * @author anahata
 */
@FunctionalInterface
public interface FilterMatcher<V> {

    /**
     * Tests a property against the requested value.
     *
     * @param actual Property extracted from the source.
     * @param expected Requested filter value.
     * @return Whether this property matches.
     */
    boolean matches(V actual, V expected);

    /**
     * Builds a locale-independent, case-insensitive substring matcher. A null
     * expected value leaves the property unconstrained.
     *
     * @return Matcher suitable for connection name filters.
     */
    static FilterMatcher<String> containsIgnoreCase() {
        return (actual, expected) -> expected == null
                || actual != null && actual.toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT));
    }
}
