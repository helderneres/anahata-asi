/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.asi.nb.mine;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.EditorKit;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileSystem;
import org.openide.filesystems.FileUtil;
import uno.anahata.asi.nb.ui.render.NbCodeBlockSegmentRenderer;
import uno.anahata.asi.swing.agi.AgiPanel;
import uno.anahata.asi.swing.agi.message.part.text.AbstractCodeBlockSegmentRenderer;
import uno.anahata.asi.swing.agi.render.editorkit.EditorKitProvider;

/**
 * NetBeans-specific implementation of {@link EditorKitProvider}.
 * <p>
 * This implementation leverages a virtual Memory FileSystem to authoritatively 
 * resolve MIME types for non-existent or proposed files, ensuring high-fidelity 
 * syntax highlighting even for files that haven't been created on disk yet.
 * </p>
 *
 * @author anahata
 */
public class NetBeansEditorKitProvider implements EditorKitProvider {
    private static final Logger logger = Logger.getLogger(NetBeansEditorKitProvider.class.getName());

    private final Map<String, String> languageToMimeTypeMap;

    /**
     * Constructs a new NetBeansEditorKitProvider and initializes the 
     * language-to-MIME-type mapping cache.
     */
    public NetBeansEditorKitProvider() {
        logger.log(Level.INFO, "Initializing NetBeansEditorKitProvider language cache...");
        this.languageToMimeTypeMap = new ConcurrentHashMap<>();
        
        // 1. Initialize authoritative baseline mappings
        Map<String, String> hardcodedMap = Map.of(
            "java", "text/x-java",
            "xml", "text/xml",
            "html", "text/html",
            "css", "text/css",
            "javascript", "text/javascript",
            "json", "text/x-json",
            "sql", "text/x-sql",
            "properties", "text/x-properties",
            "bash", "text/x-sh",
            "sh", "text/x-sh"
        );
        languageToMimeTypeMap.putAll(hardcodedMap);
        
        // 2. Augment with IDE-discovered mappings from MimeUtils
        try {
            languageToMimeTypeMap.putAll(MimeUtils.getExtensionToMimeTypeMap());
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to load IDE-discovered MIME mappings", e);
        }
        
        logger.log(Level.INFO, "Cache initialization complete. Final cache size: {0}", languageToMimeTypeMap.size());
    }

    /**
     * {@inheritDoc}
     * <p>
     * Implementation details:
     * Resolves the NetBeans MIME type for the given language ID and retrieves 
     * the corresponding {@link EditorKit} from the global {@code MimeLookup}.
     * </p>
     */
    @Override
    public EditorKit getEditorKitForLanguage(String language) {
        String langLower = (language == null) ? "" : language.toLowerCase().trim();
        String mimeType = languageToMimeTypeMap.get(langLower);

        if (mimeType != null) {
            EditorKit kit = MimeLookup.getLookup(mimeType).lookup(EditorKit.class);
            if (kit != null) {
                return kit;
            }
        }
        return MimeLookup.getLookup("text/plain").lookup(EditorKit.class);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Implementation details:
     * Authoritatively resolves the language for a filename:
     * 1. If the file exists on disk, uses its real MIME type.
     * 2. If the file is virtual (e.g., createTextFile proposal), uses a 
     *    transient Memory-FS probe to resolve the type via NetBeans resolvers.
     * </p>
     */
    @Override
    public String getLanguageForFile(String filename) {
        String mime = null;
        
        // 1. Authoritative Disk Check
        java.io.File file = new java.io.File(filename);
        FileObject fo = FileUtil.toFileObject(file);
        if (fo != null) {
            mime = fo.getMIMEType();
        }

        // 2. Memory-FS Prober (authoritative for virtual/proposed files)
        if (mime == null || "content/unknown".equals(mime)) {
            try {
                // We create a transient memory filesystem for the probe to avoid serialization issues
                FileSystem mfs = FileUtil.createMemoryFileSystem();
                FileObject root = mfs.getRoot();
                FileObject probe = root.createData(file.getName());
                mime = probe.getMIMEType();
            } catch (IOException ex) {
                logger.log(Level.FINE, "MIME probe failed for {0}", filename);
            }
        }

        if (mime == null) {
            return "text";
        }
        
        // Reverse mapping search
        for (Map.Entry<String, String> entry : languageToMimeTypeMap.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(mime)) {
                return entry.getKey();
            }
        }
        
        // Fallback: use the subtype part of the mime (e.g., x-java -> java)
        int slash = mime.lastIndexOf('/');
        String sub = (slash != -1) ? mime.substring(slash + 1) : mime;
        return sub.replace("x-", "");
    }

    /**
     * {@inheritDoc}
     * <p>
     * Implementation details:
     * Instantiates a {@link NbCodeBlockSegmentRenderer} to provide 
     * full NetBeans editor fidelity.
     * </p>
     */
    @Override
    public AbstractCodeBlockSegmentRenderer createRenderer(AgiPanel agiPanel, String content, String language) {
        return new NbCodeBlockSegmentRenderer(agiPanel, content, language, getEditorKitForLanguage(language));
    }
}
