package com.cacheviewer;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.NPCComposition;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.widgets.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.*;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import javax.inject.Inject;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.HashMap;
import java.util.Map;
@Slf4j
@PluginDescriptor(
        name = "Cache Viewer",
        description = "Adds a ::cache command to open an in game cache viewer"
)
public class CacheViewerPlugin extends Plugin
        implements KeyListener, MouseListener, MouseWheelListener
{
    private static final int INTERFACE_GROUP_ID = 4;

    private static final String CATEGORY_SPRITES = "Sprites";
    private static final String CATEGORY_MODELS  = "Models";
    private static final String CATEGORY_NPCS    = "Npcs";
    private static final String CATEGORY_CHATHEADS    = "Chatheads";
    private static final String CATEGORY_ITEMS   = "Items";

    private String selectedCategory = CATEGORY_SPRITES;

    private final Map<String, Integer> categoryIndex = new HashMap<>();

    private boolean dragging = false;
    private int lastMouseX;
    private int lastMouseY;

    private int rotX = 0;
    private int rotZ = 0;

    private int modelZoom = 500;
    private static final int MIN_ZOOM = 250;
    private static final int MAX_ZOOM = 800;

    private boolean cacheViewerOpen = false;
    private static final int NPC_VIEWPORT_HEIGHT = 450;

    @Inject private Client client;
    @Inject private ClientThread clientThread;
    @Inject private KeyManager keyManager;
    @Inject private MouseManager mouseManager;

    private volatile boolean cacheViewerVisible = false;
    private volatile Rectangle viewportBounds = null;

    private CacheViewerSearchBar searchBar;
    private boolean searchActive = false;
    private final StringBuilder searchBuffer = new StringBuilder();

    @Subscribe
    public void onCommandExecuted(CommandExecuted event) // We should open via a command instead
    {
        if (!event.getCommand().equalsIgnoreCase("cache"))
        {
            return;
        }

        clientThread.invokeLater(() ->
        {
            Widget root = client.getWidget(INTERFACE_GROUP_ID, 0);

            if (root != null && !root.isHidden())
            {
                return;
            }

            openInterface();
        });
    }
    @Subscribe
    public void onClientTick(ClientTick tick) // Check if open
    {
        Widget root = client.getWidget(INTERFACE_GROUP_ID, 0);
        cacheViewerVisible = root != null && !root.isHidden();

        Widget template = client.getWidget(4, 4);
        viewportBounds = template != null ? template.getBounds() : null;
    }


    @Override
    protected void startUp()
    {
        categoryIndex.put(CATEGORY_SPRITES, 0);
        categoryIndex.put(CATEGORY_MODELS, 0);
        categoryIndex.put(CATEGORY_NPCS, 0);
        categoryIndex.put(CATEGORY_CHATHEADS, 0);
        categoryIndex.put(CATEGORY_ITEMS, 0);

        searchBar = new CacheViewerSearchBar(client, clientThread, 4, 2);
        searchBar.setOnActivate(this::activateSearch);
    }

    private void activateSearch()
    {
        searchActive = true;
        searchBuffer.setLength(0);
        searchBar.updateValue("");
    }

    private void shutDownPlugin()
    {
        cacheViewerOpen = false;
        dragging = false;
        searchActive = false;

        keyManager.unregisterKeyListener(this);
        mouseManager.unregisterMouseListener(this);
        mouseManager.unregisterMouseWheelListener(this);
    }

    @Override
    protected void shutDown()
    {
        shutDownPlugin();
    }


    private void openInterface()
    {
        clientThread.invokeLater(() ->
        {
            Widget root = client.getWidget(INTERFACE_GROUP_ID, 0);

            // Interface was hidden, show it again
            if (root != null)
            {
                root.setHidden(false);
                cacheViewerOpen = true;

                keyManager.registerKeyListener(this);
                mouseManager.registerMouseListener(this);
                mouseManager.registerMouseWheelListener(this);

                setupUI();
                searchBar.build();
                return;
            }

            // Interface not found, create a new one
            int componentId =
                    (client.getTopLevelInterfaceId() << 16)
                            | (client.isResized() ? 18 : 42);

            if (client.openInterface(
                    componentId,
                    INTERFACE_GROUP_ID,
                    WidgetModalMode.MODAL_NOCLICKTHROUGH) == null)
            {
                log.warn("Failed to open interface {}", INTERFACE_GROUP_ID);
                return;
            }

            cacheViewerOpen = true;

            keyManager.registerKeyListener(this);
            mouseManager.registerMouseListener(this);
            mouseManager.registerMouseWheelListener(this);

            setupUI();
            searchBar.build();
        });
    }




    private void setupUI()
    {
        Widget titleContainer = client.getWidget(4, 1);
        if (titleContainer != null)
        {
            Widget[] children = titleContainer.getDynamicChildren();
            if (children != null && children.length > 1 && children[1] != null)
            {
                children[1].setText("Cache Viewer");
                children[1].revalidate();
            }
        }

        Widget text = client.getWidget(4, 13);
        if (text != null)
        {
            text.setText("Select a category:");
            text.revalidate();
        }

        buildCategoryTabs();
        updateViewport();
    }


    private void buildCategoryTabs()
    {
        Widget container = client.getWidget(4, 12);
        if (container == null) return;

        container.deleteAllChildren();

        addCategoryTab(container, CATEGORY_SPRITES, "Sprites", 30);
        addCategoryTab(container, CATEGORY_MODELS,  "Models",  58);
        addCategoryTab(container, CATEGORY_NPCS,    "NPCs",    86);
        addCategoryTab(container, CATEGORY_CHATHEADS,   "Chatheads", 114);
        addCategoryTab(container, CATEGORY_ITEMS,   "Items", 142);

        container.revalidate();
    }

    private void addCategoryTab(Widget parent, String id, String label, int y) // Tabs, should maybe have sprites for nicer UX
    {
        Widget tab = parent.createChild(-1, WidgetType.TEXT);

        tab.setText(label);
        tab.setName(id.equals(selectedCategory)
                ? "<col=ff9040>" + label + "</col>"
                : label);

        tab.setOriginalX(0);
        tab.setOriginalY(y);
        tab.setOriginalWidth(150);
        tab.setOriginalHeight(28);

        tab.setXPositionMode(WidgetPositionMode.ABSOLUTE_CENTER);
        tab.setYPositionMode(WidgetPositionMode.ABSOLUTE_TOP);
        tab.setWidthMode(WidgetSizeMode.ABSOLUTE);
        tab.setHeightMode(WidgetSizeMode.ABSOLUTE);

        tab.setFontId(495);
        tab.setTextColor(0xFFFFFF);
        tab.setXTextAlignment(WidgetTextAlignment.CENTER);
        tab.setYTextAlignment(WidgetTextAlignment.CENTER);
        tab.setTextShadowed(true);

        tab.setAction(0, "Select");
        tab.setHasListener(true);
        tab.setOnOpListener((JavaScriptCallback) e -> switchCategory(id));

        tab.revalidate();
    }

    private void switchCategory(String category)
    {
        selectedCategory = category;
        dragging = false;

        searchActive = false;
        searchBuffer.setLength(0);

        searchBar.setCategoryLabel(category);
        searchBar.updateValue(String.valueOf(getCurrentIndex()));

        clientThread.invokeLater(() ->
        {
            buildCategoryTabs();
            updateViewport();
            updateInfoText();
        });
    }



    private void updateViewport()
    {
        Widget template = client.getWidget(4, 4);
        if (template == null)
        {
            return;
        }

        Widget parent = template.getParent();
        if (parent == null)
        {
            return;
        }

        clearNpcWidgets(parent);

        int index = getCurrentIndex();

        // SPRITES
        if (CATEGORY_SPRITES.equals(selectedCategory))
        {
            template.setHidden(false);
            template.setType(WidgetType.GRAPHIC);
            template.setSpriteId(index);
            template.revalidate();
            return;
        }

        // MODELS
        if (CATEGORY_MODELS.equals(selectedCategory))
        {
            template.setHidden(false);
            template.setType(WidgetType.MODEL);
            template.setModelType(WidgetModelType.MODEL);
            template.setModelId(index);
            applyModelSettings(template);
            return;
        }

        // ITEMS
        if (CATEGORY_ITEMS.equals(selectedCategory))
        {
            template.setHidden(false);
            template.setType(WidgetType.MODEL);
            template.setModelType(WidgetModelType.ITEM);
            template.setModelId(index);
            applyModelSettings(template);
            return;
        }

        // CHATHEADS
        if (CATEGORY_CHATHEADS.equals(selectedCategory))
        {
            template.setHidden(false);
            template.setType(WidgetType.MODEL);
            template.setModelType(WidgetModelType.NPC_CHATHEAD);
            template.setModelId(index);
            applyModelSettings(template);
            template.revalidate();
            return;
        }

        // NPCs (multi-widget causes overlap issues, but fixing would be effort)
        if (CATEGORY_NPCS.equals(selectedCategory))
        {
            template.setHidden(true);

            NPCComposition npc = client.getNpcDefinition(index);
            if (npc == null || npc.getModels() == null)
            {
                parent.revalidate();
                return;
            }

            for (int modelId : npc.getModels())
            {
                Widget w = parent.createChild(-1, WidgetType.MODEL);

                w.setOriginalX(template.getOriginalX());
                w.setOriginalY(0);
                w.setOriginalWidth(template.getOriginalWidth());
                w.setOriginalHeight(NPC_VIEWPORT_HEIGHT);

                w.setXPositionMode(WidgetPositionMode.ABSOLUTE_CENTER);
                w.setYPositionMode(WidgetPositionMode.ABSOLUTE_TOP);
                w.setWidthMode(WidgetSizeMode.ABSOLUTE);
                w.setHeightMode(WidgetSizeMode.ABSOLUTE);

                w.setType(WidgetType.MODEL);
                w.setModelType(WidgetModelType.MODEL);
                w.setModelId(modelId);

                applyModelSettings(w);

                w.setHidden(false);
                w.revalidate();
            }

            parent.revalidate();
            return;
        }

        template.setHidden(true);
        parent.revalidate();
    }



    private void updateInfoText()
    {
        Widget info = client.getWidget(4, 13);
        if (info == null)
        {
            return;
        }

        int index = getCurrentIndex();

        // Default text when no metadata is present
        String text = "Select a category:";

        if (CATEGORY_ITEMS.equals(selectedCategory))
        {
            ItemComposition item = client.getItemDefinition(index);
            if (item != null && item.getName() != null)
            {
                text = item.getName().replace(" (Members)", ""); // For the f2p newbies
            }
        }
        else if (CATEGORY_NPCS.equals(selectedCategory)
                || CATEGORY_CHATHEADS.equals(selectedCategory))
        {
            NPCComposition npc = client.getNpcDefinition(index);
            if (npc != null && npc.getName() != null)
            {
                text = npc.getName();
            }
        }

        info.setText(text);
        info.revalidate();
    }



    private void applyModelSettings(Widget w)
    {
        w.setModelZoom(modelZoom);
        w.setRotationX(rotX);
        w.setRotationZ(rotZ);
        w.revalidate();
    }



    private void clearNpcWidgets(Widget parent)
    {
        Widget[] children = parent.getDynamicChildren();
        if (children == null) return;

        int templateId = client.getWidget(4, 4).getId();

        for (Widget child : children)
        {
            if (child != null
                    && child.getType() == WidgetType.MODEL
                    && child.getId() != templateId)
            {
                child.setHidden(true);
            }
        }
    }



    @Override
    public MouseEvent mousePressed(MouseEvent e)
    {
        if (!cacheViewerVisible || viewportBounds == null)
        {
            return e;
        }

        if (!viewportBounds.contains(e.getX(), e.getY()))
        {
            return e;
        }

        dragging = true;
        lastMouseX = e.getX();
        lastMouseY = e.getY();
        e.consume();
        return e;
    }


    @Override
    public MouseEvent mouseReleased(MouseEvent e)
    {
        dragging = false;
        return e;
    }

    @Override
    public MouseEvent mouseDragged(MouseEvent e)
    {
        if (!cacheViewerVisible || !dragging)
        {
            return e;
        }

        int dx = e.getX() - lastMouseX;
        int dy = e.getY() - lastMouseY;

        lastMouseX = e.getX();
        lastMouseY = e.getY();

        rotZ = wrap(rotZ - dx * 4);
        rotX = wrap(rotX + dy * 4);

        clientThread.invokeLater(this::updateViewport);
        e.consume();
        return e;
    }


    @Override
    public MouseWheelEvent mouseWheelMoved(MouseWheelEvent e)
    {
        if (!cacheViewerVisible)
        {
            return e;
        }

        modelZoom += e.getWheelRotation() * 50;
        modelZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, modelZoom));

        clientThread.invokeLater(this::updateViewport);
        e.consume();
        return e;
    }


    @Override
    public void keyTyped(KeyEvent e)
    {
        if (cacheViewerVisible && searchActive)
        {
            e.consume();
        }
    }


    @Override
    public void keyPressed(KeyEvent e)
    {
        if (!cacheViewerVisible)
        {
            return;
        }

        if (searchActive)
        {
            handleSearchInput(e);
            e.consume();
            return;
        }

        int code = e.getKeyCode();

        if (code == KeyEvent.VK_LEFT || code == KeyEvent.VK_RIGHT)
        {
            int index = getCurrentIndex();
            index += (code == KeyEvent.VK_LEFT ? -1 : 1);
            setCurrentIndex(index);

            searchBar.updateValue(String.valueOf(getCurrentIndex()));

            clientThread.invokeLater(() ->
            {
                updateViewport();
                updateInfoText();
            });

            e.consume();
        }
    }



    private void handleSearchInput(KeyEvent e)
    {
        if (!cacheViewerVisible)
        {
            return;
        }

        char c = e.getKeyChar();

        if (Character.isDigit(c))
        {
            searchBuffer.append(c);
        }
        else if (e.getKeyCode() == KeyEvent.VK_BACK_SPACE && searchBuffer.length() > 0)
        {
            searchBuffer.deleteCharAt(searchBuffer.length() - 1);
        }


        else if (e.getKeyCode() == KeyEvent.VK_ENTER)
        {
            if (searchBuffer.length() > 0)
            {
                int value = parseSafe(searchBuffer.toString(), getCurrentIndex());
                setCurrentIndex(value);

                searchBar.updateValue(String.valueOf(value));

                clientThread.invokeLater(() ->
                {
                    updateViewport();
                    updateInfoText();
                });
            }

            searchActive = false;
            searchBuffer.setLength(0);
            e.consume();
            return;
        }

        else if (e.getKeyCode() == KeyEvent.VK_ESCAPE)
        {
            searchActive = false;
            searchBuffer.setLength(0);

            searchBar.updateValue(String.valueOf(getCurrentIndex()));
            e.consume();
            return;
        }

        searchBar.updateValue(searchBuffer.toString());
        e.consume();
    }


    private int parseSafe(String s, int fallback)
    {
        try
        {
            return Integer.parseInt(s);
        }
        catch (NumberFormatException e)
        {
            return fallback;
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {

    }

    private int getCurrentIndex()
    {
        return categoryIndex.getOrDefault(selectedCategory, 0);
    }

    private void setCurrentIndex(int index)
    {
        categoryIndex.put(selectedCategory, Math.max(0, index));
    }

    private static int wrap(int a)
    {
        a %= 2048;
        return a < 0 ? a + 2048 : a;
    }

    @Override public MouseEvent mouseClicked(MouseEvent e) { return e; }
    @Override public MouseEvent mouseEntered(MouseEvent e) { return e; }
    @Override public MouseEvent mouseExited(MouseEvent e) { return e; }
    @Override public MouseEvent mouseMoved(MouseEvent e) { return e; }
}