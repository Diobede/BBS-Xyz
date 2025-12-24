package mchorse.bbs_mod.forms.utils;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.math.Direction;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Utility class for loading vanilla structure block .nbt files
 */
public class StructureLoader
{
    /**
     * Represents a loaded structure with block data
     */
    public static class Structure
    {
        public final Vec3i size;
        public final List<BlockEntry> blocks;
        public final Map<BlockPos, BlockState> blockMap;
        private Set<BlockPos> occludedBlocks;
        private Map<BlockPos, java.util.EnumSet<Direction>> visibleFaces;

        public Structure(Vec3i size, List<BlockEntry> blocks)
        {
            this.size = size;
            this.blocks = blocks;
            this.blockMap = new HashMap<>();

            for (BlockEntry entry : blocks)
            {
                this.blockMap.put(entry.pos, entry.state);
            }
        }

        public int getBlockCount()
        {
            return this.blocks.size();
        }

        public BlockState getBlockAt(BlockPos pos)
        {
            return this.blockMap.get(pos);
        }
        
        public boolean isOccluded(BlockPos pos)
        {
            if (this.occludedBlocks == null)
            {
                this.computeOcclusion();
            }
            
            return this.occludedBlocks.contains(pos);
        }
        
        /**
         * Get the set of visible faces for a given block position.
         * Lazily computes face visibility the first time it's needed.
         */
        public java.util.EnumSet<Direction> getVisibleFaces(BlockPos pos)
        {
            if (this.visibleFaces == null)
            {
                this.computeVisibleFaces();
            }
            
            java.util.EnumSet<Direction> faces = this.visibleFaces.get(pos);
            return faces == null ? java.util.EnumSet.noneOf(Direction.class) : faces;
        }
        
        private void computeOcclusion()
        {
            this.occludedBlocks = new HashSet<>();
            
            for (BlockEntry entry : this.blocks)
            {
                if (this.isFullyOccluded(entry.pos))
                {
                    this.occludedBlocks.add(entry.pos);
                }
            }
        }
        
        private boolean isFullyOccluded(BlockPos pos)
        {
            // Check all 6 faces
            for (Direction direction : Direction.values())
            {
                BlockPos neighbor = pos.offset(direction);
                BlockState neighborState = this.getBlockAt(neighbor);
                
                // If any neighbor is air or transparent, block is visible
                if (neighborState == null || neighborState.isAir() || !neighborState.isOpaque())
                {
                    return false;
                }
            }

            // All neighbors are opaque - block is fully hidden
            return true;
        }
        
        /**
         * Compute visible faces for each block based on adjacency.
         * A face is considered visible if the adjacent block is:
         * - air or missing in the structure; or
         * - non-opaque (e.g., glass, leaves, fences).
         */
        private void computeVisibleFaces()
        {
            this.visibleFaces = new HashMap<>();
            
            for (BlockEntry entry : this.blocks)
            {
                BlockPos pos = entry.pos;
                java.util.EnumSet<Direction> faces = java.util.EnumSet.noneOf(Direction.class);
                
                for (Direction dir : Direction.values())
                {
                    BlockPos neighborPos = pos.offset(dir);
                    BlockState neighborState = this.getBlockAt(neighborPos);
                    
                    boolean exposed = neighborState == null || neighborState.isAir() || !neighborState.isOpaque();
                    
                    if (exposed)
                    {
                        faces.add(dir);
                    }
                }
                
                if (!faces.isEmpty())
                {
                    this.visibleFaces.put(pos, faces);
                }
            }
        }
    }

    /**
     * Represents a single block in the structure
     */
    public static class BlockEntry
    {
        public final BlockPos pos;
        public final BlockState state;

        public BlockEntry(BlockPos pos, BlockState state)
        {
            this.pos = pos;
            this.state = state;
        }
    }

    /**
     * Load a structure from an NBT file
     */
    public static Structure load(File file) throws IOException
    {
        if (!file.exists() || !file.isFile())
        {
            throw new IOException("Structure file does not exist: " + file.getAbsolutePath());
        }

        try (FileInputStream fis = new FileInputStream(file);
             DataInputStream dis = new DataInputStream(fis))
        {
            NbtCompound nbt = net.minecraft.nbt.NbtIo.readCompressed(dis, net.minecraft.nbt.NbtTagSizeTracker.ofUnlimitedBytes());

            return load(nbt);
        }
    }

    /**
     * Load a structure from NBT compound
     */
    public static Structure load(NbtCompound nbt)
    {
        /* Read structure size */
        NbtList sizeList = nbt.getList("size", NbtElement.INT_TYPE);
        Vec3i size = new Vec3i(sizeList.getInt(0), sizeList.getInt(1), sizeList.getInt(2));

        /* Read block palette */
        NbtList paletteList = nbt.getList("palette", NbtElement.COMPOUND_TYPE);
        List<BlockState> palette = new ArrayList<>();

        for (int i = 0; i < paletteList.size(); i++)
        {
            NbtCompound blockNbt = paletteList.getCompound(i);
            BlockState state = NbtHelper.toBlockState(Registries.BLOCK.getReadOnlyWrapper(), blockNbt);

            palette.add(state);
        }

        /* Read block positions */
        NbtList blocksList = nbt.getList("blocks", NbtElement.COMPOUND_TYPE);
        List<BlockEntry> blocks = new ArrayList<>();

        for (int i = 0; i < blocksList.size(); i++)
        {
            NbtCompound blockNbt = blocksList.getCompound(i);

            /* Read position */
            NbtList posList = blockNbt.getList("pos", NbtElement.INT_TYPE);
            BlockPos pos = new BlockPos(posList.getInt(0), posList.getInt(1), posList.getInt(2));

            /* Read state from palette */
            int stateIndex = blockNbt.getInt("state");

            if (stateIndex >= 0 && stateIndex < palette.size())
            {
                BlockState state = palette.get(stateIndex);

                /* Skip air blocks */
                if (!state.isAir())
                {
                    blocks.add(new BlockEntry(pos, state));
                }
            }
        }

        return new Structure(size, blocks);
    }

    /**
     * Check if a file is a valid structure file
     */
    public static boolean isValidStructureFile(File file)
    {
        if (!file.exists() || !file.isFile())
        {
            return false;
        }

        if (!file.getName().endsWith(".nbt"))
        {
            return false;
        }

        try
        {
            load(file);

            return true;
        }
        catch (Exception e)
        {
            return false;
        }
    }
}
