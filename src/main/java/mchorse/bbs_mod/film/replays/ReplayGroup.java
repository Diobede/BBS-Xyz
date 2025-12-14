package mchorse.bbs_mod.film.replays;

import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import mchorse.bbs_mod.utils.pose.Transform;

/**
 * Represents a replay group with its own visible, color, transform, and texture keyframe channels.
 * When a group's properties change, they affect all replays within the group.
 */
public class ReplayGroup extends ValueGroup
{
    public final KeyframeChannel<Boolean> visible = new KeyframeChannel<>("visible", KeyframeFactories.BOOLEAN);
    public final KeyframeChannel<Color> color = new KeyframeChannel<>("color", KeyframeFactories.COLOR);
    public final KeyframeChannel<Transform> transform = new KeyframeChannel<>("transform", KeyframeFactories.TRANSFORM);
    public final KeyframeChannel<Link> texture = new KeyframeChannel<>("texture", KeyframeFactories.LINK);
    public final KeyframeChannel<Link> moreTexture1 = new KeyframeChannel<>("more_texture1", KeyframeFactories.LINK);
    public final KeyframeChannel<Link> moreTexture2 = new KeyframeChannel<>("more_texture2", KeyframeFactories.LINK);
    public final KeyframeChannel<Boolean> randomTexture = new KeyframeChannel<>("random_texture", KeyframeFactories.BOOLEAN);
    public final KeyframeChannel<Integer> randomTextureSeed = new KeyframeChannel<>("random_texture_seed", KeyframeFactories.INTEGER);
    public final FormProperties properties = new FormProperties("properties");

    private String groupName;

    public ReplayGroup(String groupName)
    {
        super(groupName);

        this.groupName = groupName;

        this.add(this.visible);
        this.add(this.color);
        this.add(this.transform);
        this.add(this.texture);
        this.add(this.moreTexture1);
        this.add(this.moreTexture2);
        this.add(this.randomTexture);
        this.add(this.randomTextureSeed);
        this.add(this.properties);
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

        for (int i = 0; i < this.transform.getKeyframes().size(); i++)
        {
            this.transform.getKeyframes().get(i).setTick(this.transform.getKeyframes().get(i).getTick() + tick);
        }

        for (int i = 0; i < this.texture.getKeyframes().size(); i++)
        {
            this.texture.getKeyframes().get(i).setTick(this.texture.getKeyframes().get(i).getTick() + tick);
        }

        for (int i = 0; i < this.moreTexture1.getKeyframes().size(); i++)
        {
            this.moreTexture1.getKeyframes().get(i).setTick(this.moreTexture1.getKeyframes().get(i).getTick() + tick);
        }

        for (int i = 0; i < this.moreTexture2.getKeyframes().size(); i++)
        {
            this.moreTexture2.getKeyframes().get(i).setTick(this.moreTexture2.getKeyframes().get(i).getTick() + tick);
        }

        for (int i = 0; i < this.randomTexture.getKeyframes().size(); i++)
        {
            this.randomTexture.getKeyframes().get(i).setTick(this.randomTexture.getKeyframes().get(i).getTick() + tick);
        }

        for (int i = 0; i < this.randomTextureSeed.getKeyframes().size(); i++)
        {
            this.randomTextureSeed.getKeyframes().get(i).setTick(this.randomTextureSeed.getKeyframes().get(i).getTick() + tick);
        }

        this.properties.shift(tick);
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

    /**
     * Apply this group's transform value at the given tick
     */
    public Transform getTransform(float tick)
    {
        return this.transform.interpolate(tick);
    }

    /**
     * Get the texture for this group at the given tick.
     * If random texture is enabled, randomly selects between texture, more_texture1, and more_texture2.
     */
    public Link getTexture(float tick, long entityId)
    {
        Boolean randomEnabled = this.randomTexture.interpolate(tick);

        if (randomEnabled == null || !randomEnabled)
        {
            return this.texture.interpolate(tick);
        }

        /* Random texture selection based on seed and entity ID only (not tick, so it stays constant) */
        Integer seed = this.randomTextureSeed.interpolate(tick);

        if (seed == null)
        {
            seed = 1;
        }

        long hash = seed + entityId;
        int random = (int) ((hash * 2654435761L) % 3);

        if (random < 0)
        {
            random = -random;
        }

        Link result = null;

        if (random == 0)
        {
            result = this.texture.interpolate(tick);
        }
        else if (random == 1)
        {
            result = this.moreTexture1.interpolate(tick);
        }
        else if (random == 2)
        {
            result = this.moreTexture2.interpolate(tick);
        }

        /* Fall back to main texture if selected texture is null */
        if (result == null)
        {
            result = this.texture.interpolate(tick);
        }

        return result;
    }
}
