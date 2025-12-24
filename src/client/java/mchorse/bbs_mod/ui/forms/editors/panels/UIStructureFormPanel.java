package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.forms.forms.StructureForm;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIVanillaStructureList;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIListOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIStructureOverlayPanel;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.utils.UI;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.WorldSavePath;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.forms.utils.StructureLoader;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;

/**
 * Structure Form Panel
 *
 * UI panel for editing Structure form properties.
 * Provides controls for structure file selection and rendering options.
 */
public class UIStructureFormPanel extends UIFormPanel<StructureForm>
{
    /* Structure file path */
    public UITextbox structurePath;
    public UIButton pickStructure;
    public UILabel statusLabel;

    /* Rendering options */
    public UIToggle useCache;
    public UIToggle chunkMeshing;
    public UIToggle lighting;
    public UIToggle debugChunkBoundaries;
    public UITrackpad maxBlocks;

    /* LOD settings */
    public UITrackpad lodDistance1;
    public UITrackpad lodDistance2;

    public UIStructureFormPanel(UIForm editor)
    {
        super(editor);

        /* Structure path */
        this.structurePath = new UITextbox(500, (t) -> {
            this.form.structurePath.set(t);
            this.validatePath(t);
        });
        this.structurePath.tooltip(UIKeys.FORMS_EDITORS_STRUCTURE_PATH_TOOLTIP);

        this.pickStructure = new UIButton(UIKeys.FORMS_EDITORS_STRUCTURE_PICK, (b) ->
        {
            /* Create visual structure browser with three tabs */
            UIStructureOverlayPanel picker = new UIStructureOverlayPanel(
                (link) ->
                {
                    if (link != null)
                    {
                        String path = link.toString();
                        this.form.structurePath.set(path);
                        this.structurePath.setText(path);
                        this.validatePath(path);
                        
                        /* Clear renderer cache and trigger reload */
                        if (mchorse.bbs_mod.forms.FormUtilsClient.getRenderer(this.form) instanceof mchorse.bbs_mod.forms.renderers.StructureFormRenderer renderer)
                        {
                            renderer.clearInstanceCache();
                        }
                        
                        this.editor.startEdit(this.form);
                    }
                },
                this.getContext()
            );

            UIOverlay.addOverlay(this.getContext(), picker);
        });

        this.statusLabel = UI.label(UIKeys.PANELS_MODALS_EMPTY).color(Colors.RED);

        /* Cache toggle */
        this.useCache = new UIToggle(UIKeys.FORMS_EDITORS_STRUCTURE_USE_CACHE, (b) -> this.form.useCache.set(b.getValue()));
        this.useCache.tooltip(UIKeys.FORMS_EDITORS_STRUCTURE_USE_CACHE_TOOLTIP);

        /* Chunk Meshing toggles */
        this.chunkMeshing = new UIToggle(UIKeys.FORMS_EDITORS_STRUCTURE_CHUNK_MESHING, (b) -> this.form.chunkMeshing.set(b.getValue()));
        this.chunkMeshing.tooltip(UIKeys.FORMS_EDITORS_STRUCTURE_CHUNK_MESHING_TOOLTIP);
        
        this.lighting = new UIToggle(UIKeys.FORMS_EDITORS_STRUCTURE_LIGHTING, (b) -> this.form.lighting.set(b.getValue()));
        this.lighting.tooltip(UIKeys.FORMS_EDITORS_STRUCTURE_LIGHTING_TOOLTIP);
        
        this.debugChunkBoundaries = new UIToggle(UIKeys.FORMS_EDITORS_STRUCTURE_DEBUG_CHUNKS, (b) -> this.form.debugChunkBoundaries.set(b.getValue()));
        this.debugChunkBoundaries.tooltip(UIKeys.FORMS_EDITORS_STRUCTURE_DEBUG_CHUNKS_TOOLTIP);

        /* Max blocks limiter */
        this.maxBlocks = new UITrackpad((v) -> this.form.maxBlocks.set(v.intValue()));
        this.maxBlocks.limit(1, 100000).integer();
        this.maxBlocks.tooltip(UIKeys.FORMS_EDITORS_STRUCTURE_MAX_BLOCKS_TOOLTIP);

        /* LOD distances */
        this.lodDistance1 = new UITrackpad((v) -> this.form.lodDistance1.set(v.floatValue()));
        this.lodDistance1.limit(0, 1000);
        this.lodDistance1.tooltip(UIKeys.FORMS_EDITORS_STRUCTURE_LOD1_TOOLTIP);

        this.lodDistance2 = new UITrackpad((v) -> this.form.lodDistance2.set(v.floatValue()));
        this.lodDistance2.limit(0, 1000);
        this.lodDistance2.tooltip(UIKeys.FORMS_EDITORS_STRUCTURE_LOD2_TOOLTIP);

        /* Layout */
        this.options.add(
            UI.label(UIKeys.FORMS_EDITORS_STRUCTURE_PATH_LABEL),
            UI.row(this.structurePath, this.pickStructure),
            this.statusLabel,

            UI.label(UIKeys.FORMS_EDITORS_STRUCTURE_OPTIONS_LABEL).marginTop(12),
            this.useCache,
            this.chunkMeshing,
            this.debugChunkBoundaries,
            
            UI.label(UIKeys.FORMS_EDITORS_STRUCTURE_MAX_BLOCKS_LABEL),
            this.maxBlocks,
            
            UI.label(UIKeys.FORMS_EDITORS_STRUCTURE_LOD_LABEL).marginTop(12),
            UI.row(
                UI.column(UI.label(UIKeys.FORMS_EDITORS_STRUCTURE_LOD1_LABEL), this.lodDistance1),
                UI.column(UI.label(UIKeys.FORMS_EDITORS_STRUCTURE_LOD2_LABEL), this.lodDistance2)
            )
        );
    }

    private void validatePath(String path)
    {
        if (path.isEmpty())
        {
            this.statusLabel.label = UIKeys.PANELS_MODALS_EMPTY;
            return;
        }
        
        /* Basic validation */
        boolean valid = true;
        
        if (!path.startsWith("world:") && !path.startsWith("minecraft:") && !path.startsWith("assets:"))
        {
            /* Check if file exists (basic check) */
            File file = new File(mchorse.bbs_mod.BBSMod.getAssetsFolder(), "structures/" + path + (path.endsWith(".nbt") ? "" : ".nbt"));
            if (!file.exists())
            {
                file = new File(path); // Absolute
                if (!file.exists())
                {
                    valid = false;
                }
            }
        }
        
        if (valid)
        {
            this.statusLabel.label = UIKeys.PANELS_MODALS_EMPTY;
        }
        else
        {
            this.statusLabel.label = mchorse.bbs_mod.l10n.keys.IKey.raw("File not found!");
            this.statusLabel.color(Colors.RED);
        }
    }

    @Override
    public void startEdit(StructureForm form)
    {
        super.startEdit(form);

        this.structurePath.setText(form.structurePath.get());
        this.useCache.setValue(form.useCache.get());
        this.chunkMeshing.setValue(form.chunkMeshing.get());
        this.debugChunkBoundaries.setValue(form.debugChunkBoundaries.get());
        this.maxBlocks.setValue(form.maxBlocks.get());
        this.lodDistance1.setValue(form.lodDistance1.get());
        this.lodDistance2.setValue(form.lodDistance2.get());
        
        this.validatePath(form.structurePath.get());
    }

    /**
     * Recursively scan a folder for .nbt structure files
     */
    private void scanStructureFolder(File root, File current, String prefix, List<String> results)
    {
        if (!current.exists() || !current.isDirectory())
        {
            return;
        }

        File[] files = current.listFiles();
        
        if (files == null)
        {
            return;
        }

        for (File file : files)
        {
            if (file.isDirectory())
            {
                this.scanStructureFolder(root, file, prefix, results);
            }
            else if (file.getName().endsWith(".nbt"))
            {
                /* Get relative path from root */
                String relativePath = root.toPath().relativize(file.toPath()).toString().replace('\\', '/');
                
                results.add(prefix + relativePath);
            }
        }
    }
}
