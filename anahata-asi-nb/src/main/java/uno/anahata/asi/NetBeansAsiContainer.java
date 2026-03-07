/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi;

import lombok.extern.slf4j.Slf4j;
import uno.anahata.asi.agi.Agi;
import uno.anahata.asi.nb.ui.render.FullTextFileUpdateRenderer;
import uno.anahata.asi.nb.ui.render.TextFileReplacementsRenderer;
import uno.anahata.asi.nb.ui.resources.NbResourceUI;
import uno.anahata.asi.nb.util.ElementHandleModule;
import uno.anahata.asi.swing.agi.message.part.tool.param.ParameterRendererFactory;
import uno.anahata.asi.swing.agi.resources.ResourceUiRegistry;
import uno.anahata.asi.tool.schema.SchemaProvider;
import uno.anahata.asi.toolkit.files.FullTextFileUpdate;
import uno.anahata.asi.toolkit.files.TextFileReplacements;

/**
 * NetBeans-specific configuration for the Anahata ASI.
 * <p>
 * Handles IDE-specific initialization and session management. Global environment 
 * configuration (parameter renderers, JSON modules) is performed once during 
 * static initialization.
 * </p>
 * 
 * @author anahata
 */
@Slf4j
public class NetBeansAsiContainer extends AsiContainer {

    static {
        log.info("Performing global NetBeans environment configuration...");
        
        // 1. Register specialized parameter renderers for file operations
        ParameterRendererFactory.register(FullTextFileUpdate.class, FullTextFileUpdateRenderer.class);        
        ParameterRendererFactory.register(TextFileReplacements.class, TextFileReplacementsRenderer.class);
        
        // 2. Register the ElementHandle module for global JSON support in the IDE
        SchemaProvider.OBJECT_MAPPER.registerModule(new ElementHandleModule());
        
        // 3. Register the NetBeans-native resource UI strategy
        ResourceUiRegistry.getInstance().setResourceUI(new NbResourceUI());
    }

    /**
     * Default constructor for the NetBeans container.
     */
    public NetBeansAsiContainer() {
        super("netbeans");
    }

    /**
     * {@inheritDoc}
     * <p>
     * Initializes the session environment. Classpath setup is now delegated 
     * to the autonomous {@code NbJava} toolkit.
     * </p>
     */
    @Override
    public void onAgiCreated(Agi agi) {
        log.info("Initializing NetBeans environment for agi session: {}", agi.getConfig().getSessionId());

        // Default model configuration for NetBeans
        if (agi.getSelectedModel() == null) {
            agi.setProviderAndModel("Gemini", "models/gemini-3-flash-preview");
        }
    }

    /**
     * Finds an existing active agi by its session ID, or creates a new one
     * if the ID is null or not found.
     * 
     * @param sessionId The session ID to find.
     * @return The found or newly created agi session.
     */
    public Agi findOrCreateAgi(String sessionId) {
        if (sessionId != null) {
            for (Agi agi : getActiveAgis()) {
                if (agi.getConfig().getSessionId().equals(sessionId)) {
                    return agi;
                }
            }
        }
        return createNewAgi();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Agi createNewAgi() {
        return new Agi(new NetBeansAgiConfig(this));
    }
}
