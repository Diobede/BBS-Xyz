package mchorse.bbs_mod.forms.forms;

import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.core.ValueLink;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;

/**
 * AAA Particle Form
 *
 * This form renders Effekseer particles using the AAA Particles mod.
 * Supports bone attachment, looping, duration control, and transform animations.
 */
public class AAAParticleForm extends Form
{
    /* Effect path - the .efkefc file path relative to assets/effeks/ */
    public final ValueLink effect = new ValueLink("effect", null);

    /* Bone attachment - attach particle to a specific bone on parent model */
    public final ValueString bone = new ValueString("bone", "");

    /* Playback control */
    public final ValueBoolean paused = new ValueBoolean("paused", false);
    public final ValueBoolean loop = new ValueBoolean("loop", true);
    public final ValueInt duration = new ValueInt("duration", 0);

    /* Speed multiplier for particle playback */
    public final ValueFloat speed = new ValueFloat("speed", 1F);

    /* Particle scale multiplier (separate from transform scale) */
    public final ValueFloat particleScale = new ValueFloat("particleScale", 1F);

    /* Sorting layer - higher values render on top */
    public final ValueInt sortingLayer = new ValueInt("sortingLayer", 0);

    /* Whether to use world space or local space */
    public final ValueBoolean worldSpace = new ValueBoolean("worldSpace", false);

    public AAAParticleForm()
    {
        super();

        this.effect.invisible();

        this.add(this.effect);
        this.add(this.bone);
        this.add(this.paused);
        this.add(this.loop);
        this.add(this.duration);
        this.add(this.speed);
        this.add(this.particleScale);
        this.add(this.sortingLayer);
        this.add(this.worldSpace);
    }

    @Override
    public String getDefaultDisplayName()
    {
        Link effectLink = this.effect.get();

        if (effectLink == null)
        {
            return "none";
        }

        String path = effectLink.path;

        if (path == null || path.isEmpty())
        {
            return "none";
        }

        /* Extract just the filename without extension */
        int lastSlash = path.lastIndexOf('/');
        String name = lastSlash >= 0 ? path.substring(lastSlash + 1) : path;

        if (name.endsWith(".efkefc"))
        {
            name = name.substring(0, name.length() - 7);
        }

        return name;
    }
}
