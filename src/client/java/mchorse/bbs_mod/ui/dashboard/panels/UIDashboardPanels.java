package mchorse.bbs_mod.ui.dashboard.panels;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.docking.UIDockable;
import mchorse.bbs_mod.ui.framework.elements.docking.UIDockingNode;
import mchorse.bbs_mod.ui.framework.elements.docking.UIDockingRoot;
import mchorse.bbs_mod.ui.framework.elements.events.UIEvent;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.framework.elements.utils.UIRenderable;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.ScrollDirection;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UIDashboardPanels extends UIElement
{
    public List<UIDashboardPanel> panels = new ArrayList<>();
    public Map<UIDashboardPanel, PanelData> panelData = new HashMap<>();
    public UIDashboardPanel panel;

    public UIDockingRoot dockRoot;
    public UIElement taskBar;
    public UIElement pinned;
    public UIScrollView panelButtons;

    public static class PanelData
    {
        public IKey title;
        public Icon icon;
        
        public PanelData(IKey title, Icon icon)
        {
            this.title = title;
            this.icon = icon;
        }
    }

    public static void renderHighlight(Batcher2D batcher, Area area)
    {
        int color = BBSSettings.primaryColor.get();

        batcher.box(area.x, area.ey() - 2, area.ex(), area.ey(), Colors.A100 | color);
        batcher.gradientVBox(area.x, area.y, area.ex(), area.ey() - 2, color, Colors.A75 | color);
    }

    public static void renderHighlightHorizontal(Batcher2D batcher, Area area)
    {
        int color = BBSSettings.primaryColor.get();

        batcher.box(area.ex() - 2, area.y, area.ex(), area.ey(), Colors.A100 | color);
        batcher.gradientHBox(area.x, area.y, area.ex() - 2, area.ey(), color, Colors.A75 | color);
    }

    public UIDashboardPanels()
    {
        this.dockRoot = new UIDockingRoot();
        this.dockRoot.relative(this).w(1F).h(1F, -20);
        this.add(this.dockRoot);

        this.taskBar = new UIElement();
        this.taskBar.relative(this).y(1F, -20).w(1F).h(20);
        this.pinned = new UIElement();
        this.pinned.relative(this.taskBar).h(20).row(0).resize();
        this.panelButtons = new UIScrollView(ScrollDirection.HORIZONTAL);
        this.panelButtons.relative(this.pinned).x(1F, 5).h(20).wTo(this.taskBar.area, 1F).column(0).scroll();
        this.panelButtons.scroll.cancelScrolling().noScrollbar();
        this.panelButtons.scroll.scrollSpeed = 5;
        this.panelButtons.preRender((context) ->
        {
            for (int i = 0, c = this.panels.size(); i < c; i++)
            {
                if (this.panel == this.panels.get(i))
                {
                    renderHighlight(context.batcher, ((UIIcon) this.panelButtons.getChildren().get(i)).area);
                }
            }
        });

        this.taskBar.add(new UIRenderable(this::renderBackground), this.pinned, this.panelButtons);
        this.add(this.taskBar);
    }

    public <T> T getPanel(Class<T> clazz)
    {
        for (UIDashboardPanel panel : this.panels)
        {
            if (panel.getClass() == clazz)
            {
                return (T) panel;
            }
        }

        return null;
    }

    public boolean isFlightSupported()
    {
        return this.panel instanceof IFlightSupported;
    }

    public void open()
    {
        for (UIDashboardPanel panel : this.panels)
        {
            panel.open();
        }
    }

    public void close()
    {
        for (UIDashboardPanel panel : this.panels)
        {
            panel.close();
        }
    }

    public void setPanel(UIDashboardPanel panel, boolean docked)
    {
        UIDashboardPanel lastPanel = this.panel;

        if (BBSSettings.developerNewUI.get())
        {
            if (docked)
            {
                // Docking logic (Right Click)
                if (this.dockRoot.rootNode.contains(panel))
                {
                    // Already docked, just activate it
                    // Find the dockable containing this panel and make sure it's visible?
                    // Dockables are always visible.
                    this.panel = panel;
                    this.getEvents().emit(new PanelEvent(this, lastPanel, panel));
                    return;
                }
                
                // Add to dock
                PanelData data = this.panelData.get(panel);
                UIDockable dockable = new UIDockable(data != null ? data.title : IKey.raw("Unknown"), data != null ? data.icon : null, panel);
                
                if (this.dockRoot.rootNode.isLeaf() && this.dockRoot.rootNode.getContent() == null)
                {
                    this.dockRoot.setRootContent(dockable);
                }
                else
                {
                    this.dockRoot.rootNode.split(false, dockable, false);
                }
            }
            else
            {
                // Preview/Floating logic (Left Click)
                // We want to replace the content of the currently active or first docking node with this panel.
                
                // 1. Check if panel is already somewhere in the dock
                if (this.dockRoot.rootNode.contains(panel))
                {
                    this.panel = panel;
                    this.getEvents().emit(new PanelEvent(this, lastPanel, panel));
                    return;
                }
                
                // 2. If not, find a target node to replace content
                UIDockingNode target = null;
                
                // If the root is empty, it's the target
                if (this.dockRoot.rootNode.isLeaf() && this.dockRoot.rootNode.getContent() == null)
                {
                    target = this.dockRoot.rootNode;
                }
                else
                {
                    // Find the first leaf node
                    target = findFirstLeaf(this.dockRoot.rootNode);
                }
                
                if (target != null)
                {
                    // Before replacing, we should check if the current content is "docked" (persistent) or "preview" (transient)?
                    // For now, we just swap.
                    // IMPORTANT: We need to remove the panel from its previous parent if it was attached elsewhere
                    panel.removeFromParent();
                    
                    PanelData data = this.panelData.get(panel);
                    // Create new dockable wrapper
                    UIDockable dockable = new UIDockable(data != null ? data.title : IKey.raw("Unknown"), data != null ? data.icon : null, panel);
                    
                    target.setContent(dockable);
                    target.resize(); // Force resize to ensure layout updates immediately
                }
            }
            
            this.panel = panel;
            this.getEvents().emit(new PanelEvent(this, lastPanel, panel));
            if (this.panel != null) this.panel.appear();
        }
        else
        {
            // Legacy behavior
            this.setPanelLegacy(panel);
        }
    }

    private UIDockingNode findFirstLeaf(UIDockingNode node)
    {
        if (node.isLeaf()) return node;
        
        if (!node.getChildren().isEmpty())
        {
             UIElement child = (UIElement) node.getChildren().get(0);
             if (child instanceof UIDockingNode) return findFirstLeaf((UIDockingNode) child);
        }
        return null;
    }

    public void setPanel(UIDashboardPanel panel)
    {
        // Default to docked=true for legacy calls, but for our new UI interactions we specify
        this.setPanel(panel, true); 
    }

    public void setPanelLegacy(UIDashboardPanel panel)
    {
        UIDashboardPanel lastPanel = this.panel;
        if (this.panel != null)
        {
            this.panel.disappear();
            this.panel.removeFromParent();
        }

        this.panel = panel;
        this.add(this.panel);
        this.panel.relative(this).w(1F).h(1F, -20);
        this.panel.resize();

        this.getEvents().emit(new PanelEvent(this, lastPanel, panel));

        if (this.panel != null)
        {
            this.panel.appear();
        }
    }

    public UIIcon registerPanel(UIDashboardPanel panel, IKey tooltip, Icon icon)
    {
        this.panelData.put(panel, new PanelData(tooltip, icon));
        
        UIIcon button = new UIIcon(icon, (b) ->
         {
             // Default left click handled here if New UI is OFF
             if (!BBSSettings.developerNewUI.get())
             {
                 this.setPanel(panel);
             }
             else
             {
                 // New UI Left Click: Open without docking (Replace active/first)
                 this.setPanel(panel, false);
             }
         })
         {
             @Override
             public boolean subMouseClicked(UIContext context)
             {
                 // Custom right click handling if New UI is ON
                 if (BBSSettings.developerNewUI.get() && this.area.isInside(context) && context.mouseButton == 1)
                 {
                     UIDashboardPanels.this.setPanel(panel, true); // Docked = true
                     return true;
                 }
                 
                 return super.subMouseClicked(context);
             }
         };

        button.tooltip(tooltip, Direction.TOP);

        this.panels.add(panel);
        this.panelButtons.add(button);

        return button;
    }

    protected void renderBackground(UIContext context)
    {
        Area area = this.taskBar.area;
        Area a = this.pinned.area;

        context.batcher.box(area.x, area.y, area.ex(), area.ey(), Colors.CONTROL_BAR);
        context.batcher.box(a.ex() + 2, a.y + 3, a.ex() + 3, a.ey() - 3, 0x44ffffff);
    }

    public static class PanelEvent extends UIEvent<UIDashboardPanels>
    {
        public final UIDashboardPanel lastPanel;
        public final UIDashboardPanel panel;

        public PanelEvent(UIDashboardPanels element, UIDashboardPanel lastPanel, UIDashboardPanel panel)
        {
            super(element);

            this.lastPanel = lastPanel;
            this.panel = panel;
        }
    }
}
