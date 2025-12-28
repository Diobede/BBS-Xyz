package mchorse.bbs_mod.ui.framework.elements.docking;

import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.colors.Colors;

public class UIDockingRoot extends UIElement
{
    public UIDockingNode rootNode;
    
    private UIDockable dragging;
    private UIDockingNode targetNode;
    private int dropZone = -1; /* 0: Center, 1: Top, 2: Bottom, 3: Left, 4: Right */
    
    public UIDockingRoot()
    {
        super();
        
        this.rootNode = new UIDockingNode();
        this.add(this.rootNode.full(this));
    }
    
    public void setRootContent(UIElement content)
    {
        this.rootNode.setContent(content);
    }
    
    public void startDragging(UIDockable dockable)
    {
        this.dragging = dockable;
    }
    
    @Override
    public void render(UIContext context)
    {
        super.render(context);
        
        if (this.dragging != null)
        {
            /* Render Dragging Preview */
            int w = 150;
            int h = 30;
            context.batcher.box(context.mouseX - w/2, context.mouseY - h/2, context.mouseX + w/2, context.mouseY + h/2, Colors.A75 | Colors.ACTIVE);
            context.batcher.text(this.dragging.title.get(), context.mouseX - w/2 + 10, context.mouseY - h/2 + 10);
            
            this.findDropZone(context, this.rootNode);
            this.renderDropZone(context);
            
            /* Handle mouse release manually since we are in global drag mode */
            if (!context.menu.context.isHeld(0)) // 0 is Left Mouse Button
            {
                this.drop(context);
                this.dragging = null;
                this.targetNode = null;
                this.dropZone = -1;
            }
        }
        else
        {
            /* Pass drag event to nodes for resizing */
            if (context.menu.context.isHeld(0))
            {
                this.rootNode.handleDrag(context);
            }
        }
    }
    
    private void findDropZone(UIContext context, UIDockingNode node)
    {
        if (node.childA != null)
        {
            findDropZone(context, node.childA);
            findDropZone(context, node.childB);
            return;
        }
        
        if (node.area.isInside(context))
        {
            this.targetNode = node;
            
            int x = context.mouseX - node.area.x;
            int y = context.mouseY - node.area.y;
            int w = node.area.w;
            int h = node.area.h;
            
            if (x > w * 0.25 && x < w * 0.75 && y > h * 0.25 && y < h * 0.75)
            {
                this.dropZone = 0; // Center (Tab) - Not implemented yet, treats as replace/cancel
            }
            else
            {
                // Determine closest edge
                int dLeft = x;
                int dRight = w - x;
                int dTop = y;
                int dBottom = h - y;
                
                int min = Math.min(Math.min(dLeft, dRight), Math.min(dTop, dBottom));
                
                if (min == dTop) this.dropZone = 1;
                else if (min == dBottom) this.dropZone = 2;
                else if (min == dLeft) this.dropZone = 3;
                else this.dropZone = 4;
            }
        }
    }
    
    private void renderDropZone(UIContext context)
    {
        if (this.targetNode == null || this.dropZone == -1) return;
        
        Area area = this.targetNode.area;
        int color = Colors.A50 | Colors.ACTIVE;
        
        switch (this.dropZone)
        {
            case 0: // Center
                context.batcher.box(area.x, area.y, area.ex(), area.ey(), color);
                break;
            case 1: // Top
                context.batcher.box(area.x, area.y, area.ex(), area.y + area.h / 2, color);
                break;
            case 2: // Bottom
                context.batcher.box(area.x, area.y + area.h / 2, area.ex(), area.ey(), color);
                break;
            case 3: // Left
                context.batcher.box(area.x, area.y, area.x + area.w / 2, area.ey(), color);
                break;
            case 4: // Right
                context.batcher.box(area.x + area.w / 2, area.y, area.ex(), area.ey(), color);
                break;
        }
    }
    
    private void drop(UIContext context)
    {
        if (this.targetNode != null && this.dropZone != -1)
        {
            /* Remove dragging element from its old parent */
            this.dragging.removeFromParent();

            /* Create a new wrapper for the content because the old UIDockable might be tied to old layout logic */
            UIDockable newDockable = new UIDockable(this.dragging.title, this.dragging.icon, this.dragging.content);
            
            boolean vertical = this.dropZone == 1 || this.dropZone == 2;
            boolean first = this.dropZone == 1 || this.dropZone == 3;
            
            if (this.dropZone == 0)
            {
                this.targetNode.setContent(newDockable);
            }
            else
            {
                this.targetNode.split(vertical, newDockable, first);
            }
        }
    }
}
