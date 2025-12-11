package mchorse.bbs_mod.film.replays;

import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

/**
 * Represents a replay group with its own visible and color keyframe channels.
 * When a group's properties change, they affect all replays within the group.
 */
public class ReplayGroup extends ValueGroup
{
    public final KeyframeChannel<Boolean> visible = new KeyframeChannel<>("visible", KeyframeFactories.BOOLEAN);
    public final KeyframeChannel<Color> color = new KeyframeChannel<>("color", KeyframeFactories.COLOR);

    private String groupName;

    public ReplayGroup(String groupName)
    {
        super(groupName);

        this.groupName = groupName;

        this.add(this.visible);
        this.add(this.color);
    }

    public String getGroupName()
    {
        return this.groupName;
    }

    public void setGroupName(String groupName)
    {
        this.groupName = groupName;
        this.setId(groupName);
    }

    public void shift(float tick)
    {
        for (int i = 0; i < this.visible.getKeyframes().size(); i++)
        {
            this.visible.getKeyframes().get(i).setTick(this.visible.getKeyframes().get(i).getTick() + tick);
        }

        for (int i = 0; i < this.color.getKeyframes().size(); i++)
        {
            this.color.getKeyframes().get(i).setTick(this.color.getKeyframes().get(i).getTick() + tick);
        }
    }

    /**
     * Apply this group's visible value at the given tick
     */
    public Boolean getVisible(float tick)
    {
        return this.visible.interpolate(tick);
    }

    /**
     * Apply this group's color value at the given tick
     */
    public Color getColor(float tick)
    {
        return this.color.interpolate(tick);
    }
}
