/*
 * Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça!
 */
package uno.anahata.asi.swing.agi.render.editorkit;

/**
 * An interface for semantic language identification.
 * <p>
 * This allows the platform to map filenames and content to language identifiers 
 * suitable for syntax highlighting. 
 * </p>
 * <p>
 * <b>Architectural Note:</b> Visual component creation (EditorKits) has been 
 * moved to the {@code ResourceUI} system to support high-fidelity host-aware 
 * assembled frames (NetBeans Editor vs RSyntaxTextArea).
 * </p>
 *
 * @author anahata
 */
public interface EditorKitProvider {

    /**
     * Maps a filename or extension to a language identifier suitable for 
     * syntax highlighting.
     * 
     * @param filename The name of the file or its path.
     * @return A language string (e.g., "java", "json"), or "text" as a fallback.
     */
    String getLanguageForFile(String filename);
}
