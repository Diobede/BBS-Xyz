package mchorse.bbs_mod.ui.framework.elements.docking;

import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.utils.colors.Colors;

public class UIDockingNode extends UIElement
{
    private static final int BLACK = 0x000000;

    public UIDockingNode childA;
    public UIDockingNode childB;
    
    public UIElement content;
    
    public boolean vertical = false;
    public float split = 0.5F;
    
    private boolean draggingSplit = false;

    public UIDockingNode()
    {
        super();
        this.markContainer();
    }

    public void setContent(UIElement content)
    {
        this.removeAll();
        this.content = content;
        this.childA = this.childB = null;
        
        if (content != null)
        {
            this.add(content.full(this));
        }
    }

    public void split(boolean vertical, UIElement newContent, boolean first)
    {
        /* We are creating new nodes, so we need to properly transfer ownership */
        UIDockingNode oldNode = new UIDockingNode();
        
        /* Instead of just setting the field, we use setContent to register parent-child relationship */
        if (this.content != null)
        {
            this.content.removeFromParent(); // Detach from 'this'
            oldNode.setContent(this.content);
        }
        
        UIDockingNode newNode = new UIDockingNode();
        newNode.setContent(newContent);
        
        this.content = null;
        this.vertical = vertical;
        this.childA = first ? newNode : oldNode;
        this.childB = first ? oldNode : newNode;
        
        this.removeAll();
        this.add(this.childA, this.childB);
        this.resize();
    }
    
    public boolean contains(UIElement target)
    {
        if (this.content == target) return true;
        if (this.content instanceof UIDockable && ((UIDockable) this.content).content == target) return true;
        
        if (this.childA != null)
        {
            return this.childA.contains(target) || this.childB.contains(target);
        }
        
        return false;
    }

    @Override
    public void resize()
    {
        if (this.childA != null && this.childB != null)
        {
            int size = this.vertical ? this.area.h : this.area.w;
            int splitPoint = (int) (size * this.split);

            if (this.vertical)
            {
                this.childA.set(this.area.x, this.area.y, this.area.w, splitPoint);
                this.childB.set(this.area.x, this.area.y + splitPoint, this.area.w, this.area.h - splitPoint);
            }
            else
            {
                this.childA.set(this.area.x, this.area.y, splitPoint, this.area.h);
                this.childB.set(this.area.x + splitPoint, this.area.y, this.area.w - splitPoint, this.area.h);
            }
            
            this.childA.resize();
            this.childB.resize();
        }
        else if (this.content != null)
        {
            /* Force content to fill the node */
            this.content.set(this.area.x, this.area.y, this.area.w, this.area.h);
            this.content.resize();
        }
        else if (this.content != null)
        {
            this.content.resize();
        }
    }

    @Override
    public void render(UIContext context)
    {
        if (this.childA != null && this.childB != null)
        {
            this.childA.render(context);
            this.childB.render(context);
        }
        else if (this.content != null)
        {
            this.content.render(context);
        }
        
        if (this.childA != null)
        {
            int mx = context.mouseX;
            int my = context.mouseY;
            
            if (this.vertical)
            {
                int sy = this.area.y + (int)(this.area.h * this.split);
                
                context.batcher.box(this.area.x, sy - 1, this.area.ex(), sy + 1, Colors.A50 | BLACK);

                if (this.draggingSplit || (Math.abs(my - sy) < 4 && this.area.isInside(context)))
                {
                    context.batcher.box(this.area.x, sy - 2, this.area.ex(), sy + 2, Colors.ACTIVE);
                }
            }
            else
            {
                int sx = this.area.x + (int)(this.area.w * this.split);
                
                context.batcher.box(sx - 1, this.area.y, sx + 1, this.area.ey(), Colors.A50 | BLACK);

                if (this.draggingSplit || (Math.abs(mx - sx) < 4 && this.area.isInside(context)))
                {
                    context.batcher.box(sx - 2, this.area.y, sx + 2, this.area.ey(), Colors.ACTIVE);
                }
            }
        }
    }
    
    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        if (this.childA != null && this.area.isInside(context))
        {
             if (this.vertical)
             {
                 int sy = this.area.y + (int)(this.area.h * this.split);
                 
                 if (Math.abs(context.mouseY - sy) < 4)
                 {
                     this.draggingSplit = true;
                     return true;
                 }
             }
             else
             {
                 int sx = this.area.x + (int)(this.area.w * this.split);
                 
                 if (Math.abs(context.mouseX - sx) < 4)
                 {
                     this.draggingSplit = true;
                     return true;
                 }
             }
        }
        
        return super.subMouseClicked(context);
    }
    
    @Override
    protected boolean subMouseReleased(UIContext context)
    {
        this.draggingSplit = false;
        return super.subMouseReleased(context);
    }
    
    public void handleDrag(UIContext context)
    {
        if (this.draggingSplit)
        {
            if (this.vertical)
            {
                this.split = (float) (context.mouseY - this.area.y) / this.area.h;
            }
            else
            {
                this.split = (float) (context.mouseX - this.area.x) / this.area.w;
            }
            
            this.split = Math.max(0.1F, Math.min(0.9F, this.split));
            this.resize();
        }
        
        if (this.childA != null)
        {
            this.childA.handleDrag(context);
            this.childB.handleDrag(context);
        }
    }
}
