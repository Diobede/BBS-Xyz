package mchorse.bbs_mod.forms.utils;

import net.minecraft.client.render.VertexConsumer;

/**
 * Wraps a VertexConsumer and overrides light() calls to use context.light
 * This makes blocks render with the same lighting as models/items
 */
public class LightOverrideVertexConsumer implements VertexConsumer
{
    private final VertexConsumer delegate;
    private final int blockLight;
    private final int skyLight;
    
    public LightOverrideVertexConsumer(VertexConsumer delegate, int contextLight)
    {
        this.delegate = delegate;
        /* Extract light components from context.light (same as CubicCubeRenderer) */
        this.blockLight = contextLight & 0xFFFF;
        this.skyLight = (contextLight >> 16) & 0xFFFF;
    }
    
    @Override
    public VertexConsumer vertex(double x, double y, double z)
    {
        this.delegate.vertex(x, y, z);
        return this;
    }
    
    @Override
    public VertexConsumer color(int red, int green, int blue, int alpha)
    {
        this.delegate.color(red, green, blue, alpha);
        return this;
    }
    
    @Override
    public VertexConsumer texture(float u, float v)
    {
        this.delegate.texture(u, v);
        return this;
    }
    
    @Override
    public VertexConsumer overlay(int u, int v)
    {
        this.delegate.overlay(u, v);
        return this;
    }
    
    @Override
    public VertexConsumer light(int u, int v)
    {
        /* OVERRIDE: Use context.light instead of world lighting */
        this.delegate.light(this.blockLight, this.skyLight);
        return this;
    }
    
    @Override
    public VertexConsumer normal(float x, float y, float z)
    {
        this.delegate.normal(x, y, z);
        return this;
    }
    
    @Override
    public void next()
    {
        this.delegate.next();
    }
    
    @Override
    public void fixedColor(int red, int green, int blue, int alpha)
    {
        this.delegate.fixedColor(red, green, blue, alpha);
    }
    
    @Override
    public void unfixColor()
    {
        this.delegate.unfixColor();
    }
}
