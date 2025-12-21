package mchorse.bbs_mod.ui.forms.editors.forms;

import mchorse.bbs_mod.forms.forms.StructureForm;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.panels.UIStructureFormPanel;
import mchorse.bbs_mod.ui.utils.icons.Icons;

/**
 * Structure Form UI Editor
 *
 * This class provides the form editor for Structure forms.
 */
public class UIStructureForm extends UIForm<StructureForm>
{
    public UIStructureForm()
    {
        super();

        this.defaultPanel = new UIStructureFormPanel(this);

        this.registerPanel(this.defaultPanel, UIKeys.FORMS_EDITORS_STRUCTURE_TITLE, Icons.BLOCK);
        this.registerDefaultPanels();
    }
}
