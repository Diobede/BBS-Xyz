package mchorse.bbs_mod.ui.framework.elements.docking;

import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.utils.colors.Colors;

public class UIDockingNode extends UIElement
{
    private static final int BLACK = 0x000000;

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
        
        if (content != null)
        {
            this.add(content.full(this));
        }
    }
    
    public UIElement getContent()
    {
        return this.getChildren().isEmpty() ? null : (UIElement) this.getChildren().get(0);
    }
    
    public boolean isLeaf()
    {
        /* A leaf node has either 0 children (empty) or 1 child (content) */
        /* A split node has exactly 2 children (both are UIDockingNode) */
        return this.getChildren().size() <= 1;
    }

    public void split(boolean vertical, UIElement newContent, boolean first)
    {
        UIElement currentContent = this.getContent();
        this.removeAll();
        
        UIDockingNode nodeA = new UIDockingNode();
        UIDockingNode nodeB = new UIDockingNode();
        
        if (currentContent != null)
        {
            currentContent.removeFromParent();
            nodeA.setContent(currentContent);
        }
        
        nodeB.setContent(newContent);
        
        this.vertical = vertical;
        this.split = 0.5F;
        
        /* If first is true, new content goes to A (top/left), old to B */
        /* If first is false, old to A, new to B */
        
        if (first)
        {
            // Swap contents
            UIElement temp = nodeA.getContent();
            if (temp != null) temp.removeFromParent();
            
            nodeA.setContent(nodeB.getContent());
            nodeB.setContent(temp);
        }
        
        this.add(nodeA, nodeB);
        this.resize();
    }
    
    public void removeChild(UIElement child)
    {
        if (this.isLeaf())
        {
            /* If we are a leaf and child is removed, we become empty */
            this.removeAll();
        }
        else
        {
            /* We are a split node */
            UIElement remaining = null;
            
            if (this.getChildren().contains(child))
            {
                int index = this.getChildren().indexOf(child);
                remaining = (UIElement) this.getChildren().get(index == 0 ? 1 : 0);
            }
            
            if (remaining != null)
            {
                /* Collapse this node: Replace 'this' in parent with 'remaining' */
                UIDockingNode parent = this.getParent(UIDockingNode.class);
                UIDockingRoot root = this.getParent(UIDockingRoot.class);
                
                remaining.removeFromParent();
                
                if (parent != null)
                {
                    parent.replaceChild(this, remaining);
                }
                else if (root != null)
                {
                    root.setRootContent(remaining);
                }
            }
        }
        
        this.resize();
    }
    
    public void replaceChild(UIElement oldChild, UIElement newChild)
    {
        int index = this.getChildren().indexOf(oldChild);
        if (index != -1)
        {
            this.getChildren().set(index, newChild);
            
            /* Properly update parent links using public methods */
            oldChild.removeFromParent();
            this.add(newChild); // This adds to the end, which might mess up order if we rely on index.
            
            // Correction: Since we manually set the list element above, we need to ensure parentage is correct.
            // But we can't access .parent directly.
            // Best approach: Remove old, insert new at specific index.
            
            // Re-doing the logic:
            this.getChildren().set(index, oldChild); // Revert for a sec to use proper removal
            
            // Actually, let's use the provided methods if possible. 
            // Since we are in a subpackage, we can't access .parent.
            // But UIElement.add() sets the parent.
            
            // Let's rely on remove/add but keep order?
            // UIDockingNode relies on index 0 being A and 1 being B.
            
            // Strategy: Clear list, add them back in correct order.
            UIElement other = (UIElement) this.getChildren().get(index == 0 ? 1 : 0);
            this.removeAll();
            
            if (index == 0)
            {
                this.add(newChild, other);
            }
            else
            {
                this.add(other, newChild);
            }
            
            this.resize();
        }
    }
    
    public boolean contains(UIElement target)
    {
        if (target == null) return false;
        
        /* Check direct content */
        for (IUIElement child : this.getChildren())
        {
            if (child == target) return true;
            if (child instanceof UIDockable && ((UIDockable) child).content == target) return true;
            if (child instanceof UIDockingNode && ((UIDockingNode) child).contains(target)) return true;
        }
        
        return false;
    }

    @Override
    public void resize()
    {
        /* Standard resize first to set my own area */
        // super.resize(); // We handle child sizing manually below
        this.resizer.apply(this.area);

        if (!this.isLeaf() && this.getChildren().size() == 2)
        {
            UIElement childA = (UIElement) this.getChildren().get(0);
            UIElement childB = (UIElement) this.getChildren().get(1);
            
            int size = this.vertical ? this.area.h : this.area.w;
            int splitPoint = (int) (size * this.split);

            if (this.vertical)
            {
                childA.set(this.area.x, this.area.y, this.area.w, splitPoint);
                childB.set(this.area.x, this.area.y + splitPoint, this.area.w, this.area.h - splitPoint);
            }
            else
            {
                childA.set(this.area.x, this.area.y, splitPoint, this.area.h);
                childB.set(this.area.x + splitPoint, this.area.y, this.area.w - splitPoint, this.area.h);
            }
            
            childA.resize();
            childB.resize();
        }
        else
        {
            /* Leaf node: let standard resize handle the single child (content) */
            for (IUIElement child : this.getChildren())
            {
                child.resize();
            }
        }
        
        this.resizer.postApply(this.area);
    }

    @Override
    public void render(UIContext context)
    {
        super.render(context);
        
        if (!this.isLeaf() && this.getChildren().size() == 2)
        {
            if (this.vertical)
            {
                int sy = this.area.y + (int)(this.area.h * this.split);
                context.batcher.box(this.area.x, sy - 1, this.area.ex(), sy + 1, Colors.A50 | BLACK);
                
                if (this.draggingSplit || (Math.abs(context.mouseY - sy) < 4 && this.area.isInside(context)))
                {
                    context.batcher.box(this.area.x, sy - 2, this.area.ex(), sy + 2, Colors.ACTIVE);
                }
            }
            else
            {
                int sx = this.area.x + (int)(this.area.w * this.split);
                context.batcher.box(sx - 1, this.area.y, sx + 1, this.area.ey(), Colors.A50 | BLACK);
                
                if (this.draggingSplit || (Math.abs(context.mouseX - sx) < 4 && this.area.isInside(context)))
                {
                    context.batcher.box(sx - 2, this.area.y, sx + 2, this.area.ey(), Colors.ACTIVE);
                }
            }
        }
    }
    
    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        if (!this.isLeaf() && this.area.isInside(context))
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
        
        for (IUIElement child : this.getChildren())
        {
            if (child instanceof UIDockingNode)
            {
                ((UIDockingNode) child).handleDrag(context);
            }
        }
    }
}
