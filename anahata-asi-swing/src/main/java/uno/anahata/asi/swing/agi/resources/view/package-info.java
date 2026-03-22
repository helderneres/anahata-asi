/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
/**
 * Provides specialized UI components for viewing and editing managed resources.
 * <p>
 * This package implements the **Interpreter/Viewer Duality**. Each resource type 
 * is rendered through a high-fidelity viewer that supports both a "Preview" 
 * mode (for model interaction/RAG) and a "High-Fidelity Edit" mode 
 * (for standalone or IDE-like modifications).
 * </p>
 * <p>
 * Viewers in this package are designed to be "Autonomous," managing their own 
 * control strips and synchronization logic with the underlying resource handles.
 * </p>
 * 
 * @author anahata
 */
package uno.anahata.asi.swing.agi.resources.view;
