package mchorse.bbs_mod.forms.utils;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.forms.utils.StructureLoader.BlockEntry;
import mchorse.bbs_mod.forms.utils.StructureLoader.Structure;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import mchorse.bbs_mod.forms.utils.VirtualBlockRenderView;

/**
 * Generates optimized meshes for structure chunks (16x16x16)
 * Handles mesh generation, optimization, and buffer management
 */
public class ChunkMeshGenerator
{
    private static final int CHUNK_SIZE = 16;

    /**
     * Represents a 3D chunk position
     */
    public record ChunkPos3D(int x, int y, int z)
    {
        public long asLong()
        {
            return ((long) x & 0xFFFFF) | (((long) y & 0xFFFFF) << 20) | (((long) z & 0xFFFFF) << 40);
        }
    }

    /**
     * Container for chunk meshes (solid, translucent, etc.)
     */
    public static class ChunkRenderData
    {
        public final ChunkPos3D pos;
        public StructureMesh solidMesh;
        public StructureMesh translucentMesh;
        public boolean isEmpty = true;

        public ChunkRenderData(ChunkPos3D pos)
        {
            this.pos = pos;
        }

        public void delete()
        {
            if (this.solidMesh != null) this.solidMesh.delete();
            if (this.translucentMesh != null) this.translucentMesh.delete();
        }
    }

    /**
     * Generate meshes for the entire structure
     */
    public static Map<Long, ChunkRenderData> generate(Structure structure)
    {
        Map<Long, ChunkRenderData> chunks = new HashMap<>();
        Map<ChunkPos3D, Map<RenderLayer, BufferBuilder>> builders = new HashMap<>();

        MinecraftClient mc = MinecraftClient.getInstance();
        MatrixStack matrices = new MatrixStack();
        Random random = Random.create();
        List<VirtualBlockRenderView.Entry> entries = new ArrayList<>();
        for (BlockEntry entry : structure.blocks)
        {
            entries.add(new VirtualBlockRenderView.Entry(entry.state, entry.pos));
        }
        VirtualBlockRenderView blockView = new VirtualBlockRenderView(entries);
        blockView.setStrictCulling(true);

        // 1. Bucket blocks into chunks and render into builders
        for (BlockEntry entry : structure.blocks)
        {
            if (structure.isOccluded(entry.pos))
            {
                continue;
            }

            BlockState state = entry.state;
            if (state.getRenderType() != BlockRenderType.MODEL)
            {
                continue;
            }

            BlockPos pos = entry.pos;
            int cx = pos.getX() >> 4;
            int cy = pos.getY() >> 4;
            int cz = pos.getZ() >> 4;
            ChunkPos3D chunkPos = new ChunkPos3D(cx, cy, cz);

            // Determine render layer
            RenderLayer layer = state.isOpaque() ? RenderLayer.getSolid() : RenderLayer.getTranslucent();
            
            // Get or create builder
            builders.putIfAbsent(chunkPos, new HashMap<>());
            Map<RenderLayer, BufferBuilder> chunkBuilders = builders.get(chunkPos);
            
            if (!chunkBuilders.containsKey(layer))
            {
                BufferBuilder builder = new BufferBuilder(layer.getExpectedBufferSize());
                builder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR_TEXTURE_LIGHT_NORMAL);
                chunkBuilders.put(layer, builder);
            }
            
            BufferBuilder builder = chunkBuilders.get(layer);
            // Wrap with LightOverrideVertexConsumer to bake full brightness (since we can't update lightmap per frame for static meshes)
            VertexConsumer consumer = new LightOverrideVertexConsumer(builder, 15728880); // MAX_LIGHT_COORDINATE (240, 240)

            matrices.push();
            matrices.translate(pos.getX(), pos.getY(), pos.getZ());

            /* Set the cull state to the current block state.
             * This tricks the renderer into thinking the neighbor is the same block,
             * forcing aggressive culling (e.g., Glass next to Glass).
             */
            blockView.setCullState(state);

            // Render block
            mc.getBlockRenderManager().renderBlock(
                state,
                pos,
                blockView,
                matrices,
                consumer,
                true,
                random
            );

            matrices.pop();
        }

        // 2. Finish builders and create meshes
        for (Map.Entry<ChunkPos3D, Map<RenderLayer, BufferBuilder>> entry : builders.entrySet())
        {
            ChunkPos3D pos = entry.getKey();
            Map<RenderLayer, BufferBuilder> chunkBuilders = entry.getValue();
            ChunkRenderData data = new ChunkRenderData(pos);
            boolean hasData = false;

            if (chunkBuilders.containsKey(RenderLayer.getSolid()))
            {
                BufferBuilder builder = chunkBuilders.get(RenderLayer.getSolid());
                BufferBuilder.BuiltBuffer built = builder.end();
                // TODO: Count blocks properly? passing 0 for now as it seems unused for rendering
                data.solidMesh = new StructureMesh(built, 0); 
                hasData = true;
            }

            if (chunkBuilders.containsKey(RenderLayer.getTranslucent()))
            {
                BufferBuilder builder = chunkBuilders.get(RenderLayer.getTranslucent());
                BufferBuilder.BuiltBuffer built = builder.end();
                data.translucentMesh = new StructureMesh(built, 0);
                hasData = true;
            }

            if (hasData)
            {
                data.isEmpty = false;
                chunks.put(pos.asLong(), data);
            }
        }

        return chunks;
    }
}
