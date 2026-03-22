/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
/**
 * Specialized rendering subsystem for Anahata Toolkits in a Swing environment.
 * <p>
 * This package provides a provider-agnostic framework for building and registering 
 * high-fidelity UIs for {@link uno.anahata.asi.agi.tool.AnahataToolkit} instances. 
 * It decouples the domain-level toolkit logic from its visual representation 
 * while maintaining strict thread-safety and session isolation.
 * </p>
 * 
 * <h2>Key Architectural Components:</h2>
 * <ul>
 *   <li><b>{@link uno.anahata.asi.swing.toolkit.render.ToolkitUiRegistry}</b>: 
 *       The central discovery hub that maps toolkit classes to their specialized renderers.</li>
 *   <li><b>{@link uno.anahata.asi.swing.toolkit.render.ToolkitRenderer}</b>: 
 *       The fundamental contract for any component capable of rendering a toolkit.</li>
 *   <li><b>{@link uno.anahata.asi.swing.toolkit.render.AbstractToolkitRenderer}</b>: 
 *       A robust base class that handles EDT synchronization and property-change binding 
 *       automatically using {@link uno.anahata.asi.swing.internal.EdtPropertyChangeListener}.</li>
 * </ul>
 * 
 * <h2>Threading Model:</h2>
 * <p>
 * Renderers in this package are designed to be triggered from either the background threads 
 * of the AI Tool execution context or the Swing Event Dispatch Thread (EDT). 
 * The base implementation ensures that all state-to-UI updates are safely marshaled 
 * to the EDT.
 * </p>
 * 
 * @author anahata
 */
package uno.anahata.asi.swing.toolkit.render;
