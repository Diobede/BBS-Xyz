package mchorse.bbs_mod.ui.forms.editors.forms;

import mchorse.bbs_mod.forms.forms.AAAParticleForm;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.panels.UIAAAParticleFormPanel;
import mchorse.bbs_mod.ui.utils.icons.Icons;

/**
 * AAA Particle Form Editor
 *
 * UI editor for configuring AAA Particle (Effekseer) forms.
 * Provides controls for effect selection, playback, and transform settings.
 */
public class UIAAAParticleForm extends UIForm<AAAParticleForm>
{
    public UIAAAParticleFormPanel particlePanel;

    public UIAAAParticleForm()
    {
        super();

        this.particlePanel = new UIAAAParticleFormPanel(this);

        this.registerPanel(this.particlePanel, UIKeys.FORMS_EDITORS_AAA_PARTICLE_TITLE, Icons.PARTICLE);
        this.registerDefaultPanels();

        this.defaultPanel = this.particlePanel;
    }
}
