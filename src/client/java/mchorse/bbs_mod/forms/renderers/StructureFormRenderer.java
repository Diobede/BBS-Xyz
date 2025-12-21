package mchorse.bbs_mod.forms.renderers;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.forms.CustomVertexConsumerProvider;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.StructureForm;
import mchorse.bbs_mod.forms.utils.LightOverrideVertexConsumer;
import mchorse.bbs_mod.forms.utils.StructureLoader;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.joml.Vectors;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Renderer for StructureForm - simple per-block rendering with BBS lighting
 */
public class StructureFormRenderer extends FormRenderer<StructureForm>
{
    /* Structure cache to avoid reloading same structure */
    private static final Map<String, StructureLoader.Structure> STRUCTURE_CACHE = new HashMap<>();
    
    private StructureLoader.Structure cachedStructure;
    private String lastPath;
    private int currentLodLevel = 0;

    public StructureFormRenderer(StructureForm form)
    {
        super(form);
    }

    @Override
    public void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        StructureLoader.Structure structure = this.loadStructure();

        if (structure == null)
        {
            return;
        }

        context.batcher.getContext().draw();

        CustomVertexConsumerProvider consumers = FormUtilsClient.getProvider();
        MatrixStack matrices = context.batcher.getContext().getMatrices();

        Matrix4f uiMatrix = ModelFormRenderer.getUIMatrix(context, x1, y1, x2, y2);

        matrices.push();
        MatrixStackUtils.multiply(matrices, uiMatrix);
        matrices.scale(this.form.uiScale.get(), this.form.uiScale.get(), this.form.uiScale.get());

        /* Center the structure */
        matrices.translate(-structure.size.getX() / 2F, 0F, -structure.size.getZ() / 2F);

        matrices.peek().getNormalMatrix().getScale(Vectors.EMPTY_3F);
        matrices.peek().getNormalMatrix().scale(1F / Vectors.EMPTY_3F.x, -1F / Vectors.EMPTY_3F.y, 1F / Vectors.EMPTY_3F.z);

        consumers.setUI(true);

        /* Render all blocks */
        this.renderBlocks(structure, matrices, consumers, LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV);

        consumers.draw();
        consumers.setUI(false);

        matrices.pop();
    }

    @Override
    protected void render3D(FormRenderingContext context)
    {
        StructureLoader.Structure structure = this.loadStructure();

        if (structure == null)
        {
            return;
        }

        /* Check block count limit */
        if (structure.getBlockCount() > this.form.maxBlocks.get())
        {
            return;
        }

        /* Calculate distance to structure for LOD */
        MinecraftClient mc = MinecraftClient.getInstance();
        Vec3d cameraPos = mc.gameRenderer.getCamera().getPos();
        Vec3d structureCenter = new Vec3d(
            context.entity.getX() + structure.size.getX() / 2.0,
            context.entity.getY() + structure.size.getY() / 2.0,
            context.entity.getZ() + structure.size.getZ() / 2.0
        );
        double distance = cameraPos.distanceTo(structureCenter);
        
        /* Determine LOD level based on distance */
        int lodLevel = this.calculateLodLevel(distance);
        this.currentLodLevel = lodLevel;

        CustomVertexConsumerProvider consumers = FormUtilsClient.getProvider();
        
        /* Use context lighting (same as other form renderers) */
        int light = context.light;

        context.stack.push();

        /* Center the structure */
        context.stack.translate(-structure.size.getX() / 2F, 0F, -structure.size.getZ() / 2F);

        if (context.isPicking())
        {
            CustomVertexConsumerProvider.hijackVertexFormat((layer) ->
            {
                this.setupTarget(context, BBSShaders.getPickerModelsProgram());
                RenderSystem.setShader(BBSShaders::getPickerModelsProgram);
            });

            light = 0;
        }
        else
        {
            CustomVertexConsumerProvider.hijackVertexFormat((l) -> RenderSystem.enableBlend());
        }

        /* Render based on LOD level */
        if (lodLevel == 0)
        {
            /* LOD 0: Full detail rendering */
            this.renderBlocks(structure, context.stack, consumers, light, context.overlay);
        }
        else if (lodLevel == 1)
        {
            /* LOD 1: Simplified mesh (50-70% polygon reduction) */
            /* TODO: Implement simplified mesh generation */
            this.renderBlocks(structure, context.stack, consumers, light, context.overlay);
        }
        else
        {
            /* LOD 2: Billboard or bounding box */
            /* TODO: Implement billboard rendering */
            /* For now, don't render at extreme distances */
        }

        consumers.draw();

        CustomVertexConsumerProvider.clearRunnables();

        context.stack.pop();

        RenderSystem.enableDepthTest();
    }

    /**
     * Render blocks with context.light (same approach as ModelFormRenderer)
     * Uses LightOverrideVertexConsumer to inject context.light into vertices
     */
    private void renderBlocks(StructureLoader.Structure structure, MatrixStack matrices, CustomVertexConsumerProvider consumers, int light, int overlay)
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        
        for (StructureLoader.BlockEntry entry : structure.blocks)
        {
            BlockState state = entry.state;
            
            /* Only render MODEL blocks */
            if (state.getRenderType() != net.minecraft.block.BlockRenderType.MODEL)
            {
                continue;
            }
            
            /* Skip occluded blocks */
            if (structure.isOccluded(entry.pos))
            {
                continue;
            }
            
            BlockPos pos = entry.pos;
            
            matrices.push();
            matrices.translate(pos.getX(), pos.getY(), pos.getZ());
            
            /* Wrap vertex consumer to override light() calls with context.light */
            VertexConsumer originalConsumer = consumers.getBuffer(RenderLayer.getSolid());
            VertexConsumer wrappedConsumer = new LightOverrideVertexConsumer(originalConsumer, light);
            
            /* Render block - lighting will be overridden by our wrapper */
            mc.getBlockRenderManager().renderBlock(
                state,
                pos,
                mc.world,
                matrices,
                wrappedConsumer,
                true,
                Random.create()
            );
            
            matrices.pop();
        }
    }

    /**
     * Load structure from file, with caching
     */
    private StructureLoader.Structure loadStructure()
    {
        String path = this.form.structurePath.get();

        if (path.isEmpty())
        {
            return null;
        }

        /* Check if we already loaded this structure */
        if (path.equals(this.lastPath) && this.cachedStructure != null)
        {
            return this.cachedStructure;
        }

        /* Check global cache */
        if (this.form.useCache.get() && STRUCTURE_CACHE.containsKey(path))
        {
            this.lastPath = path;
            this.cachedStructure = STRUCTURE_CACHE.get(path);

            return this.cachedStructure;
        }

        /* Load structure from file */
        File file = this.resolveStructureFile(path);

        if (file == null || !file.exists())
        {
            System.err.println("Structure file not found: " + path + " (resolved to: " + file + ")");
            return null;
        }

        try
        {
            StructureLoader.Structure structure = StructureLoader.load(file);

            this.lastPath = path;
            this.cachedStructure = structure;

            if (this.form.useCache.get())
            {
                STRUCTURE_CACHE.put(path, structure);
            }

            return structure;
        }
        catch (Exception e)
        {
            System.err.println("Failed to load structure: " + path);
            e.printStackTrace();

            return null;
        }
    }

    /**
     * Resolve structure file path
     */
    private File resolveStructureFile(String path)
    {
        /* Handle world: prefix */
        if (path.startsWith("world:"))
        {
            String relativePath = path.substring("world:".length());
            MinecraftClient mc = MinecraftClient.getInstance();
            
            if (mc.world != null && mc.isInSingleplayer() && mc.getServer() != null)
            {
                try
                {
                    File generatedFolder = mc.getServer().getSavePath(net.minecraft.util.WorldSavePath.GENERATED).toFile();
                    File worldStructures = new File(new File(generatedFolder, "minecraft"), "structures");
                    File file = new File(worldStructures, relativePath);
                    
                    if (file.exists())
                    {
                        return file;
                    }
                }
                catch (Exception e)
                {
                    System.err.println("Failed to resolve world structure path: " + path);
                    e.printStackTrace();
                }
            }
            
            return null;
        }
        
        /* Handle minecraft: prefix */
        if (path.startsWith("minecraft:"))
        {
            String relativePath = path.substring("minecraft:".length());
            MinecraftClient mc = MinecraftClient.getInstance();
            File minecraftStructures = new File(mc.runDirectory, "structures");
            File file = new File(minecraftStructures, relativePath);
            
            if (file.exists())
            {
                return file;
            }
            
            return null;
        }
        
        /* Handle assets: prefix (BBS asset provider) */
        if (path.startsWith("assets:"))
        {
            String relativePath = path.substring("assets:".length());
            File file = new File(BBSMod.getAssetsFolder(), relativePath);
            
            if (file.exists())
            {
                return file;
            }
            
            return null;
        }
        
        /* Absolute path */
        File file = new File(path);

        if (file.exists())
        {
            return file;
        }

        /* Relative to structures folder */
        file = new File(BBSMod.getAssetsFolder(), "structures/" + path);

        if (file.exists())
        {
            return file;
        }

        /* Try with .nbt extension */
        if (!path.endsWith(".nbt"))
        {
            file = new File(BBSMod.getAssetsFolder(), "structures/" + path + ".nbt");

            if (file.exists())
            {
                return file;
            }
        }

        return null;
    }

    /**
    /**
     * Clear the global structure cache
     */
    public static void clearCache()
    {
        STRUCTURE_CACHE.clear();
    }

    /**
     * Clear this instance's cached structure
     */
    public void clearInstanceCache()
    {
        this.cachedStructure = null;
        this.lastPath = null;
    }

    /**
     * Calculate LOD level based on camera distance
     * 
     * @param distance Distance from camera to structure center
     * @return LOD level (0 = full detail, 1 = simplified, 2 = billboard)
     */
    private int calculateLodLevel(double distance)
    {
        float lod1 = this.form.lodDistance1.get();
        float lod2 = this.form.lodDistance2.get();
        
        if (distance < lod1)
        {
            return 0; /* Full detail */
        }
        else if (distance < lod2)
        {
            return 1; /* Simplified mesh */
        }
        else
        {
            return 2; /* Billboard/bounding box */
        }
    }
}