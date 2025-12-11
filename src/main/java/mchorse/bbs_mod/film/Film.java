package mchorse.bbs_mod.film;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.film.replays.Inventory;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.ReplayGroup;
import mchorse.bbs_mod.film.replays.Replays;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.clips.Clips;

import java.util.HashMap;
import java.util.Map;

public class Film extends ValueGroup
{
    public final Clips camera = new Clips("camera", BBSMod.getFactoryCameraClips());
    public final Replays replays = new Replays("replays");
    public final Map<String, ReplayGroup> replayGroups = new HashMap<>();

    public final Inventory inventory = new Inventory("inventory");
    public final ValueFloat hp = new ValueFloat("hp", 20F);
    public final ValueFloat hunger = new ValueFloat("hunger", 20F);
    public final ValueInt xpLevel = new ValueInt("xp_level", 0);
    public final ValueFloat xpProgress = new ValueFloat("xp_progress", 0F);

    public Film()
    {
        super("");

        this.add(this.camera);
        this.add(this.replays);

        this.add(this.inventory);
        this.add(this.hp);
        this.add(this.hunger);
        this.add(this.xpLevel);
        this.add(this.xpProgress);
    }

    public Replay getFirstPersonReplay()
    {
        for (Replay replay : this.replays.getList())
        {
            if (replay.fp.get())
            {
                return replay;
            }
        }

        return null;
    }

    public boolean hasFirstPerson()
    {
        return this.getFirstPersonReplay() != null;
    }

    /**
     * Sync replay groups based on current replays
     */
    public void syncReplayGroups()
    {
        /* Collect all unique group names from replays */
        Map<String, ReplayGroup> newGroups = new HashMap<>();

        for (Replay replay : this.replays.getList())
        {
            String groupName = replay.group.get();

            if (!groupName.isEmpty() && !newGroups.containsKey(groupName))
            {
                /* Try to reuse existing group data if available */
                ReplayGroup group = this.replayGroups.getOrDefault(groupName, new ReplayGroup(groupName));

                newGroups.put(groupName, group);
            }
        }

        this.replayGroups.clear();
        this.replayGroups.putAll(newGroups);
    }

    /**
     * Get or create a replay group
     */
    public ReplayGroup getOrCreateGroup(String groupName)
    {
        if (groupName.isEmpty())
        {
            return null;
        }

        return this.replayGroups.computeIfAbsent(groupName, ReplayGroup::new);
    }

    /**
     * Get a replay group by name
     */
    public ReplayGroup getGroup(String groupName)
    {
        return this.replayGroups.get(groupName);
    }

    @Override
    public BaseType toData()
    {
        MapType data = (MapType) super.toData();

        /* Serialize replay groups */
        if (!this.replayGroups.isEmpty())
        {
            MapType groupsData = new MapType();

            for (Map.Entry<String, ReplayGroup> entry : this.replayGroups.entrySet())
            {
                groupsData.put(entry.getKey(), entry.getValue().toData());
            }

            data.put("replay_groups", groupsData);
        }

        return data;
    }

    @Override
    public void fromData(BaseType data)
    {
        super.fromData(data);

        /* Deserialize replay groups */
        if (data.isMap())
        {
            MapType map = data.asMap();

            if (map.has("replay_groups"))
            {
                BaseType groupsData = map.get("replay_groups");

                if (groupsData.isMap())
                {
                    this.replayGroups.clear();

                    for (Map.Entry<String, BaseType> entry : groupsData.asMap())
                    {
                        ReplayGroup group = new ReplayGroup(entry.getKey());
                        
                        group.fromData(entry.getValue());
                        this.replayGroups.put(entry.getKey(), group);
                    }
                }
            }
        }
    }
}
