package mchorse.bbs_mod.forms.renderers;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.forms.ITickable;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.AAAParticleForm;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.joml.Vectors;
import mod.chloeprime.aaaparticles.api.client.effekseer.ParticleEmitter;
import mod.chloeprime.aaaparticles.client.registry.EffectDefinition;
import mod.chloeprime.aaaparticles.client.registry.EffectRegistry;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * AAA Particle Form Renderer
 *
 * Renders Effekseer particles using the AAA Particles mod.
 * Supports bone attachment, looping, duration control, and context-aware rendering.
 */
public class AAAParticleFormRenderer extends FormRenderer<AAAParticleForm> implements ITickable
{
    /* Track emitter state per form instance */
    private ParticleEmitter emitter;
    private Identifier lastEffectId;
    private boolean lastPaused = false;

    /* Unique identifier for named emitters */
    private final Identifier emitterName;

    public AAAParticleFormRenderer(AAAParticleForm form)
    {
        super(form);

        this.emitterName = new Identifier(BBSMod.MOD_ID, "form_" + UUID.randomUUID().toString().replace("-", ""));
    }

    public ParticleEmitter getEmitter()
    {
        return this.emitter;
    }

    /**
     * Get the effect identifier from the form's effect link
     */
    private Identifier getEffectId()
    {
        Link effect = this.form.effect.get();

        if (effect == null)
        {
            return null;
        }

        String path = effect.path;

        if (path == null || path.isEmpty())
        {
            return null;
        }

        /* Remove .efkefc extension if present */
        if (path.endsWith(".efkefc"))
        {
            path = path.substring(0, path.length() - 7);
        }

        /* Remove effeks/ prefix if present */
        if (path.startsWith("effeks/"))
        {
            path = path.substring(7);
        }

        return new Identifier(effect.source, path);
    }

    /**
     * Ensure the emitter is created and up to date
     */
    private void ensureEmitter()
    {
        if (BBSRendering.isIrisShadowPass())
        {
            return;
        }

        Identifier effectId = this.getEffectId();

        if (effectId == null)
        {
            this.stopEmitter();
            return;
        }

        /* Check if effect changed */
        if (this.lastEffectId == null || !this.lastEffectId.equals(effectId))
        {
            this.stopEmitter();
            this.lastEffectId = effectId;
        }

        /* Get effect definition - try EffectRegistry first (AAA Particles' standard way) */
        EffectDefinition definition = EffectRegistry.get(effectId);

        if (definition == null)
        {
            /* Try BBS external assets loader - this injects into EffectRegistry */
            definition = BBSEffectLoader.getOrLoad(effectId);
        }

        if (definition == null)
        {
            return;
        }

        /* Create emitter if needed */
        if (this.emitter == null)
        {
            this.emitter = definition.play(ParticleEmitter.Type.WORLD, this.emitterName);
        }

        /* Skip the rest if emitter doesn't exist */
        if (this.emitter == null)
        {
            return;
        }

        /* Update pause state */
        boolean paused = this.form.paused.get();

        if (paused != this.lastPaused)
        {
            if (paused)
            {
                this.emitter.pause();
            }
            else
            {
                this.emitter.resume();
            }

            this.lastPaused = paused;
        }

        /* Update visibility */
        this.emitter.setVisibility(this.form.visible.get());
    }

    /**
     * Stop and clean up the current emitter
     */
    private void stopEmitter()
    {
        if (this.emitter != null)
        {
            this.emitter.stop();
            this.emitter = null;
        }
    }

    @Override
    protected void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        /* Render a placeholder icon in UI since we can't easily render Effekseer in 2D */
        int cx = (x1 + x2) / 2;
        int cy = (y1 + y2) / 2;
        int size = Math.min(x2 - x1, y2 - y1) / 3;

        /* Draw a simple particle icon representation */
        context.batcher.iconArea(
            mchorse.bbs_mod.ui.utils.icons.Icons.PARTICLE,
            cx - size / 2,
            cy - size / 2,
            cx + size / 2,
            cy + size / 2
        );
    }

    @Override
    protected void render3D(FormRenderingContext context)
    {
        this.ensureEmitter();

        if (this.emitter == null || !this.emitter.exists())
        {
            return;
        }

        if (BBSRendering.isIrisShadowPass())
        {
            return;
        }

        /* Get position the same way BBS ParticleFormRenderer does it */
        /* This properly handles the camera view rotation */
        Matrix4f matrix = new Matrix4f(RenderSystem.getInverseViewRotationMatrix());
        matrix.mul(context.stack.peek().getPositionMatrix());

        /* Extract translation and convert to world coordinates */
        Vector3d translation = new Vector3d(matrix.getTranslation(Vectors.TEMP_3F));
        translation.add(context.camera.position.x, context.camera.position.y, context.camera.position.z);

        /* Apply scale from form settings */
        float scale = this.form.particleScale.get();

        /* Set emitter position (world coordinates) and scale */
        this.emitter.setPosition((float) translation.x, (float) translation.y, (float) translation.z);
        this.emitter.setScale(scale, scale, scale);

        /* Get rotation from form's transform - values are in radians */
        /* BBS Transform uses ZYX euler order, Effekseer also uses euler angles */
        Vector3f rotate = this.form.transform.get().rotate;
        Vector3f rotate2 = this.form.transform.get().rotate2;

        /* Combine both rotation sets */
        float rotX = rotate.x + rotate2.x;
        float rotY = rotate.y + rotate2.y;
        float rotZ = rotate.z + rotate2.z;

        this.emitter.setRotation(rotX, rotY, rotZ);
    }

    @Override
    public void tick(IEntity entity)
    {
        if (this.emitter == null)
        {
            return;
        }

        /* Check if effect finished and handle loop */
        if (!this.emitter.exists())
        {
            if (this.form.loop.get())
            {
                /* Restart by clearing emitter - ensureEmitter will recreate it */
                this.emitter = null;
            }
        }
    }

    /**
     * Clean up resources when renderer is destroyed
     */
    public void cleanup()
    {
        this.stopEmitter();
    }
}
