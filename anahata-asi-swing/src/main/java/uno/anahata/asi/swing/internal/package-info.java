/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
/**
 * Internal Swing utilities and bridge components for Anahata ASI.
 * <p>
 * This package provides the low-level "plumbing" required to maintain architectural 
 * integrity within a Swing environment. It includes lifecycle-aware listeners, 
 * background task orchestrators, and native screen capture utilities.
 * </p>
 * 
 * <h2>Key Infrastructure:</h2>
 * <ul>
 *   <li><b>{@link uno.anahata.asi.swing.internal.SwingUtils}</b>: 
 *       A comprehensive utility class for EDT management, component discovery, and image processing.</li>
 *   <li><b>{@link uno.anahata.asi.swing.internal.EdtPropertyChangeListener}</b>: 
 *       A sophisticated, hierarchy-aware listener that bridges domain events to the EDT 
 *       while preventing memory leaks in dynamic UIs.</li>
 *   <li><b>{@link uno.anahata.asi.swing.internal.SwingTask}</b>: 
 *       A functional wrapper around {@code SwingWorker} that standardizes background 
 *       execution and error handling.</li>
 *   <li><b>{@link uno.anahata.asi.swing.internal.UICapture}</b>: 
 *       The native bridge for multi-screen and window-level vision.</li>
 * </ul>
 * 
 * @author anahata
 */
package uno.anahata.asi.swing.internal;
