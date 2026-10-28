/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
/**
 * Supplies typed functional composition for filtering and projecting tool data.
 *
 * <p>Prefer filtering through a source's native API. These abstractions provide
 * an in-memory fallback without reflective property access; null handling,
 * resource ownership and thread safety remain responsibilities of the supplied
 * functions. Projection converts source values to public information and does
 * not resolve GraphQL selections or schedule source operations.</p>
 */
package uno.anahata.asi.agi.tool.query;
