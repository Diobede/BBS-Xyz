package mchorse.bbs_mod.forms.utils;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockRenderView;
import net.minecraft.world.LightType;
import net.minecraft.world.chunk.light.LightingProvider;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.Blocks;
import net.minecraft.world.biome.ColorResolver;
import net.minecraft.world.biome.Biome;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

/**
 * Minimal world view to allow rendering blocks with culling.
 * 
 * Provides block states and basic methods required by BlockRenderView.
 * Lighting and color are delegated to the ClientWorld if it exists; 
 * in the absence of a world, safe values are returned.
 * 
 * Ported from BBS Mod Fork (VirtualBlockRenderView).
 */
public class VirtualBlockRenderView implements BlockRenderView
{
    private final Map<BlockPos, BlockState> states = new HashMap<>();
    private final Map<BlockPos, Integer> localBlockLight = new HashMap<>();
    private int bottomY = 0;
    private int topY = 256;

    private Identifier biomeOverrideId = null;
    private Biome biomeOverride = null;

    private BlockPos worldAnchor = BlockPos.ORIGIN;
    private int baseDx = 0;
    private int baseDy = 0;
    private int baseDz = 0;
    private boolean lightsEnabled = true;
    private int lightIntensity = 15;
    private boolean forceMaxSkyLight = false;
    
    // For strict culling self-masking logic
    private boolean strictCulling = true;
    private BlockState cullState = null;

    public static class Entry
    {
        public final BlockState state;
        public final BlockPos pos;

        public Entry(BlockState state, BlockPos pos)
        {
            this.state = state;
            this.pos = pos;
        }
    }

    public VirtualBlockRenderView(List<Entry> entries)
    {
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;

        ArrayList<BlockPos> emitters = new ArrayList<>();
        ArrayList<Integer> emitterLevels = new ArrayList<>();

        for (Entry e : entries)
        {
            this.states.put(e.pos, e.state == null ? Blocks.AIR.getDefaultState() : e.state);

            BlockState st = this.states.get(e.pos);
            int lum = st == null ? 0 : st.getLuminance();
            if (lum > 0)
            {
                emitters.add(e.pos);
                emitterLevels.add(lum);
            }

            if (e.pos.getY() < minY) minY = e.pos.getY();
            if (e.pos.getY() > maxY) maxY = e.pos.getY();
        }

        if (minY != Integer.MAX_VALUE && maxY != Integer.MIN_VALUE)
        {
            this.bottomY = minY;
            this.topY = maxY;
        }

        if (!emitters.isEmpty() && !this.states.isEmpty())
        {
            for (Map.Entry<BlockPos, BlockState> target : this.states.entrySet())
            {
                BlockPos tp = target.getKey();
                int max = 0;
                for (int i = 0; i < emitters.size(); i++)
                {
                    BlockPos sp = emitters.get(i);
                    int L = emitterLevels.get(i);
                    int dist = Math.abs(sp.getX() - tp.getX()) + Math.abs(sp.getY() - tp.getY()) + Math.abs(sp.getZ() - tp.getZ());
                    int contrib = L - dist;
                    if (contrib > max)
                    {
                        max = contrib;
                        if (max >= 15)
                        {
                            max = 15;
                            break;
                        }
                    }
                }
                if (max > 0)
                {
                    this.localBlockLight.put(tp, max);
                }
            }
        }
    }
    
    public void setStrictCulling(boolean strict)
    {
        this.strictCulling = strict;
    }
    
    public void setCullState(BlockState state)
    {
        this.cullState = state;
    }

    public VirtualBlockRenderView setWorldAnchor(BlockPos anchor, int baseDx, int baseDy, int baseDz)
    {
        this.worldAnchor = anchor == null ? BlockPos.ORIGIN : anchor;
        this.baseDx = baseDx;
        this.baseDy = baseDy;
        this.baseDz = baseDz;
        return this;
    }

    public VirtualBlockRenderView setBiomeOverride(String biomeId)
    {
        if (biomeId == null || biomeId.isEmpty())
        {
            this.biomeOverrideId = null;
            this.biomeOverride = null;
            return this;
        }

        try
        {
            this.biomeOverrideId = new Identifier(biomeId);
            if (MinecraftClient.getInstance().world != null)
            {
                Registry<Biome> reg = MinecraftClient.getInstance().world.getRegistryManager().get(RegistryKeys.BIOME);
                this.biomeOverride = reg.get(this.biomeOverrideId);
            }
            else
            {
                this.biomeOverride = null;
            }
        }
        catch (Throwable t)
        {
            this.biomeOverrideId = null;
            this.biomeOverride = null;
        }

        return this;
    }

    public VirtualBlockRenderView setLightsEnabled(boolean enabled)
    {
        this.lightsEnabled = enabled;
        return this;
    }

    public VirtualBlockRenderView setLightIntensity(int level)
    {
        if (level < 1) level = 1;
        if (level > 15) level = 15;
        this.lightIntensity = level;
        return this;
    }

    public VirtualBlockRenderView setForceMaxSkyLight(boolean force)
    {
        this.forceMaxSkyLight = force;
        return this;
    }

    @Override
    public BlockEntity getBlockEntity(BlockPos pos)
    {
        return null;
    }

    @Override
    public BlockState getBlockState(BlockPos pos)
    {
        /* Strict Culling / Self-Masking Logic integration */
        if (this.strictCulling)
        {
            if (this.states.containsKey(pos))
            {
                 // If we are spoofing a cull state (e.g. for self-culling), return it
                 if (this.cullState != null)
                 {
                     return this.cullState;
                 }
                 // Otherwise return the actual state
                 return this.states.get(pos);
            }
            
            // If strict culling is on and block is missing, return AIR (render face)
            return Blocks.AIR.getDefaultState();
        }
        
        BlockState state = this.states.get(pos);
        return state != null ? state : Blocks.AIR.getDefaultState();
    }

    @Override
    public FluidState getFluidState(BlockPos pos)
    {
        return Fluids.EMPTY.getDefaultState();
    }

    @Override
    public int getLuminance(BlockPos pos)
    {
        if (!this.lightsEnabled)
        {
            return 0;
        }
        // Avoid recursive getBlockState call if strict culling is doing magic
        BlockState s = this.states.get(pos); 
        int lum = s == null ? 0 : s.getLuminance();
        return Math.min(lum, this.lightIntensity);
    }

    @Override
    public int getMaxLightLevel()
    {
        return 15;
    }

    @Override
    public float getBrightness(Direction direction, boolean shaded)
    {
        if (MinecraftClient.getInstance().world != null)
        {
            return MinecraftClient.getInstance().world.getBrightness(direction, shaded);
        }
        return 1.0F;
    }

    @Override
    public LightingProvider getLightingProvider()
    {
        if (MinecraftClient.getInstance().world != null)
        {
            return MinecraftClient.getInstance().world.getLightingProvider();
        }
        return null;
    }

    @Override
    public int getColor(BlockPos pos, ColorResolver colorResolver)
    {
        if (this.biomeOverride != null)
        {
            int wx = this.worldAnchor.getX() + this.baseDx + pos.getX();
            int wz = this.worldAnchor.getZ() + this.baseDz + pos.getZ();
            return colorResolver.getColor(this.biomeOverride, wx, wz);
        }

        if (MinecraftClient.getInstance().world != null)
        {
            BlockPos worldPos = this.worldAnchor.add(this.baseDx + pos.getX(), this.baseDy + pos.getY(), this.baseDz + pos.getZ());
            return MinecraftClient.getInstance().world.getColor(worldPos, colorResolver);
        }

        return 0xFFFFFF;
    }

    @Override
    public int getLightLevel(LightType type, BlockPos pos)
    {
        if (this.forceMaxSkyLight || MinecraftClient.getInstance().world == null)
        {
            if (type == LightType.SKY)
            {
                return 15;
            }
            else
            {
                return this.lightsEnabled ? Math.min(this.localBlockLight.getOrDefault(pos, 0), this.lightIntensity) : 0;
            }
        }

        int worldLevel = 0;
        BlockPos worldPos = this.worldAnchor.add(this.baseDx + pos.getX(), this.baseDy + pos.getY(), this.baseDz + pos.getZ());
        worldLevel = MinecraftClient.getInstance().world.getLightLevel(type, worldPos);

        if (type == LightType.BLOCK)
        {
            int local = this.lightsEnabled ? Math.min(this.localBlockLight.getOrDefault(pos, 0), this.lightIntensity) : 0;
            return Math.max(worldLevel, local);
        }

        return worldLevel;
    }

    @Override
    public int getBaseLightLevel(BlockPos pos, int ambientDarkness)
    {
        if (this.forceMaxSkyLight || MinecraftClient.getInstance().world == null)
        {
            return 15;
        }

        BlockPos worldPos = this.worldAnchor.add(this.baseDx + pos.getX(), this.baseDy + pos.getY(), this.baseDz + pos.getZ());
        int worldBase = MinecraftClient.getInstance().world.getBaseLightLevel(worldPos, ambientDarkness);

        int localBlock = this.lightsEnabled ? Math.min(this.localBlockLight.getOrDefault(pos, 0), this.lightIntensity) : 0;
        return Math.max(worldBase, localBlock);
    }

    @Override
    public boolean isSkyVisible(BlockPos pos)
    {
        if (this.forceMaxSkyLight || MinecraftClient.getInstance().world == null)
        {
            return true;
        }

        BlockPos worldPos = this.worldAnchor.add(this.baseDx + pos.getX(), this.baseDy + pos.getY(), this.baseDz + pos.getZ());
        return MinecraftClient.getInstance().world.isSkyVisible(worldPos);
    }

    @Override
    public int getBottomY()
    {
        return this.bottomY;
    }

    @Override
    public int getTopY()
    {
        return this.topY;
    }

    @Override
    public int getHeight()
    {
        return this.topY - this.bottomY + 1;
    }
}
