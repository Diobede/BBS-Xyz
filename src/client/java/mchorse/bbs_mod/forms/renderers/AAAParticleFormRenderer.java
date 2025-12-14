package mchorse.bbs_mod.forms.renderers;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.forms.ITickable;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.AAAParticleForm;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.joml.Vectors;
import mod.chloeprime.aaaparticles.api.client.effekseer.ParticleEmitter;
import mod.chloeprime.aaaparticles.client.registry.EffectDefinition;
import mod.chloeprime.aaaparticles.client.registry.EffectRegistry;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * AAA Particle Form Renderer
 *
 * Renders Effekseer particles using the AAA Particles mod.
 * Supports bone attachment, looping, duration control, and context-aware rendering.
 * Includes tick memory system for film playback synchronization.
 */
public class AAAParticleFormRenderer extends FormRenderer<AAAParticleForm> implements ITickable
{
    /* Tick memory system - stores particle state per tick for film seeking */
    private static class ParticleState
    {
        int tick;
        boolean wasPlaying;
        Identifier effectId;

        ParticleState(int tick, boolean wasPlaying, Identifier effectId)
        {
            this.tick = tick;
            this.wasPlaying = wasPlaying;
            this.effectId = effectId;
        }
    }

    /* Track emitter state per form instance */
    private ParticleEmitter emitter;
    private Identifier lastEffectId;
    private boolean lastPaused = false;
    private boolean lastRestart = false;

    /* Film synchronization */
    private int currentTick = -1;
    private int lastTick = -1;
    private int lastRenderTick = -1;
    private boolean filmWasPlaying = false;
    private boolean hadForm = false;
    private Map<Integer, ParticleState> tickMemory = new HashMap<>();
    private long lastTickTime = 0;
    private FormRenderType lastRenderType = null;

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
            
            /* Clear tick memory when effect changes - old cache is invalid */
            this.tickMemory.clear();
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

        /* Detect if film is playing: tick() is only called when film is playing */
        /* Only use tick timing detection for ENTITY render type (films) */
        /* For model blocks, items, and other contexts, always consider as playing */
        boolean filmPlaying;
        
        if (this.lastRenderType == FormRenderType.ENTITY)
        {
            /* Film context - use tick timing to detect if film is paused */
            long currentTime = System.currentTimeMillis();
            filmPlaying = (currentTime - this.lastTickTime) < 100;
        }
        else
        {
            /* Model block, item, or unknown context - always playing */
            filmPlaying = true;
        }

        /* Update pause state - pause particle when film is paused OR when paused property is true */
        boolean shouldPause = this.form.paused.get() || !filmPlaying;

        if (shouldPause != this.lastPaused)
        {
            if (shouldPause)
            {
                this.emitter.pause();
            }
            else
            {
                this.emitter.resume();
            }

            this.lastPaused = shouldPause;
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
            this.emitter.setVisibility(false);
            this.emitter.stop();
            this.emitter = null;
        }

        this.lastPaused = false;
        this.lastRestart = false;
        this.tickMemory.clear();
        this.currentTick = -1;
        this.lastTick = -1;
    }

    @Override
    protected void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        Link preview = this.form.preview.get();

        if (preview != null)
        {
            /* Render the preview texture */
            context.batcher.texturedBox(
                BBSModClient.getTextures().getTexture(preview),
                Colors.WHITE,
                x1, y1, x2 - x1, y2 - y1,
                0, 0, 1, 1
            );
        }
        else
        {
            /* Fallback to particle icon */
            int cx = (x1 + x2) / 2;
            int cy = (y1 + y2) / 2;
            int size = Math.min(x2 - x1, y2 - y1) / 3;

            context.batcher.iconArea(
                mchorse.bbs_mod.ui.utils.icons.Icons.PARTICLE,
                cx - size / 2,
                cy - size / 2,
                cx + size / 2,
                cy + size / 2
            );
        }
    }

    @Override
    protected void render3D(FormRenderingContext context)
    {
        /* Store render type for film playing detection */
        this.lastRenderType = context.type;

        /* Check if entity or form was removed - stop particle */
        if (context.entity == null || context.entity.getForm() == null)
        {
            if (this.hadForm)
            {
                this.stopEmitter();
                this.hadForm = false;
            }
            return;
        }

        /* Check if effect was cleared - stop particle when replay is removed */
        if (this.form.effect.get() == null)
        {
            if (this.hadForm)
            {
                this.stopEmitter();
                this.hadForm = false;
            }
            return;
        }

        this.hadForm = true;

        /* Check for seeks even when paused - render is called even when film is paused */
        int renderTick = context.entity.getAge();
        boolean seekedWhilePaused = false;

        if (this.lastRenderTick >= 0 && renderTick != this.lastRenderTick)
        {
            /* Tick changed between renders - check if we seeked (jumped backwards or skipped) */
            if (renderTick < this.lastRenderTick || Math.abs(renderTick - this.lastRenderTick) > 1)
            {
                seekedWhilePaused = true;

                /* Try to restore particle state from memory */
                ParticleState state = this.tickMemory.get(renderTick);

                if (state != null && state.effectId != null && state.effectId.equals(this.getEffectId()))
                {
                    /* Restore particle from cached state */
                    this.stopEmitter();
                }
                else
                {
                    /* No cached state, restart particle */
                    this.stopEmitter();
                }
            }
        }

        this.lastRenderTick = renderTick;

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
        /* Check if entity was removed - stop particle */
        if (entity == null || entity.getForm() == null)
        {
            if (this.hadForm)
            {
                this.stopEmitter();
                this.hadForm = false;
            }
            return;
        }

        /* Check if effect was cleared - stop particle when replay is removed */
        if (this.form.effect.get() == null)
        {
            if (this.hadForm)
            {
                this.stopEmitter();
                this.hadForm = false;
            }
            return;
        }

        this.hadForm = true;

        /* Record tick time - this tells ensureEmitter that film is playing */
        this.lastTickTime = System.currentTimeMillis();

        this.currentTick = entity.getAge();

        /* Detect seeking (tick jumped backwards or skipped forward significantly) */
        boolean seeked = this.lastTick >= 0 && (this.currentTick < this.lastTick || Math.abs(this.currentTick - this.lastTick) > 1);

        /* If seeked, try to restore particle state from memory */
        if (seeked)
        {
            ParticleState state = this.tickMemory.get(this.currentTick);

            if (state != null && state.effectId != null && state.effectId.equals(this.getEffectId()))
            {
                /* Restore particle from cached state */
                this.stopEmitter();
                this.ensureEmitter();
                this.filmWasPlaying = state.wasPlaying;
            }
            else
            {
                /* No cached state, restart particle */
                this.stopEmitter();
                this.ensureEmitter();
            }
        }
        else
        {
            this.ensureEmitter();
        }

        /* Store current state in tick memory (with size limit to prevent unbounded growth) */
        if (this.currentTick >= 0)
        {
            Identifier effectId = this.getEffectId();

            if (effectId != null)
            {
                /* Limit tick memory to 1000 entries to prevent memory leaks on very long films */
                if (this.tickMemory.size() >= 1000)
                {
                    /* Remove oldest entry (lowest tick number) */
                    int minTick = this.tickMemory.keySet().stream().min(Integer::compare).orElse(-1);
                    
                    if (minTick >= 0)
                    {
                        this.tickMemory.remove(minTick);
                    }
                }
                
                this.tickMemory.put(this.currentTick, new ParticleState(this.currentTick, true, effectId));
            }
        }

        if (this.emitter == null)
        {
            this.lastTick = this.currentTick;
            return;
        }

        /* Handle restart keyframe - trigger restart when property changes to true */
        boolean restart = this.form.restart.get();

        if (restart && !this.lastRestart)
        {
            this.emitter.stop();
            this.emitter = null;
            this.ensureEmitter();
        }

        this.lastRestart = restart;

        /* Check if effect finished and handle loop */
        if (!this.emitter.exists())
        {
            if (this.form.loop.get())
            {
                /* Restart by clearing emitter - ensureEmitter will recreate it */
                this.emitter = null;
                this.ensureEmitter();
            }
        }

        this.lastTick = this.currentTick;
        this.filmWasPlaying = true;
    }

    /**
     * Clean up resources when renderer is destroyed
     */
    public void cleanup()
    {
        this.stopEmitter();
    }
}
