package mchorse.bbs_mod.forms.forms;

import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;

public class StructureForm extends Form
{
    public final ValueString structurePath = new ValueString("structure_path", "");
    public final ValueBoolean useCache = new ValueBoolean("use_cache", true);
    public final ValueInt maxBlocks = new ValueInt("max_blocks", 10000);
    public final ValueFloat lodDistance1 = new ValueFloat("lod_distance_1", 64F);
    public final ValueFloat lodDistance2 = new ValueFloat("lod_distance_2", 256F);

    public StructureForm()
    {
        super();

        this.add(this.structurePath);
        this.add(this.useCache);
        this.add(this.maxBlocks);
        this.add(this.lodDistance1);
        this.add(this.lodDistance2);
    }

    @Override
    protected String getDefaultDisplayName()
    {
        String path = this.structurePath.get();

        if (path.isEmpty())
        {
            return "structure";
        }

        /* Extract filename from path */
        int lastSlash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));

        if (lastSlash >= 0 && lastSlash < path.length() - 1)
        {
            path = path.substring(lastSlash + 1);
        }

        /* Remove .nbt extension if present */
        if (path.endsWith(".nbt"))
        {
            path = path.substring(0, path.length() - 4);
        }

        return path;
    }
}
