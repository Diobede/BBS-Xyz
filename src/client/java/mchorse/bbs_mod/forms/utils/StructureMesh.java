package mchorse.bbs_mod.forms.utils;

import com.mojang.blaze3d.systems.RenderSystem;
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
    public void render(MatrixStack matrices, GameRenderer gameRenderer)
    {
        if (!this.uploaded || this.vertexBuffer == null)
        {
            return;
        }

        // Setup render state for block rendering
        RenderSystem.setShader(GameRenderer::getPositionColorTexLightmapProgram);
        RenderSystem.setShaderTexture(0, net.minecraft.client.texture.SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
        
        // Draw the mesh
        this.vertexBuffer.bind();
        this.vertexBuffer.draw();
        VertexBuffer.unbind();
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
