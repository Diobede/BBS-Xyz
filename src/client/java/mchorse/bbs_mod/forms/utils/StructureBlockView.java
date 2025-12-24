package mchorse.bbs_mod.forms.utils;

import mchorse.bbs_mod.forms.utils.StructureLoader.Structure;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockRenderView;
import net.minecraft.world.biome.ColorResolver;
import net.minecraft.world.chunk.light.LightingProvider;

public class StructureBlockView implements BlockRenderView
{
    private final Structure structure;
    private boolean strictCulling = true;
    private BlockState cullState;

    public StructureBlockView(Structure structure)
    {
        this.structure = structure;
    }

    public void setStrictCulling(boolean strictCulling)
    {
        this.strictCulling = strictCulling;
    }

    public void setCullState(BlockState state)
    {
        this.cullState = state;
    }

    @Override
    public float getBrightness(Direction direction, boolean shaded)
    {
        return 1.0F;
    }

    @Override
    public LightingProvider getLightingProvider()
    {
        return null;
    }

    @Override
    public int getColor(BlockPos pos, ColorResolver colorResolver)
    {
        return -1;
    }

    @Override
    public BlockEntity getBlockEntity(BlockPos pos)
    {
        return null;
    }

    @Override
    public BlockState getBlockState(BlockPos pos)
    {
        if (this.structure == null || this.structure.blockMap == null)
        {
            return Blocks.AIR.getDefaultState();
        }
        
        /* In strict culling mode, check if the block exists in the structure.
         * If it does, return the cullState (usually the block itself) to force face culling.
         * If it doesn't (it's air), return Air to allow face rendering.
         */
        if (this.strictCulling)
        {
            if (this.structure.blockMap.containsKey(pos))
            {
                return this.cullState != null ? this.cullState : Blocks.BEDROCK.getDefaultState();
            }
            
            return Blocks.AIR.getDefaultState();
        }
        
        return this.structure.blockMap.getOrDefault(pos, Blocks.AIR.getDefaultState());
    }

    @Override
    public FluidState getFluidState(BlockPos pos)
    {
        return this.getBlockState(pos).getFluidState();
    }
    
    @Override
    public int getHeight()
    {
        return 256;
    }

    @Override
    public int getBottomY()
    {
        return 0;
    }
}
