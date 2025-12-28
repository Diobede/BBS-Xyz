package mchorse.bbs_mod.ui.framework.elements.docking;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;

public class UIDockable extends UIElement
{
    public UIElement content;
    public IKey title;
    public Icon icon;
    
    public UIIcon close;
    
    public UIDockable(IKey title, Icon icon, UIElement content)
    {
        super();
        
        this.title = title;
        this.icon = icon;
        this.content = content;
        
        this.close = new UIIcon(Icons.CLOSE, (b) -> this.removeFromParent());
        this.close.relative(this).x(1F, -20).w(20).h(20);
        
        if (content != null)
        {
            this.add(content);
            content.relative(this).y(20).w(1F).h(1F, -20);
        }
        
        this.add(this.close);
    }
    
    @Override
    public void render(UIContext context)
    {
        /* Render Header */
        context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.y + 20, Colors.A50 | 0x000000);
        
        if (this.icon != null)
        {
            context.batcher.icon(this.icon, Colors.WHITE, this.area.x + 10, this.area.y + 10, 0.5F, 0.5F);
        }
        
        if (this.title != null)
        {
            context.batcher.text(this.title.get(), this.area.x + 24, this.area.y + 6);
        }
        
        super.render(context);
    }
    
    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        if (this.area.isInside(context) && context.mouseY < this.area.y + 20)
        {
            if (this.close.area.isInside(context))
            {
                return false;
            }

            /* Initiate Dragging in Root */
            UIDockingRoot root = this.getParent(UIDockingRoot.class);
            if (root != null)
            {
                /* Prevent duplicate drags if for some reason multiple clicks happen */
                root.startDragging(this);
                return true;
            }
        }
        
        return false;
    }
}
