package mchorse.bbs_mod.ui.film.replays;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.blocks.entities.ModelProperties;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.clips.CameraClipContext;
import mchorse.bbs_mod.camera.clips.modifiers.EntityClip;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.Replays;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.AnchorForm;
import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.utils.Anchor;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.math.IExpression;
import mchorse.bbs_mod.math.MathBuilder;
import mchorse.bbs_mod.settings.values.IValueListener;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.core.ValueForm;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.replays.overlays.UIReplaysOverlayPanel;
import mchorse.bbs_mod.ui.forms.UIFormPalette;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIList;
import mchorse.bbs_mod.ui.framework.elements.input.list.UISearchList;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIConfirmOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UINumberOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.CollectionUtils;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.RayTracing;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.Clips;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * This GUI is responsible for drawing replays available in the 
 * director thing
 */
public class UIReplayList extends UIList<Replay>
{
    private static String LAST_PROCESS = "v";
    private static String LAST_OFFSET = "0";
    private static List<String> LAST_PROCESS_PROPERTIES = Arrays.asList("x");

    public UIFilmPanel panel;
    public UIReplaysOverlayPanel overlay;

    private Map<String, Boolean> collapsedGroups = new HashMap<>();
    private String filterGroup = "";


    /* Track dragging state since parent's fields are private */
    private int groupDraggingActual = -1;
    private int groupDraggingVisual = -1;
    private long groupDragTime;


    /* Track group header dragging */
    private String draggingGroupName = null;
    private int draggingGroupVisual = -1;

    /* Track selected group for editing */
    private String selectedGroup = null;
    private long lastGroupClickTime = 0;
    private String lastClickedGroup = null;
    
    /* Track right-clicked group for context menu */
    private String contextClickedGroup = null;

    public UIReplayList(Consumer<List<Replay>> callback, UIReplaysOverlayPanel overlay, UIFilmPanel panel)
    {
        super(callback);

        this.overlay = overlay;
        this.panel = panel;

        this.multi().sorting();
        this.context((menu) ->
        {
            /* Context menu for group headers */
            if (this.contextClickedGroup != null)
            {
                menu.action(Icons.REMOVE, IKey.raw("Delete Group"), this::deleteGroup);
                menu.action(Icons.CLOSE, IKey.raw("Clear Group"), this::clearContextGroup);
                return;
            }

            menu.action(Icons.ADD, UIKeys.SCENE_REPLAYS_CONTEXT_ADD, this::addReplay);
            menu.action(Icons.POSE, UIKeys.SCENE_REPLAYS_CONTEXT_NEW_GROUP, this::createNewGroup);

            if (!this.collapsedGroups.isEmpty())
            {
                menu.action(Icons.VISIBLE, IKey.raw("Expand All Groups"), () -> {
                    this.collapsedGroups.clear();
                    this.update();
                });
            }

            if (!this.filterGroup.isEmpty())
            {
                menu.action(Icons.REFRESH, UIKeys.SCENE_REPLAYS_CONTEXT_SHOW_ALL_GROUPS, () -> this.setFilterGroup(""));
            }

            if (this.isSelected())
            {
                menu.action(Icons.COPY, UIKeys.SCENE_REPLAYS_CONTEXT_COPY, this::copyReplay);
            }

            MapType copyReplay = Window.getClipboardMap("_CopyReplay");

            if (copyReplay != null)
            {
                menu.action(Icons.PASTE, UIKeys.SCENE_REPLAYS_CONTEXT_PASTE, () -> this.pasteReplay(copyReplay));
            }

            int duration = this.panel.getData().camera.calculateDuration();

            if (duration > 0)
            {
                menu.action(Icons.PLAY, UIKeys.SCENE_REPLAYS_CONTEXT_FROM_CAMERA, () -> this.fromCamera(duration));
            }

            menu.action(Icons.BLOCK, UIKeys.SCENE_REPLAYS_CONTEXT_FROM_MODEL_BLOCK, this::fromModelBlock);

            if (this.isSelected())
            {
                boolean shift = Window.isShiftPressed();
                MapType data = Window.getClipboardMap("_CopyKeyframes");

                menu.action(Icons.ALL_DIRECTIONS, UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS, this::processReplays);
                menu.action(Icons.TIME, UIKeys.SCENE_REPLAYS_CONTEXT_OFFSET_TIME, this::offsetTimeReplays);
                menu.action(Icons.POSE, UIKeys.SCENE_REPLAYS_CONTEXT_ASSIGN_GROUP, this::assignGroup);

                if (data != null)
                {
                    menu.action(Icons.PASTE, UIKeys.SCENE_REPLAYS_CONTEXT_PASTE_KEYFRAMES, () -> this.pasteToReplays(data));
                }

                menu.action(Icons.DUPE, UIKeys.SCENE_REPLAYS_CONTEXT_DUPE, () ->
                {
                    if (Window.isShiftPressed() || shift)
                    {
                        this.dupeReplay();
                    }
                    else
                    {
                        UINumberOverlayPanel numberPanel = new UINumberOverlayPanel(UIKeys.SCENE_REPLAYS_CONTEXT_DUPE, UIKeys.SCENE_REPLAYS_CONTEXT_DUPE_DESCRIPTION, (n) ->
                        {
                            for (int i = 0; i < n; i++)
                            {
                                this.dupeReplay();
                            }
                        });

                        numberPanel.value.limit(1).integer();
                        numberPanel.value.setValue(1D);

                        UIOverlay.addOverlay(this.getContext(), numberPanel);
                    }
                });
                menu.action(Icons.REMOVE, UIKeys.SCENE_REPLAYS_CONTEXT_REMOVE, this::removeReplay);
            }
        });
    }

    @Override
    protected void handleSwap(int from, int to)
    {
        Film data = this.panel.getData();
        Replays replays = data.replays;
        Replay value = replays.getList().get(from);

        data.preNotify(IValueListener.FLAG_UNMERGEABLE);

        replays.remove(value);
        replays.add(to, value);
        replays.sync();

        /* Readjust tracker and anchor indices */
        for (Replay replay : replays.getList())
        {
            if (replay.properties.get("anchor") instanceof KeyframeChannel<?> channel && channel.getFactory() == KeyframeFactories.ANCHOR)
            {
                KeyframeChannel<Anchor> keyframeChannel = (KeyframeChannel<Anchor>) channel;

                for (Keyframe<Anchor> keyframe : keyframeChannel.getKeyframes())
                {
                    keyframe.getValue().replay = MathUtils.remapIndex(keyframe.getValue().replay, from, to);
                }
            }
        }

        for (Clip clip : data.camera.get())
        {
            if (clip instanceof EntityClip entityClip)
            {
                entityClip.selector.set(MathUtils.remapIndex(entityClip.selector.get(), from, to));
            }
        }

        data.postNotify(IValueListener.FLAG_UNMERGEABLE);

        this.setList(replays.getList());
        this.updateFilmEditor();
        this.pick(to);
    }

    private void pasteToReplays(MapType data)
    {
        UIReplaysEditor replayEditor = this.panel.replayEditor;
        List<Replay> selectedReplays = replayEditor.replays.replays.getCurrent();

        if (data == null)
        {
            return;
        }

        Map<String, UIKeyframes.PastedKeyframes> parsedKeyframes = UIKeyframes.parseKeyframes(data);

        if (parsedKeyframes.isEmpty())
        {
            return;
        }

        UINumberOverlayPanel offsetPanel = new UINumberOverlayPanel(UIKeys.SCENE_REPLAYS_CONTEXT_PASTE_KEYFRAMES_TITLE, UIKeys.SCENE_REPLAYS_CONTEXT_PASTE_KEYFRAMES_DESCRIPTION, (n) ->
        {
            int tick = this.panel.getCursor();

            for (Replay replay : selectedReplays)
            {
                int randomOffset = (int) (n.intValue() * Math.random());

                for (Map.Entry<String, UIKeyframes.PastedKeyframes> entry : parsedKeyframes.entrySet())
                {
                    String id = entry.getKey();
                    UIKeyframes.PastedKeyframes pastedKeyframes = entry.getValue();
                    KeyframeChannel channel = (KeyframeChannel) replay.keyframes.get(id);

                    if (channel == null || channel.getFactory() != pastedKeyframes.factory)
                    {
                        channel = replay.properties.getOrCreate(replay.form.get(), id);
                    }

                    float min = Integer.MAX_VALUE;

                    for (Keyframe kf : pastedKeyframes.keyframes)
                    {
                        min = Math.min(kf.getTick(), min);
                    }

                    for (Keyframe kf : pastedKeyframes.keyframes)
                    {
                        float finalTick = tick + (kf.getTick() - min) + randomOffset;
                        int index = channel.insert(finalTick, kf.getValue());
                        Keyframe inserted = channel.get(index);

                        inserted.copy(kf);
                        inserted.setTick(finalTick);
                    }

                    channel.sort();
                }
            }
        });

        UIOverlay.addOverlay(this.getContext(), offsetPanel);
    }

    private void processReplays()
    {
        UITextbox expression = new UITextbox((t) -> LAST_PROCESS = t);
        UIStringList properties = new UIStringList(null);
        UIConfirmOverlayPanel panel = new UIConfirmOverlayPanel(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_TITLE, UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_DESCRIPTION, (b) ->
        {
            if (b)
            {
                MathBuilder builder = new MathBuilder();
                int min = Integer.MAX_VALUE;

                builder.register("i");
                builder.register("o");
                builder.register("v");
                builder.register("ki");

                IExpression parse;

                try
                {
                    parse = builder.parse(expression.getText());
                }
                catch (Exception e)
                {
                    return;
                }

                LAST_PROCESS_PROPERTIES = new ArrayList<>(properties.getCurrent());

                for (int index : this.current)
                {
                    min = Math.min(min, index);
                }

                for (int index : this.current)
                {
                    Replay replay = this.list.get(index);

                    builder.variables.get("i").set(index);
                    builder.variables.get("o").set(index - min);

                    for (String s : properties.getCurrent())
                    {
                        KeyframeChannel channel = (KeyframeChannel) replay.keyframes.get(s);
                        List keyframes = channel.getKeyframes();

                        for (int i = 0; i < keyframes.size(); i++)
                        {
                            Keyframe kf = (Keyframe) keyframes.get(i);

                            builder.variables.get("v").set(kf.getFactory().getY(kf.getValue()));
                            builder.variables.get("ki").set(i);

                            kf.setValue(kf.getFactory().yToValue(parse.doubleValue()), true);
                        }
                    }
                }
            }
        });

        for (KeyframeChannel<?> channel : this.getCurrentFirst().keyframes.getChannels())
        {
            if (KeyframeFactories.isNumeric(channel.getFactory()))
            {
                properties.add(channel.getId());
            }
        }

        properties.background().multi().sort();
        properties.relative(expression).y(-5).w(1F).h(16 * 9).anchor(0F, 1F);

        if (!LAST_PROCESS_PROPERTIES.isEmpty())
        {
            properties.setCurrentScroll(LAST_PROCESS_PROPERTIES.get(0));
        }

        for (String property : LAST_PROCESS_PROPERTIES)
        {
            properties.addIndex(properties.getList().indexOf(property));
        }

        expression.setText(LAST_PROCESS);
        expression.tooltip(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_EXPRESSION_TOOLTIP);
        expression.relative(panel.confirm).y(-1F, -5).w(1F).h(20);

        panel.confirm.w(1F, -10);
        panel.content.add(expression, properties);

        UIOverlay.addOverlay(this.getContext(), panel, 240, 300);
    }

    private void offsetTimeReplays()
    {
        UITextbox tick = new UITextbox((t) -> LAST_OFFSET = t);
        UIConfirmOverlayPanel panel = new UIConfirmOverlayPanel(UIKeys.SCENE_REPLAYS_CONTEXT_OFFSET_TIME_TITLE, UIKeys.SCENE_REPLAYS_CONTEXT_OFFSET_TIME_DESCRIPTION, (b) ->
        {
            if (b)
            {
                MathBuilder builder = new MathBuilder();
                int min = Integer.MAX_VALUE;

                builder.register("i");
                builder.register("o");

                IExpression parse = null;

                try
                {
                    parse = builder.parse(tick.getText());
                }
                catch (Exception e)
                {}

                for (int index : this.current)
                {
                    min = Math.min(min, index);
                }

                for (int index : this.current)
                {
                    Replay replay = this.list.get(index);

                    builder.variables.get("i").set(index);
                    builder.variables.get("o").set(index - min);

                    float tickv = parse == null ? 0F : (float) parse.doubleValue();

                    BaseValue.edit(replay, (r) -> r.shift(tickv));
                }
            }
        });

        tick.setText(LAST_OFFSET);
        tick.tooltip(UIKeys.SCENE_REPLAYS_CONTEXT_OFFSET_TIME_EXPRESSION_TOOLTIP);
        tick.relative(panel.confirm).y(-1F, -5).w(1F).h(20);

        panel.confirm.w(1F, -10);
        panel.content.add(tick);

        UIOverlay.addOverlay(this.getContext(), panel);
    }

    private void createNewGroup()
    {
        UITextbox groupNameInput = new UITextbox((t) -> {});
        UIConfirmOverlayPanel panel = new UIConfirmOverlayPanel(UIKeys.SCENE_REPLAYS_CONTEXT_NEW_GROUP_TITLE, UIKeys.SCENE_REPLAYS_CONTEXT_NEW_GROUP_DESCRIPTION, (b) ->
        {
            if (b)
            {
                String groupName = groupNameInput.getText();

                if (!groupName.trim().isEmpty())
                {
                    List<Replay> replays = this.getCurrent();

                    if (!replays.isEmpty())
                    {
                        for (Replay replay : replays)
                        {
                            replay.group.set(groupName);
                        }
                    }

                    this.update();
                    this.overlay.setReplay(this.overlay.replays.getCurrentFirst());
                }
            }
        });

        groupNameInput.relative(panel.confirm).y(-1F, -5).w(1F).h(20);
        panel.confirm.w(1F, -10);
        panel.content.add(groupNameInput);

        UIOverlay.addOverlay(this.getContext(), panel);
    }


    private void assignGroup()
    {
        List<String> existingGroups = new ArrayList<>();


        for (Replay replay : this.list)
        {
            String group = replay.group.get();

            if (group != null && !group.trim().isEmpty() && !existingGroups.contains(group))
            {
                existingGroups.add(group);
            }
        }


        if (existingGroups.isEmpty())
        {
            this.createNewGroup();

            return;
        }


        UIStringList groupList = new UIStringList((selected) ->
        {
            if (!selected.isEmpty())
            {
                String groupName = selected.get(0);
                List<Replay> replays = this.getCurrent();

                for (Replay replay : replays)
                {
                    replay.group.set(groupName);
                }

                this.update();
                this.overlay.setReplay(this.overlay.replays.getCurrentFirst());
            }
        });

        groupList.setList(existingGroups);
        groupList.sort();

        UISearchList<String> searchList = new UISearchList<>(groupList);
        searchList.label(UIKeys.SCENE_REPLAYS_CONTEXT_ASSIGN_GROUP_DESCRIPTION);

        UIOverlayPanel panel = new UIOverlayPanel(UIKeys.SCENE_REPLAYS_CONTEXT_ASSIGN_GROUP_TITLE);
        searchList.relative(panel.content).wh(1F, 1F);
        panel.content.add(searchList);

        Replay first = this.getCurrentFirst();

        if (first != null && !first.group.get().isEmpty())
        {
            groupList.setCurrentScroll(first.group.get());
        }

        UIOverlay.addOverlay(this.getContext(), panel);
    }

    private void deleteGroup()
    {
        if (this.contextClickedGroup == null)
        {
            return;
        }

        /* Delete all replays in this group and its subgroups */
        List<Replay> toRemove = new ArrayList<>();
        Film film = this.panel.getData();

        for (Replay replay : film.replays.getList())
        {
            String group = replay.group.get();

            if (group.equals(this.contextClickedGroup) || group.startsWith(this.contextClickedGroup + "/"))
            {
                toRemove.add(replay);
            }
        }

        for (Replay replay : toRemove)
        {
            film.replays.remove(replay);
        }

        /* Clear selection if deleted */
        if (this.contextClickedGroup.equals(this.selectedGroup) || 
            (this.selectedGroup != null && this.selectedGroup.startsWith(this.contextClickedGroup + "/")))
        {
            this.selectedGroup = null;
        }

        this.contextClickedGroup = null;
        
        /* Recreate entities to rebuild entity map after deletion */
        this.panel.getController().createEntities();
        
        this.update();
        this.overlay.setReplay(null);
    }

    private void clearContextGroup()
    {
        if (this.contextClickedGroup == null)
        {
            return;
        }

        /* Clear group assignment for all replays in this group and its subgroups */
        Film film = this.panel.getData();

        for (Replay replay : film.replays.getList())
        {
            String group = replay.group.get();

            if (group.equals(this.contextClickedGroup) || group.startsWith(this.contextClickedGroup + "/"))
            {
                replay.group.set("");
            }
        }

        /* Clear selection if cleared */
        if (this.contextClickedGroup.equals(this.selectedGroup) || 
            (this.selectedGroup != null && this.selectedGroup.startsWith(this.contextClickedGroup + "/")))
        {
            this.selectedGroup = null;
        }

        this.contextClickedGroup = null;
        this.update();
        this.overlay.setReplay(this.overlay.replays.getCurrentFirst());
    }

    private void copyReplay()
    {
        MapType replays = new MapType();
        ListType replayList = new ListType();

        replays.put("replays", replayList);

        for (Replay replay : this.getCurrent())
        {
            replayList.add(replay.toData());
        }

        Window.setClipboard(replays, "_CopyReplay");
    }

    private void pasteReplay(MapType data)
    {
        Film film = this.panel.getData();
        ListType replays = data.getList("replays");
        Replay last = null;

        for (BaseType replayType : replays)
        {
            Replay replay = film.replays.addReplay();

            BaseValue.edit(replay, (r) -> r.fromData(replayType));

            last = replay;
        }

        if (last != null)
        {
            this.update();
            this.panel.replayEditor.setReplay(last);
            this.updateFilmEditor();
        }
    }

    public void openFormEditor(ValueForm form, boolean editing, Consumer<Form> consumer)
    {
        UIElement target = this.panel;

        if (this.getRoot() != null)
        {
            target = this.getParentContainer();
        }

        UIFormPalette palette = UIFormPalette.open(target, editing, form.get(), (f) ->
        {
            for (Replay replay : this.getCurrent())
            {
                replay.form.set(FormUtils.copy(f));
            }

            this.updateFilmEditor();

            if (consumer != null)
            {
                consumer.accept(f);
            }
            else
            {
                this.overlay.pickEdit.setForm(f);
            }
        });

        palette.updatable();
    }

    private void addReplay()
    {
        World world = MinecraftClient.getInstance().world;
        Camera camera = this.panel.getCamera();

        BlockHitResult blockHitResult = RayTracing.rayTrace(world, camera, 64F);
        Vec3d p = blockHitResult.getPos();
        Vector3d position = new Vector3d(p.x, p.y, p.z);

        if (blockHitResult.getType() == HitResult.Type.MISS)
        {
            position.set(camera.getLookDirection()).mul(5F).add(camera.position);
        }

        this.addReplay(position, camera.rotation.x, camera.rotation.y + MathUtils.PI);
    }

    private void fromCamera(int duration)
    {
        Position position = new Position();
        Clips camera = this.panel.getData().camera;
        CameraClipContext context = new CameraClipContext();

        Film film = this.panel.getData();
        Replay replay = film.replays.addReplay();

        context.clips = camera;

        for (int i = 0; i < duration; i++)
        {
            context.clipData.clear();
            context.setup(i, 0F);

            for (Clip clip : context.clips.getClips(i))
            {
                context.apply(clip, position);
            }

            context.currentLayer = 0;

            float yaw = position.angle.yaw - 180;

            replay.keyframes.x.insert(i, position.point.x);
            replay.keyframes.y.insert(i, position.point.y);
            replay.keyframes.z.insert(i, position.point.z);
            replay.keyframes.yaw.insert(i, (double) yaw);
            replay.keyframes.headYaw.insert(i, (double) yaw);
            replay.keyframes.bodyYaw.insert(i, (double) yaw);
            replay.keyframes.pitch.insert(i, (double) position.angle.pitch);
        }

        this.update();
        this.panel.replayEditor.setReplay(replay);
        this.updateFilmEditor();

        this.openFormEditor(replay.form, false, null);
    }

    private void fromModelBlock()
    {
        ArrayList<ModelBlockEntity> modelBlocks = new ArrayList<>(BBSRendering.capturedModelBlocks);
        UISearchList<String> search = new UISearchList<>(new UIStringList(null));
        UIList<String> list = search.list;
        UIConfirmOverlayPanel panel = new UIConfirmOverlayPanel(UIKeys.SCENE_REPLAYS_CONTEXT_FROM_MODEL_BLOCK_TITLE, UIKeys.SCENE_REPLAYS_CONTEXT_FROM_MODEL_BLOCK_DESCRIPTION, (b) ->
        {
            if (b)
            {
                int index = list.getIndex();
                ModelBlockEntity modelBlock = CollectionUtils.getSafe(modelBlocks, index);

                if (modelBlock != null)
                {
                    this.fromModelBlock(modelBlock);
                }
            }
        });

        modelBlocks.sort(Comparator.comparing(ModelBlockEntity::getName));

        for (ModelBlockEntity modelBlock : modelBlocks)
        {
            list.add(modelBlock.getName());
        }

        list.background();
        search.relative(panel.confirm).y(-5).w(1F).h(16 * 9 + 20).anchor(0F, 1F);

        panel.confirm.w(1F, -10);
        panel.content.add(search);

        UIOverlay.addOverlay(this.getContext(), panel, 240, 300);
    }

    private void fromModelBlock(ModelBlockEntity modelBlock)
    {
        Film film = this.panel.getData();
        Replay replay = film.replays.addReplay();
        BlockPos blockPos = modelBlock.getPos();
        ModelProperties properties = modelBlock.getProperties();
        Transform transform = properties.getTransform().copy();
        double x = blockPos.getX() + transform.translate.x + 0.5D;
        double y = blockPos.getY() + transform.translate.y;
        double z = blockPos.getZ() + transform.translate.z + 0.5D;

        transform.translate.set(0, 0, 0);

        replay.shadow.set(properties.isShadow());
        replay.form.set(FormUtils.copy(properties.getForm()));
        replay.keyframes.x.insert(0, x);
        replay.keyframes.y.insert(0, y);
        replay.keyframes.z.insert(0, z);

        if (!transform.isDefault())
        {
            if (
                transform.rotate.x == 0 && transform.rotate.z == 0 &&
                transform.rotate2.x == 0 && transform.rotate2.y == 0 && transform.rotate2.z == 0 &&
                transform.scale.x == 1 && transform.scale.y == 1 && transform.scale.z == 1
            ) {
                double yaw = -Math.toDegrees(transform.rotate.y);

                replay.keyframes.yaw.insert(0, yaw);
                replay.keyframes.headYaw.insert(0, yaw);
                replay.keyframes.bodyYaw.insert(0, yaw);
            }
            else
            {
                AnchorForm form = new AnchorForm();
                BodyPart part = new BodyPart("");

                part.setForm(replay.form.get());
                form.transform.set(transform);
                form.parts.addBodyPart(part);

                replay.form.set(form);
            }
        }

        this.update();
        this.panel.replayEditor.setReplay(replay);
        this.updateFilmEditor();
    }

    public void addReplay(Vector3d position, float pitch, float yaw)
    {
        Film film = this.panel.getData();
        Replay replay = film.replays.addReplay();

        replay.keyframes.x.insert(0, position.x);
        replay.keyframes.y.insert(0, position.y);
        replay.keyframes.z.insert(0, position.z);

        replay.keyframes.pitch.insert(0, (double) pitch);
        replay.keyframes.yaw.insert(0, (double) yaw);
        replay.keyframes.headYaw.insert(0, (double) yaw);
        replay.keyframes.bodyYaw.insert(0, (double) yaw);

        this.update();
        this.panel.replayEditor.setReplay(replay);
        this.updateFilmEditor();

        this.openFormEditor(replay.form, false, null);
    }

    private void updateFilmEditor()
    {
        this.panel.getController().createEntities();
        this.panel.replayEditor.updateChannelsList();
    }

    private void dupeReplay()
    {
        if (this.isDeselected())
        {
            return;
        }

        Replay last = null;

        for (Replay replay : this.getCurrent())
        {
            Film film = this.panel.getData();
            Replay newReplay = film.replays.addReplay();

            newReplay.copy(replay);

            last = newReplay;
        }

        if (last != null)
        {
            this.update();
            this.panel.replayEditor.setReplay(last);
            this.updateFilmEditor();
        }
    }

    private void removeReplay()
    {
        if (this.isDeselected())
        {
            return;
        }

        Film film = this.panel.getData();
        int index = this.getIndex();

        for (Replay replay : this.getCurrent())
        {
            film.replays.remove(replay);
        }

        int size = this.list.size();
        index = MathUtils.clamp(index, 0, size - 1);

        this.update();
        this.panel.replayEditor.setReplay(size == 0 ? null : this.list.get(index));
        this.updateFilmEditor();
    }


    public void setFilterGroup(String group)
    {
        this.filterGroup = group;
        this.update();
    }

    public String getSelectedGroup()
    {
        return this.selectedGroup;
    }


    @Override
    public void update()
    {
        if (this.isFiltering())
        {
            super.update();

            return;
        }


        int totalSize = 0;
        Map<String, List<Replay>> groups = new LinkedHashMap<>();


        for (Replay replay : this.list)
        {
            String group = replay.group.get();

            if (group == null || group.trim().isEmpty())
            {
                if (this.filterGroup.isEmpty())
                {
                    totalSize++;
                }
            }
            else
            {
                if (this.filterGroup.isEmpty() || group.equals(this.filterGroup))
                {
                    groups.computeIfAbsent(group, k -> new ArrayList<>()).add(replay);
                }
            }
        }


        for (Map.Entry<String, List<Replay>> entry : groups.entrySet())
        {
            totalSize++;

            if (!this.collapsedGroups.getOrDefault(entry.getKey(), false))
            {
                totalSize += entry.getValue().size();
            }
        }


        this.scroll.setSize(totalSize);
        this.scroll.clamp();
    }


    public void toggleGroup(String group)
    {
        this.collapsedGroups.put(group, !this.collapsedGroups.getOrDefault(group, false));
    }


    /**
     * Calculate the depth of a group (number of slashes) for nested groups.
     * e.g., "group1" = 0, "group1/group2" = 1, "group1/group2/group3" = 2
     */
    private int getGroupDepth(String groupName)
    {
        if (groupName == null || groupName.isEmpty())
        {
            return 0;
        }

        int depth = 0;

        for (int i = 0; i < groupName.length(); i++)
        {
            if (groupName.charAt(i) == '/')
            {
                depth++;
            }
        }

        return depth;
    }


    private boolean isGroupOrParentCollapsed(String groupName)
    {
        /* Only check if any PARENT group is collapsed, not the group itself */
        int lastSlash = groupName.lastIndexOf('/');

        while (lastSlash >= 0)
        {
            String parentGroup = groupName.substring(0, lastSlash);

            if (this.collapsedGroups.getOrDefault(parentGroup, false))
            {
                return true;
            }

            lastSlash = parentGroup.lastIndexOf('/');
        }

        return false;
    }


    @Override
    public void renderList(UIContext context)
    {
        if (this.isFiltering())
        {
            super.renderList(context);

            return;
        }


        Map<String, List<Replay>> groups = new LinkedHashMap<>();
        Map<Replay, Integer> replayIndices = new IdentityHashMap<>();
        List<Replay> ungrouped = new ArrayList<>();

        /* Build index mapping to avoid indexOf issues */
        for (int idx = 0; idx < this.list.size(); idx++)
        {
            Replay replay = this.list.get(idx);
            replayIndices.put(replay, idx);
            
            String group = replay.group.get();

            if (group == null || group.trim().isEmpty())
            {
                ungrouped.add(replay);
            }
            else
            {
                if (!this.filterGroup.isEmpty() && !group.equals(this.filterGroup))
                {
                    continue;
                }

                groups.computeIfAbsent(group, k -> new ArrayList<>()).add(replay);
            }
        }

        /* Sort groups hierarchically - parents before children */
        Map<String, List<Replay>> sortedGroups = new LinkedHashMap<>();
        groups.entrySet().stream()
            .sorted((e1, e2) -> {
                String g1 = e1.getKey();
                String g2 = e2.getKey();
                
                /* If one is a parent of the other, parent comes first */
                if (g2.startsWith(g1 + "/"))
                {
                    return -1;
                }
                if (g1.startsWith(g2 + "/"))
                {
                    return 1;
                }
                
                /* Otherwise alphabetical order */
                return g1.compareTo(g2);
            })
            .forEachOrdered(e -> sortedGroups.put(e.getKey(), e.getValue()));


        int i = 0;
        int s = this.scroll.scrollItemSize;
        int x = this.area.x;
        int baseY = this.area.y - (int) this.scroll.getScroll();


        /* Check if currently dragging to highlight drop targets */
        boolean isDragging = super.isDragging();
        int hoverVisualIndex = isDragging ? this.scroll.getIndex(context.mouseX, context.mouseY) : -1;


        for (Map.Entry<String, List<Replay>> entry : sortedGroups.entrySet())
        {
            String groupName = entry.getKey();
            List<Replay> replays = entry.getValue();

            /* Skip if parent group is collapsed */
            if (this.isGroupOrParentCollapsed(groupName))
            {
                continue;
            }

            boolean collapsed = this.collapsedGroups.getOrDefault(groupName, false);
            int groupDepth = this.getGroupDepth(groupName);
            int groupIndent = groupDepth * 12;

            int y = baseY + i * s;


            if (y + s >= this.area.y && y < this.area.ey())
            {
                boolean hover = context.mouseX >= x && context.mouseY >= y && 
                               context.mouseX < x + this.area.w && context.mouseY < y + s;

                /* Highlight group header only when selected or being dragged */
                boolean isSelected = groupName.equals(this.selectedGroup);
                boolean isDropTarget = isDragging && hoverVisualIndex == i;
                boolean isBeingDragged = this.draggingGroupName != null && this.draggingGroupName.equals(groupName);
                
                /* Only show background when selected or being used as drop target */
                if ((isSelected || isDropTarget) && !isBeingDragged)
                {
                    int bgColor = isDropTarget ? 
                        (Colors.A100 | Colors.mulRGB(BBSSettings.primaryColor.get(), 0.75F)) :
                        (Colors.A50 | Colors.mulRGB(BBSSettings.primaryColor.get(), 0.5F));
                    
                    context.batcher.box(x + groupIndent, y, x + this.area.w, y + s, bgColor);
                }

                String icon = collapsed ? "\u25b6" : "\u25bc";
                /* Show only the last part of nested groups */
                String displayName = groupName.contains("/") ? groupName.substring(groupName.lastIndexOf("/") + 1) : groupName;
                String text = icon + " " + displayName + " (" + replays.size() + ")";

                if (!isBeingDragged)
                {
                    context.batcher.textShadow(text, x + groupIndent + 4, y + (s - context.batcher.getFont().getHeight()) / 2, 
                                              hover ? Colors.WHITE : Colors.HIGHLIGHT);
                }
            }

            i++;


            if (!collapsed)
            {
                for (Replay replay : replays)
                {
                    int index = replayIndices.get(replay);
                    int replayIndent = (groupDepth + 1) * 12;

                    /* Store original x position and apply indent */
                    int originalX = this.area.x;
                    this.area.x += replayIndent;
                    this.area.w -= replayIndent;

                    i = this.renderElement(context, replay, i, index, false);

                    /* Restore original position */
                    this.area.w += replayIndent;
                    this.area.x = originalX;

                    if (i == -1)
                    {
                        return;
                    }
                }
            }
        }


        if (!ungrouped.isEmpty())
        {
            for (Replay replay : ungrouped)
            {
                if (this.filterGroup.isEmpty())
                {
                    int index = replayIndices.get(replay);

                    i = this.renderElement(context, replay, i, index, false);

                    if (i == -1)
                    {
                        return;
                    }
                }
            }
        }
    }


    @Override
    public boolean subMouseClicked(UIContext context)
    {
        /* Reset context-clicked group */
        this.contextClickedGroup = null;

        /* Handle right-click on group headers */
        if (this.area.isInside(context) && context.mouseButton == 1)
        {
            if (!this.isFiltering())
            {
                int clickedVisualIndex = this.scroll.getIndex(context.mouseX, context.mouseY);
                int visualIndex = 0;

                Map<String, List<Replay>> groups = new LinkedHashMap<>();
                List<Replay> ungrouped = new ArrayList<>();

                for (Replay replay : this.list)
                {
                    String group = replay.group.get();

                    if (group == null || group.trim().isEmpty())
                    {
                        ungrouped.add(replay);
                    }
                    else
                    {
                        if (this.filterGroup.isEmpty() || group.equals(this.filterGroup))
                        {
                            groups.computeIfAbsent(group, k -> new ArrayList<>()).add(replay);
                        }
                    }
                }

                /* Sort groups hierarchically */
                Map<String, List<Replay>> sortedGroups = new LinkedHashMap<>();
                groups.entrySet().stream()
                    .sorted((e1, e2) -> {
                        String g1 = e1.getKey();
                        String g2 = e2.getKey();
                        if (g2.startsWith(g1 + "/")) return -1;
                        if (g1.startsWith(g2 + "/")) return 1;
                        return g1.compareTo(g2);
                    })
                    .forEachOrdered(e -> sortedGroups.put(e.getKey(), e.getValue()));

                for (Map.Entry<String, List<Replay>> entry : sortedGroups.entrySet())
                {
                    if (this.isGroupOrParentCollapsed(entry.getKey()))
                    {
                        continue;
                    }

                    if (visualIndex == clickedVisualIndex)
                    {
                        /* Right-clicked on group header */
                        this.contextClickedGroup = entry.getKey();
                        return false; /* Allow context menu to open */
                    }

                    visualIndex++;

                    if (!this.collapsedGroups.getOrDefault(entry.getKey(), false))
                    {
                        visualIndex += entry.getValue().size();
                    }
                }
            }
        }

        if (this.scroll.mouseClicked(context))
        {
            return true;
        }

        if (this.area.isInside(context) && context.mouseButton == 0)
        {
            if (this.isFiltering())
            {
                return super.subMouseClicked(context);
            }


            int clickedVisualIndex = this.scroll.getIndex(context.mouseX, context.mouseY);
            int visualIndex = 0;


            Map<String, List<Replay>> groups = new LinkedHashMap<>();
            Map<Replay, Integer> replayIndices = new IdentityHashMap<>();
            List<Replay> ungrouped = new ArrayList<>();


            /* Build index mapping to avoid indexOf issues */
            for (int idx = 0; idx < this.list.size(); idx++)
            {
                Replay replay = this.list.get(idx);
                replayIndices.put(replay, idx);
                
                String group = replay.group.get();

                if (group == null || group.trim().isEmpty())
                {
                    ungrouped.add(replay);
                }
                else
                {
                    if (this.filterGroup.isEmpty() || group.equals(this.filterGroup))
                    {
                        groups.computeIfAbsent(group, k -> new ArrayList<>()).add(replay);
                    }
                }
            }

            /* Sort groups hierarchically - must match renderList() order */
            Map<String, List<Replay>> sortedGroups = new LinkedHashMap<>();
            groups.entrySet().stream()
                .sorted((e1, e2) -> {
                    String g1 = e1.getKey();
                    String g2 = e2.getKey();
                    if (g2.startsWith(g1 + "/")) return -1;
                    if (g1.startsWith(g2 + "/")) return 1;
                    return g1.compareTo(g2);
                })
                .forEachOrdered(e -> sortedGroups.put(e.getKey(), e.getValue()));


            for (Map.Entry<String, List<Replay>> entry : sortedGroups.entrySet())
            {
                /* Skip if parent group is collapsed */
                if (this.isGroupOrParentCollapsed(entry.getKey()))
                {
                    continue;
                }

                if (visualIndex == clickedVisualIndex)
                {
                    String groupName = entry.getKey();
                    long currentTime = System.currentTimeMillis();

                    /* Check for double-click on group header */
                    if (groupName.equals(this.lastClickedGroup) && (currentTime - this.lastGroupClickTime) < 250)
                    {
                        /* Double-click detected - select the group */
                        this.selectedGroup = groupName;
                        this.current.clear();

                        /* Notify parent via callback with null (no replay selected) */
                        if (this.callback != null)
                        {
                            this.callback.accept(new ArrayList<>());
                        }

                        /* Notify overlay about group selection */
                        if (this.overlay != null)
                        {
                            this.overlay.setGroup(groupName);
                        }

                        this.lastClickedGroup = null;
                        return true;
                    }

                    /* Single click - set up dragging state for group headers */
                    this.draggingGroupName = groupName;
                    this.draggingGroupVisual = visualIndex;
                    this.groupDragTime = currentTime;
                    this.lastClickedGroup = groupName;
                    this.lastGroupClickTime = currentTime;

                    return true;
                }

                visualIndex++;

                if (!this.collapsedGroups.getOrDefault(entry.getKey(), false))
                {
                    for (Replay replay : entry.getValue())
                    {
                        if (visualIndex == clickedVisualIndex)
                        {
                            int actualIndex = replayIndices.get(replay);

                            /* Clear selected group when replay is selected */
                            this.selectedGroup = null;

                            /* Handle selection */
                            if (this.multi && Window.isShiftPressed() && this.isSelected())
                            {
                                int first = this.current.get(0);
                                int increment = first > actualIndex ? -1 : 1;

                                for (int i = first + increment; i != actualIndex + increment; i += increment)
                                {
                                    this.addIndex(i);
                                }
                            }
                            else if (this.multi && Window.isCtrlPressed())
                            {
                                this.toggleIndex(actualIndex);
                            }
                            else
                            {
                                this.setIndex(actualIndex);
                            }


                            /* Set up dragging if sorting enabled and single selection */
                            if (!this.isFiltering() && this.sorting && this.current.size() == 1)
                            {
                                this.groupDraggingActual = actualIndex;
                                this.groupDraggingVisual = visualIndex;
                                this.groupDragTime = System.currentTimeMillis();
                            }


                            List<Replay> current = this.getCurrent();

                            if (this.callback != null)
                            {
                                this.callback.accept(current);
                            }

                            return true;
                        }

                        visualIndex++;
                    }
                }
            }


            if (!ungrouped.isEmpty() && this.filterGroup.isEmpty())
            {
                for (Replay replay : ungrouped)
                {
                    if (visualIndex == clickedVisualIndex)
                    {
                        int actualIndex = replayIndices.get(replay);

                        /* Handle selection */
                        if (this.multi && Window.isShiftPressed() && this.isSelected())
                        {
                            int first = this.current.get(0);
                            int increment = first > actualIndex ? -1 : 1;

                            for (int i = first + increment; i != actualIndex + increment; i += increment)
                            {
                                this.addIndex(i);
                            }
                        }
                        else if (this.multi && Window.isCtrlPressed())
                        {
                            this.toggleIndex(actualIndex);
                        }
                        else
                        {
                            this.setIndex(actualIndex);
                        }


                        /* Set up dragging if sorting enabled and single selection */
                        if (!this.isFiltering() && this.sorting && this.current.size() == 1)
                        {
                            this.groupDraggingActual = actualIndex;
                            this.groupDraggingVisual = visualIndex;
                            this.groupDragTime = System.currentTimeMillis();
                        }


                        List<Replay> current = this.getCurrent();

                        if (this.callback != null)
                        {
                            this.callback.accept(current);
                        }

                        return true;
                    }

                    visualIndex++;
                }
            }
        }

        return super.subMouseClicked(context);
    }


    @Override
    public boolean isDragging()
    {
        /* Use our tracking when groups are visible */
        if (!this.isFiltering())
        {
            boolean replayDragging = this.groupDraggingActual >= 0 && System.currentTimeMillis() - this.groupDragTime > 100;
            boolean groupHeaderDragging = this.draggingGroupName != null && System.currentTimeMillis() - this.groupDragTime > 100;
            
            return replayDragging || groupHeaderDragging;
        }

        return super.isDragging();
    }


    @Override
    public boolean subMouseReleased(UIContext context)
    {
        /* Handle group header drag release */
        if (!this.isFiltering() && this.draggingGroupName != null)
        {
            boolean wasDragging = System.currentTimeMillis() - this.groupDragTime > 100;
            
            if (wasDragging)
            {
                if (this.area.isInside(context))
                {
                    int targetVisualIndex = this.scroll.getIndex(context.mouseX, context.mouseY);
                    String targetGroup = this.getGroupAtVisualIndex(targetVisualIndex);

                    if (targetGroup != null && !targetGroup.equals(this.draggingGroupName) && 
                        !targetGroup.startsWith(this.draggingGroupName + "/"))
                    {
                        /* Nest the dragged group inside the target group */
                        String baseName = this.draggingGroupName.contains("/") ? 
                            this.draggingGroupName.substring(this.draggingGroupName.lastIndexOf("/") + 1) : this.draggingGroupName;
                        String newGroupName = targetGroup + "/" + baseName;
                        
                        /* Safety check - ensure names are not empty */
                        if (baseName.trim().isEmpty() || newGroupName.trim().isEmpty())
                        {
                            this.draggingGroupName = null;
                            this.draggingGroupVisual = -1;
                            return true;
                        }
                        
                        /* Rename all replays in the dragged group and its subgroups */
                        for (Replay replay : this.list)
                        {
                            String replayGroup = replay.group.get();
                            
                            if (replayGroup != null)
                            {
                                /* Match exact group or subgroups (e.g., "Group1" or "Group1/SubGroup") */
                                if (replayGroup.equals(this.draggingGroupName) || replayGroup.startsWith(this.draggingGroupName + "/"))
                                {
                                    String suffix = replayGroup.substring(this.draggingGroupName.length());
                                    replay.group.set(newGroupName + suffix);
                                }
                            }
                        }
                        
                        /* Transfer collapsed state for the group and subgroups */
                        Map<String, Boolean> newCollapsedStates = new HashMap<>();
                        
                        for (Map.Entry<String, Boolean> entry : this.collapsedGroups.entrySet())
                        {
                            String groupKey = entry.getKey();
                            
                            if (groupKey.equals(this.draggingGroupName) || groupKey.startsWith(this.draggingGroupName + "/"))
                            {
                                String suffix = groupKey.substring(this.draggingGroupName.length());
                                newCollapsedStates.put(newGroupName + suffix, entry.getValue());
                            }
                            else
                            {
                                newCollapsedStates.put(groupKey, entry.getValue());
                            }
                        }
                        
                        this.collapsedGroups = newCollapsedStates;
                        
                        this.setList(this.list);
                        this.update();
                    }
                }
                else
                {
                    /* Dragged outside - remove from parent group (un-nest) */
                    if (this.draggingGroupName.contains("/"))
                    {
                        String baseName = this.draggingGroupName.substring(this.draggingGroupName.lastIndexOf("/") + 1);
                        
                        /* Safety check - ensure base name is not empty */
                        if (baseName.trim().isEmpty())
                        {
                            this.draggingGroupName = null;
                            this.draggingGroupVisual = -1;
                            return true;
                        }
                        
                        /* Rename all replays in the dragged group and its subgroups */
                        for (Replay replay : this.list)
                        {
                            String replayGroup = replay.group.get();
                            
                            if (replayGroup != null)
                            {
                                if (replayGroup.equals(this.draggingGroupName) || replayGroup.startsWith(this.draggingGroupName + "/"))
                                {
                                    String suffix = replayGroup.substring(this.draggingGroupName.length());
                                    replay.group.set(baseName + suffix);
                                }
                            }
                        }
                        
                        /* Transfer collapsed state */
                        Map<String, Boolean> newCollapsedStates = new HashMap<>();
                        
                        for (Map.Entry<String, Boolean> entry : this.collapsedGroups.entrySet())
                        {
                            String groupKey = entry.getKey();
                            
                            if (groupKey.equals(this.draggingGroupName) || groupKey.startsWith(this.draggingGroupName + "/"))
                            {
                                String suffix = groupKey.substring(this.draggingGroupName.length());
                                newCollapsedStates.put(baseName + suffix, entry.getValue());
                            }
                            else
                            {
                                newCollapsedStates.put(groupKey, entry.getValue());
                            }
                        }
                        
                        this.collapsedGroups = newCollapsedStates;
                        
                        this.setList(this.list);
                        this.update();
                    }
                }
            }
            else
            {
                /* Was a quick click, toggle the group instead of dragging */
                this.toggleGroup(this.draggingGroupName);
            }

            this.draggingGroupName = null;
            this.draggingGroupVisual = -1;

            return true;
        }
        
        /* Handle replay drag release */
        if (!this.isFiltering() && this.groupDraggingActual >= 0)
        {
            /* Only handle as drag if dragging actually started (100ms passed) */
            if (this.isDragging())
            {
                if (this.area.isInside(context))
                {
                    int targetVisualIndex = this.scroll.getIndex(context.mouseX, context.mouseY);

                    /* Check if dropping on a group header */
                    String targetGroup = this.getGroupAtVisualIndex(targetVisualIndex);

                    if (targetGroup != null)
                    {
                        /* Assign to group */
                        Replay draggedReplay = this.list.get(this.groupDraggingActual);
                        draggedReplay.group.set(targetGroup);

                        /* Refresh the list to update internal state */
                        this.setList(this.list);
                        this.update();
                        this.overlay.setReplay(draggedReplay);
                        this.setIndex(this.groupDraggingActual);
                    }
                    else
                    {
                        /* Handle reordering - convert visual index back to actual index */
                        int targetActualIndex = this.getActualIndexFromVisualIndex(targetVisualIndex);

                        if (targetActualIndex != -1 && targetActualIndex != this.groupDraggingActual)
                        {
                            this.handleSwap(this.groupDraggingActual, targetActualIndex);
                        }
                    }
                }
                else
                {
                    /* Dragged outside - remove from group */
                    Replay draggedReplay = this.list.get(this.groupDraggingActual);
                    draggedReplay.group.set("");
                    
                    this.setList(this.list);
                    this.update();
                    this.overlay.setReplay(draggedReplay);
                    this.setIndex(this.groupDraggingActual);
                }
            }

            /* Always reset dragging state on mouse release */
            this.groupDraggingActual = -1;
            this.groupDraggingVisual = -1;

            return true;
        }

        return super.subMouseReleased(context);
    }


    /**
     * Returns the group name if the visual index corresponds to a group header,
     * or null if it's a replay item or invalid index.
     */
    private String getGroupAtVisualIndex(int visualIndex)
    {
        if (this.isFiltering())
        {
            return null;
        }

        Map<String, List<Replay>> groups = new LinkedHashMap<>();
        List<Replay> ungrouped = new ArrayList<>();


        for (Replay replay : this.list)
        {
            String group = replay.group.get();

            if (group == null || group.trim().isEmpty())
            {
                ungrouped.add(replay);
            }
            else
            {
                if (this.filterGroup.isEmpty() || group.equals(this.filterGroup))
                {
                    groups.computeIfAbsent(group, k -> new ArrayList<>()).add(replay);
                }
            }
        }

        /* Sort groups hierarchically */
        Map<String, List<Replay>> sortedGroups = new LinkedHashMap<>();
        groups.entrySet().stream()
            .sorted((e1, e2) -> {
                String g1 = e1.getKey();
                String g2 = e2.getKey();
                if (g2.startsWith(g1 + "/")) return -1;
                if (g1.startsWith(g2 + "/")) return 1;
                return g1.compareTo(g2);
            })
            .forEachOrdered(e -> sortedGroups.put(e.getKey(), e.getValue()));


        int currentVisualIndex = 0;


        for (Map.Entry<String, List<Replay>> entry : sortedGroups.entrySet())
        {
            if (currentVisualIndex == visualIndex)
            {
                return entry.getKey();
            }

            currentVisualIndex++;

            if (!this.collapsedGroups.getOrDefault(entry.getKey(), false))
            {
                currentVisualIndex += entry.getValue().size();
            }
        }


        return null;
    }


    /**
     * Convert visual index to actual list index, skipping group headers
     */
    private int getActualIndexFromVisualIndex(int targetVisualIndex)
    {
        if (this.isFiltering())
        {
            return targetVisualIndex;
        }

        int visualIndex = 0;
        Map<String, List<Replay>> groups = new LinkedHashMap<>();
        List<Replay> ungrouped = new ArrayList<>();


        for (Replay replay : this.list)
        {
            String group = replay.group.get();

            if (group == null || group.trim().isEmpty())
            {
                ungrouped.add(replay);
            }
            else
            {
                if (this.filterGroup.isEmpty() || group.equals(this.filterGroup))
                {
                    groups.computeIfAbsent(group, k -> new ArrayList<>()).add(replay);
                }
            }
        }

        /* Sort groups hierarchically */
        Map<String, List<Replay>> sortedGroups = new LinkedHashMap<>();
        groups.entrySet().stream()
            .sorted((e1, e2) -> {
                String g1 = e1.getKey();
                String g2 = e2.getKey();
                if (g2.startsWith(g1 + "/")) return -1;
                if (g1.startsWith(g2 + "/")) return 1;
                return g1.compareTo(g2);
            })
            .forEachOrdered(e -> sortedGroups.put(e.getKey(), e.getValue()));


        for (Map.Entry<String, List<Replay>> entry : sortedGroups.entrySet())
        {
            /* Skip group header */
            if (visualIndex == targetVisualIndex)
            {
                return -1;
            }

            visualIndex++;

            if (!this.collapsedGroups.getOrDefault(entry.getKey(), false))
            {
                for (Replay replay : entry.getValue())
                {
                    if (visualIndex == targetVisualIndex)
                    {
                        return this.list.indexOf(replay);
                    }

                    visualIndex++;
                }
            }
        }


        if (!ungrouped.isEmpty() && this.filterGroup.isEmpty())
        {
            for (Replay replay : ungrouped)
            {
                if (visualIndex == targetVisualIndex)
                {
                    return this.list.indexOf(replay);
                }

                visualIndex++;
            }
        }


        return -1;
    }


    @Override
    public int renderElement(UIContext context, Replay element, int i, int index, boolean postDraw)
    {
        /* Skip rendering the element being dragged (it will be drawn at mouse cursor) */
        if (!this.isFiltering() && this.isDragging() && this.groupDraggingVisual == i)
        {
            return i + 1;
        }

        return super.renderElement(context, element, i, index, postDraw);
    }


    @Override
    public void render(UIContext context)
    {
        super.render(context);

        int s = this.scroll.scrollItemSize;

        /* Draw the dragged group at mouse cursor */
        if (!this.isFiltering() && this.isDragging() && this.draggingGroupName != null)
        {
            int depth = this.getGroupDepth(this.draggingGroupName);
            String displayName = this.draggingGroupName.contains("/") ? 
                this.draggingGroupName.substring(this.draggingGroupName.lastIndexOf("/") + 1) : this.draggingGroupName;
            String text = "\u25bc " + displayName;
            
            int bgColor = Colors.A100 | Colors.mulRGB(BBSSettings.primaryColor.get(), 0.75F);
            int textX = context.mouseX + 6 + (depth * 12);
            int textY = context.mouseY - s / 2;
            
            context.batcher.box(textX - 4, textY, textX + 150, textY + s, bgColor);
            context.batcher.textShadow(text, textX, textY + (s - context.batcher.getFont().getHeight()) / 2, Colors.WHITE);
        }

        /* Draw the dragged replay at mouse cursor */
        if (!this.isFiltering() && this.isDragging() && this.groupDraggingActual >= 0 && this.groupDraggingActual < this.list.size())
        {
            Replay dragged = this.list.get(this.groupDraggingActual);

            this.renderListElement(context, dragged, this.groupDraggingActual, context.mouseX + 6, context.mouseY - s / 2, true, true);
        }
    }


    @Override
    protected String elementToString(UIContext context, int i, Replay element)
    {
        return context.batcher.getFont().limitToWidth(element.getName(), this.area.w - 20);
    }

    @Override
    protected void renderElementPart(UIContext context, Replay element, int i, int x, int y, boolean hover, boolean selected)
    {
        if (element.enabled.get())
        {
            super.renderElementPart(context, element, i, x, y, hover, selected);
        }
        else
        {
            context.batcher.textShadow(this.elementToString(context, i, element), x + 4, y + (this.scroll.scrollItemSize - context.batcher.getFont().getHeight()) / 2, hover ? Colors.mulRGB(Colors.HIGHLIGHT, 0.75F) : Colors.GRAY);
        }

        Form form = element.form.get();

        if (form != null)
        {
            x += this.area.w - 30;

            context.batcher.clip(x, y, 40, 20, context);

            y -= 10;

            FormUtilsClient.renderUI(form, context, x, y, x + 40, y + 40);

            context.batcher.unclip(context);

            if (element.fp.get())
            {
                context.batcher.outlinedIcon(Icons.ARROW_UP, x, y + 20, 0.5F, 0.5F);
            }
        }
    }
}