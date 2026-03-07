/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.swing.agi.message.part.tool.param;

import javax.swing.JComponent;
import uno.anahata.asi.model.tool.AbstractToolCall;
import uno.anahata.asi.swing.agi.AgiPanel;
import uno.anahata.asi.swing.agi.message.part.text.CodeBlockSegmentRenderer;

/**
 * A parameter renderer that displays string values as code blocks with syntax highlighting.
 * It wraps an {@link CodeBlockSegmentRenderer} and supports editing by syncing
 * changes back to the {@link AbstractToolCall}.
 * 
 * @author anahata
 */
public class CodeBlockParameterRenderer implements ParameterRenderer<String> {
    
    private CodeBlockSegmentRenderer renderer;
    private String language;

    /** No-arg constructor for factory instantiation. */
    public CodeBlockParameterRenderer() {}

    @Override
    public void init(AgiPanel agiPanel, AbstractToolCall<?, ?> call, String paramName, String value) {
        init(agiPanel, call, paramName, value, "code");
    }

    /**
     * Specialized initialization for code blocks with a specific language.
     * 
     * @param agiPanel The agi panel.
     * @param call The tool call.
     * @param paramName The parameter name.
     * @param value The initial value.
     * @param language The language for syntax highlighting.
     */
    public void init(AgiPanel agiPanel, AbstractToolCall<?, ?> call, String paramName, String value, String language) {
        this.language = language;
        
        // THE SINGULARITY PATH: Directly instantiate the concrete worker. 
        // It authoritatively leverages the Universal Resource Pipeline.
        this.renderer = new CodeBlockSegmentRenderer(agiPanel, value, language);
        this.renderer.setEditable(true);
        this.renderer.setOnSave(newContent -> {
            call.setModifiedArgument(paramName, newContent);
        });
    }

    @Override
    public JComponent getComponent() {
        return renderer.getComponent();
    }

    @Override
    public void updateContent(String value) {
        renderer.updateContent(value);
    }

    @Override
    public boolean render() {
        return renderer.render();
    }
}
