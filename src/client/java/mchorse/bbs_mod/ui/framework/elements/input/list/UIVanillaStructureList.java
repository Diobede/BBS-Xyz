package mchorse.bbs_mod.ui.framework.elements.input.list;

import mchorse.bbs_mod.forms.StructureLikeManager;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtTagSizeTracker;
import net.minecraft.util.WorldSavePath;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * List component for vanilla Minecraft structure files (.nbt)
 * 
 * Provides visual browser for structures from:
 * - .minecraft/structures/ (minecraft:)
 * - world/generated/minecraft/structures/ (world:)
 */
public class UIVanillaStructureList extends UIStringList
{
    private final Map<String, StructureInfo> structureInfoMap = new HashMap<>();
    private UIIcon likeButton;
    private Runnable likeToggleCallback;
    private StructureLikeManager likeManager;
    private boolean loaded = false;

    public UIVanillaStructureList(Consumer<List<String>> callback, StructureLikeManager likeManager)
    {
        super(callback);
        this.likeButton = new UIIcon(Icons.LIKE, null);
        this.likeManager = likeManager;
    }

    /**
     * Set like toggle callback
     */
    public void setLikeToggleCallback(Runnable callback)
    {
        this.likeToggleCallback = callback;
    }

    /**
     * Ensure list is loaded (lazy loading)
     */
    private void ensureLoaded()
    {
        if (!this.loaded)
        {
            this.loadVanillaStructures();
            this.populateList();
            this.loaded = true;
        }
    }

    /**
     * Load all vanilla structure files from multiple sources
     */
    private void loadVanillaStructures()
    {
        this.structureInfoMap.clear();
        MinecraftClient mc = MinecraftClient.getInstance();

        /* 1. Scan .minecraft/structures/ */
        File minecraftStructures = new File(mc.runDirectory, "structures");
        
        if (minecraftStructures.exists() && minecraftStructures.isDirectory())
        {
            this.scanFolder(minecraftStructures, minecraftStructures, "minecraft:", "Minecraft");
        }
    }

    /**
     * Scan folder recursively for .nbt files
     */
    private void scanFolder(File root, File folder, String prefix, String source)
    {
        if (!folder.exists() || !folder.isDirectory())
        {
            return;
        }

        try (Stream<Path> paths = Files.walk(folder.toPath()))
        {
            paths.filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".nbt"))
                .forEach(path ->
                {
                    try
                    {
                        String relativePath = root.toPath().relativize(path).toString().replace("\\", "/");
                        String fullPath = prefix + relativePath;
                        
                        /* Load structure NBT to get metadata */
                        StructureInfo info = new StructureInfo();
                        info.path = fullPath;
                        info.name = path.getFileName().toString().replace(".nbt", "");
                        info.source = source;
                        
                        try (DataInputStream dis = new DataInputStream(new FileInputStream(path.toFile())))
                        {
                            NbtCompound nbt = NbtIo.readCompressed(dis, NbtTagSizeTracker.ofUnlimitedBytes());
                            
                            if (nbt.contains("size"))
                            {
                                NbtList sizeList = nbt.getList("size", NbtElement.INT_TYPE);
                                info.sizeX = sizeList.getInt(0);
                                info.sizeY = sizeList.getInt(1);
                                info.sizeZ = sizeList.getInt(2);
                            }
                            
                            if (nbt.contains("blocks"))
                            {
                                info.blockCount = nbt.getList("blocks", NbtElement.COMPOUND_TYPE).size();
                            }
                        }
                        
                        this.structureInfoMap.put(fullPath, info);
                    }
                    catch (Exception e)
                    {
                        /* Skip invalid structures */
                    }
                });
        }
        catch (Exception e)
        {
            System.err.println("Failed to scan folder: " + folder + " - " + e.getMessage());
        }
    }

    /**
     * Populate list with loaded structures
     */
    private void populateList()
    {
        this.list.clear();

        for (String key : this.structureInfoMap.keySet())
        {
            StructureInfo info = this.structureInfoMap.get(key);
            String prefix = "[" + info.source + "]: ";
            String displayName = prefix + info.name;

            this.list.add(displayName);
        }

        this.list.sort(String::compareToIgnoreCase);
        this.update();
    }

    /**
     * Remove category prefix from display name
     */
    private String removePrefix(String displayName)
    {
        int endBracket = displayName.indexOf(']');

        if (endBracket > 0 && displayName.startsWith("["))
        {
            int colonSpace = displayName.indexOf("]: ", endBracket);

            if (colonSpace > 0)
            {
                return displayName.substring(colonSpace + 3);
            }
        }

        return displayName;
    }

    /**
     * Get actual structure path from display name
     */
    private String getStructurePath(String displayName)
    {
        String name = this.removePrefix(displayName);
        
        for (String key : this.structureInfoMap.keySet())
        {
            StructureInfo info = this.structureInfoMap.get(key);
            
            if (info.name.equals(name))
            {
                return info.path;
            }
        }

        return null;
    }

    @Override
    protected void renderElementPart(UIContext context, String element, int i, int x, int y, boolean hover, boolean selected)
    {
        int textWidth = context.batcher.getFont().getWidth(element);
        int buttonSpace = 20; /* like button = 20px */
        int maxWidth = this.area.w - 8 - buttonSpace;

        String displayText = element;

        if (textWidth > maxWidth)
        {
            displayText = context.batcher.getFont().limitToWidth(element, maxWidth);
        }

        context.batcher.textShadow(displayText, x + 4, y + (this.scroll.scrollItemSize - context.batcher.getFont().getHeight()) / 2, hover ? Colors.HIGHLIGHT : Colors.WHITE);

        /* Render like button */
        int currentIconX = this.area.x + this.area.w - 20;
        int iconY = y + (this.scroll.scrollItemSize - 16) / 2;

        String structurePath = this.getStructurePath(element);
        boolean isLiked = structurePath != null && this.likeManager.isStructureLiked(structurePath);
        boolean isHoverOnLike = this.area.isInside(context)
            && context.mouseX >= currentIconX
            && context.mouseX < currentIconX + 16
            && context.mouseY >= iconY
            && context.mouseY < iconY + 16;

        this.likeButton.both(isLiked ? Icons.DISLIKE : Icons.LIKE);
        this.likeButton.iconColor(isHoverOnLike || isLiked ? Colors.WHITE : Colors.GRAY);
        this.likeButton.area.set(currentIconX, iconY, 16, 16);
        this.likeButton.render(context);
    }

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        if (this.area.isInside(context) && context.mouseButton == 0)
        {
            int scrollIndex = this.scroll.getIndex(context.mouseX, context.mouseY);
            String element = this.getElementAt(scrollIndex);

            if (element != null)
            {
                int y = this.area.y + scrollIndex * this.scroll.scrollItemSize - (int) this.scroll.getScroll();
                int iconY = y + (this.scroll.scrollItemSize - 16) / 2;
                int likeIconX = this.area.x + this.area.w - 20;

                /* Check if like button was clicked */
                if (
                    context.mouseX >= likeIconX &&
                    context.mouseX < likeIconX + 16 &&
                    context.mouseY >= iconY &&
                    context.mouseY < iconY + 16
                ) {
                    String structurePath = this.getStructurePath(element);
                    
                    if (structurePath != null)
                    {
                        this.likeManager.toggleStructureLiked(structurePath);

                        if (this.likeToggleCallback != null)
                        {
                            this.likeToggleCallback.run();
                        }
                    }

                    return true;
                }
            }
        }

        boolean result = super.subMouseClicked(context);

        /* Replace selected display name with actual path in callback */
        if (result && this.callback != null)
        {
            List<String> current = this.getCurrent();
            
            if (!current.isEmpty())
            {
                List<String> paths = new ArrayList<>();
                
                for (String displayName : current)
                {
                    String path = this.getStructurePath(displayName);
                    
                    if (path != null)
                    {
                        paths.add(path);
                    }
                }

                if (!paths.isEmpty())
                {
                    this.callback.accept(paths);
                }

                return true;
            }
        }

        return result;
    }

    @Override
    public void render(UIContext context)
    {
        this.ensureLoaded();
        
        super.render(context);

        /* Render structure info if selected */
        if (this.getIndex() > 0)
        {
            String selected = this.list.get(this.getIndex());
            String path = this.getStructurePath(selected);
            StructureInfo info = path != null ? this.structureInfoMap.get(path) : null;

            if (info != null)
            {
                int y = this.area.ey() + 5;
                
                /* Render info panel */
                context.batcher.box(this.area.x, y, this.area.ex(), y + 60, Colors.A50);
                
                y += 5;
                context.batcher.textCard("Name: " + info.name, this.area.x + 5, y);
                y += 12;
                context.batcher.textCard("Source: " + info.source, this.area.x + 5, y);
                y += 12;
                context.batcher.textCard("Blocks: " + info.blockCount, this.area.x + 5, y);
                y += 12;
                
                if (info.sizeX > 0)
                {
                    context.batcher.textCard(
                        "Size: " + info.sizeX + "x" + info.sizeY + "x" + info.sizeZ, 
                        this.area.x + 5, y);
                }
            }
        }
    }

    /**
     * Refresh the structure list
     */
    public void refresh()
    {
        this.loaded = false;
        this.ensureLoaded();
    }

    /**
     * Structure metadata
     */
    private static class StructureInfo
    {
        public String path;
        public String name;
        public String source;
        public int blockCount;
        public int sizeX;
        public int sizeY;
        public int sizeZ;
    }
}
