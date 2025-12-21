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

    /* Rendering options */
    public UIToggle useCache;
    public UITrackpad maxBlocks;

    /* LOD settings */
    public UITrackpad lodDistance1;
    public UITrackpad lodDistance2;

    public UIStructureFormPanel(UIForm editor)
    {
        super(editor);

        /* Structure path */
        this.structurePath = new UITextbox(500, (t) -> this.form.structurePath.set(t));
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

        /* Cache toggle */
        this.useCache = new UIToggle(UIKeys.FORMS_EDITORS_STRUCTURE_USE_CACHE, (b) -> this.form.useCache.set(b.getValue()));
        this.useCache.tooltip(UIKeys.FORMS_EDITORS_STRUCTURE_USE_CACHE_TOOLTIP);

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
            this.structurePath,
            this.pickStructure,
            UI.label(UIKeys.FORMS_EDITORS_STRUCTURE_OPTIONS_LABEL),
            this.useCache,
            UI.label(UIKeys.FORMS_EDITORS_STRUCTURE_MAX_BLOCKS_LABEL),
            this.maxBlocks,
            UI.label(UIKeys.FORMS_EDITORS_STRUCTURE_LOD_LABEL),
            UI.label(UIKeys.FORMS_EDITORS_STRUCTURE_LOD1_LABEL),
            this.lodDistance1,
            UI.label(UIKeys.FORMS_EDITORS_STRUCTURE_LOD2_LABEL),
            this.lodDistance2
        );
    }

    @Override
    public void startEdit(StructureForm form)
    {
        super.startEdit(form);

        this.structurePath.setText(form.structurePath.get());
        this.useCache.setValue(form.useCache.get());
        this.maxBlocks.setValue(form.maxBlocks.get());
        this.lodDistance1.setValue(form.lodDistance1.get());
        this.lodDistance2.setValue(form.lodDistance2.get());
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
