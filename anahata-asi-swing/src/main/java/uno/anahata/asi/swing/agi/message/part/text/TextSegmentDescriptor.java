/*
 * Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça!
 */
package uno.anahata.asi.swing.agi.message.part.text;

import lombok.extern.slf4j.Slf4j;
import uno.anahata.asi.swing.agi.AgiPanel;

/**
 * A record that describes a segment of text within a {@link uno.anahata.asi.swing.agi.message.part.TextPartPanel}.
 * It includes the segment's type, its content, and optionally the language for code blocks.
 *
 * @param type The {@link TextSegmentType} of the segment (TEXT or CODE).
 * @param content The raw text content of the segment.
 * @param language The programming language for code segments (null for text segments).
 * @param closed True if the segment (especially a code block) is fully closed/complete.
 *
 * @author anahata
 */
@Slf4j
public record TextSegmentDescriptor(TextSegmentType type, String content, String language, boolean closed) {

    /**
     * Creates and returns an appropriate {@link AbstractTextSegmentRenderer} for this descriptor.
     *
     * @param agiPanel The agi panel instance.
     * @param isThought True if the text represents a model thought, false otherwise.
     * @return A concrete instance of {@link AbstractTextSegmentRenderer}.
     */
    public AbstractTextSegmentRenderer createRenderer(AgiPanel agiPanel, boolean isThought) {
        AbstractTextSegmentRenderer renderer = switch (type) {
            case TEXT -> new MarkupTextSegmentRenderer(agiPanel, content, isThought);
            case CODE -> createCodeBlockRenderer(agiPanel);
        };
        renderer.setClosed(closed);
        return renderer;
    }

    private AbstractTextSegmentRenderer createCodeBlockRenderer(AgiPanel agiPanel) {
        if ("mermaid".equalsIgnoreCase(language)) {
            return new MermaidCodeBlockSegmentRenderer(agiPanel, content, language);
        }
        
        // THE SINGULARITY PATH: Directly instantiate the concrete, host-agnostic worker.
        // It will use the ResourceUiRegistry to resolve the proper visual component.
        return new CodeBlockSegmentRenderer(agiPanel, content, language);
    }
}
