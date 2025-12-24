package mchorse.bbs_mod.forms.utils;

import net.minecraft.client.gl.ShaderProgram;
import mchorse.bbs_mod.client.BBSShaders;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;

/**
 * Optimized GPU mesh for structure rendering
 * 
 * Stores entire structure as single VBO for 1-draw-call rendering
 */
public class StructureMesh
{
    private VertexBuffer vertexBuffer;
    private final int blockCount;
    private boolean uploaded;

    public StructureMesh(BufferBuilder.BuiltBuffer buffer, int blockCount)
    {
        this.blockCount = blockCount;
        this.uploaded = false;
        
        // Create and upload VBO
        this.vertexBuffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        this.vertexBuffer.bind();
        this.vertexBuffer.upload(buffer);
        VertexBuffer.unbind();
        
        this.uploaded = true;
    }

    /**
     * Render the entire structure mesh in one draw call
     */
    public void render(MatrixStack matrices)
    {
        this.render(matrices, false);
    }

    /**
     * Render with specific transparency mode
     */
    public void render(MatrixStack matrices, boolean translucent)
    {
        this.render(matrices, translucent, -1);
    }

    /**
     * Render with specific transparency mode and light override
     */
    public void render(MatrixStack matrices, boolean translucent, int lightOverride)
    {
        if (!this.uploaded || this.vertexBuffer == null)
        {
            return;
        }

        if (translucent)
        {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableCull(); // Enable culling for translucent too, to hide internal faces when inside
        }
        else
        {
            RenderSystem.disableBlend();
            RenderSystem.enableCull(); // Solid geometry should have back-face culling enabled
        }

        if (lightOverride >= 0)
        {
            RenderSystem.setShader(BBSShaders::getStructureProgram);
            ShaderProgram shader = BBSShaders.getStructureProgram();
            
            if (shader != null && shader.getUniform("UseLightOverride") != null)
            {
                shader.getUniform("UseLightOverride").set(1);
                shader.getUniform("LightOverrideValue").set(lightOverride & 0xFFFF, (lightOverride >> 16) & 0xFFFF);
            }
        }
        else
        {
            if (translucent)
            {
                RenderSystem.setShader(GameRenderer::getRenderTypeTranslucentProgram);
            }
            else
            {
                RenderSystem.setShader(GameRenderer::getRenderTypeSolidProgram);
            }
        }

        RenderSystem.setShaderTexture(0, net.minecraft.client.texture.SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
        MinecraftClient.getInstance().gameRenderer.getLightmapTextureManager().enable();
        
        this.vertexBuffer.bind();
        this.vertexBuffer.draw(matrices.peek().getPositionMatrix(), RenderSystem.getProjectionMatrix(), RenderSystem.getShader());
        VertexBuffer.unbind();

        if (translucent)
        {
            RenderSystem.disableBlend();
            RenderSystem.enableCull(); // Restore culling state
        }
        else
        {
             RenderSystem.disableCull(); // Restore culling state (or leave it enabled? usually defaults to disabled in GUI, enabled in world)
             // Best practice is to reset to a known state or not touch if we don't know. 
             // However, since we explicitly changed it, we should probably reset it or let the next renderer handle it.
             // For safety in this mod context, let's reset to default (often culling is OFF for entities/forms).
             RenderSystem.disableCull();
        }
        
        /* Reset uniform state */
        if (lightOverride >= 0)
        {
            ShaderProgram shader = BBSShaders.getStructureProgram();
            
            if (shader != null && shader.getUniform("UseLightOverride") != null)
            {
                shader.getUniform("UseLightOverride").set(0);
            }
        }
    }

    public int getBlockCount()
    {
        return this.blockCount;
    }

    public boolean isUploaded()
    {
        return this.uploaded;
    }

    /**
     * Delete GPU resources
     */
    public void delete()
    {
        if (this.vertexBuffer != null)
        {
            this.vertexBuffer.close();
            this.vertexBuffer = null;
        }
        
        this.uploaded = false;
    }
}
