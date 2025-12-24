package mchorse.bbs_mod.forms.forms;

import mchorse.bbs_mod.settings.values.core.ValueColor;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.forms.forms.utils.PivotSettings;
import mchorse.bbs_mod.settings.values.misc.ValuePivotSettings;
import mchorse.bbs_mod.settings.values.misc.ValueStructureLightSettings;
import mchorse.bbs_mod.forms.forms.utils.StructureLightSettings;
import mchorse.bbs_mod.utils.colors.Color;

/**
 * Structure Form
 * 
 * Allows rendering vanilla structure block .nbt files as a form.
 * Supports LOD (Level of Detail) rendering for performance.
 */
public class StructureForm extends Form
{
    /** Path to the structure file (relative to config/bbs/assets/structures/ or absolute) */
    public final ValueString structurePath = new ValueString("structure_path", "");
    
    /** Whether to cache the loaded structure in memory */
    public final ValueBoolean useCache = new ValueBoolean("use_cache", true);
    
    /** Maximum number of blocks to render (performance safety) */
    public final ValueInt maxBlocks = new ValueInt("max_blocks", 10000);
    
    /** Distance at which to switch to LOD 1 (Simplified) */
    public final ValueFloat lodDistance1 = new ValueFloat("lod_distance_1", 64F);
    
    /** Distance at which to switch to LOD 2 (Bounding Box) */
    public final ValueFloat lodDistance2 = new ValueFloat("lod_distance_2", 256F);
    
    /** Debug: visualize occlusion culling faces */
    public final ValueBoolean debugCulling = new ValueBoolean("debug_culling", false);

    /** Enable chunk meshing optimization */
    public final ValueBoolean chunkMeshing = new ValueBoolean("chunk_meshing", true);

    /** Enable dynamic lighting for chunk meshes */
    public final ValueBoolean lighting = new ValueBoolean("lighting", true);

    /** Debug: visualize chunk boundaries */
    public final ValueBoolean debugChunkBoundaries = new ValueBoolean("debug_chunk_boundaries", false);

    /* cml_fork fields */
    /** Ruta relativa dentro de assets al archivo de estructura (.nbt), por ejemplo: "structures/casa.nbt" */
    public final ValueString structureFile = new ValueString("structure_file", "");
    
    /** Structure color tint */
    public final ValueColor color = new ValueColor("color", new Color().set(0xffffffff));

    /** Biome ID for biome-dependent block coloring */
    public final ValueString biomeId = new ValueString("biome_id", "");

    /** Whether the structure emits light */
    public final ValueBoolean emitLight = new ValueBoolean("emit_light", false);

    /** Light intensity if emitting light (0-15) */
    public final ValueInt lightIntensity = new ValueInt("light_intensity", 15);
    
    /** Pista unificada de luz de estructura (enabled + intensity) */
    public final ValueStructureLightSettings structureLight = new ValueStructureLightSettings("structure_light", new StructureLightSettings(false, 15));
    
    /** Aplica el tinte global también a Block Entities (cofres, carteles, etc.) */
    public final ValueBoolean tintBlockEntities = new ValueBoolean("tint_block_entities", false);
    
    /** Pivote manual en coordenadas de bloque (permite decimales) */
    public final ValueFloat pivotX = new ValueFloat("pivot_x", 0f);
    public final ValueFloat pivotY = new ValueFloat("pivot_y", 0f);
    public final ValueFloat pivotZ = new ValueFloat("pivot_z", 0f);
    
    /** Pista unificada de pivote: auto + X/Y/Z (W sin uso) */
    public final ValuePivotSettings pivot = new ValuePivotSettings("pivot", new PivotSettings(true, 0f, 0f, 0f));
    
    /** Cuando está activo, el renderer calcula el centro automáticamente y omite el pivote manual */
    public final ValueBoolean autoPivot = new ValueBoolean("auto_pivot", true);

    public StructureForm()
    {
        super();

        this.add(this.structurePath);
        this.add(this.useCache);
        this.add(this.maxBlocks);
        this.add(this.lodDistance1);
        this.add(this.lodDistance2);
        this.add(this.debugCulling);
        this.add(this.chunkMeshing);
        this.add(this.lighting);
        this.add(this.debugChunkBoundaries);
        
        this.add(this.structureFile);
        this.add(this.color);
        this.add(this.biomeId);
        this.add(this.emitLight);
        this.add(this.lightIntensity);
        
        this.tintBlockEntities.invisible();
        this.add(this.tintBlockEntities);
        this.add(this.structureLight);
        
        this.pivotX.invisible();
        this.pivotY.invisible();
        this.pivotZ.invisible();
        this.add(this.pivotX);
        this.add(this.pivotY);
        this.add(this.pivotZ);
        
        this.emitLight.invisible();
        this.lightIntensity.invisible();
        
        this.pivot.invisible();
        this.add(this.pivot);
        this.autoPivot.invisible();
        this.add(this.autoPivot);
    }

    @Override
    protected String getDefaultDisplayName()
    {
        String path = this.structureFile.get();
        if (path == null || path.isEmpty())
        {
            path = this.structurePath.get();
        }

        if (path == null || path.isEmpty())
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
    
    @Override
    public String getTrackName(String property)
    {
        int slash = property.lastIndexOf('/');
        String prefix = slash == -1 ? "" : property.substring(0, slash + 1);
        String last = slash == -1 ? property : property.substring(slash + 1);

        String mapped = last;
        if ("structure_file".equals(last)) mapped = "structure";
        else if ("biome_id".equals(last)) mapped = "biome";
        /* Mostrar el nombre visual como 'structure_light' en lugar de 'light' */
        else if ("structure_light".equals(last)) mapped = "structure_light";

        return super.getTrackName(prefix + mapped);
    }
}
