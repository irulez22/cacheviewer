package com.cacheviewer;

import net.runelite.api.Client;
import net.runelite.api.widgets.*;
import net.runelite.client.callback.ClientThread;

public class CacheViewerSearchBar
{
    private static final int FONT = 495;

    // OSRS search bar sprites
    private static final int SPRITE_LEFT  = 1123;
    private static final int SPRITE_MID   = 1124;
    private static final int SPRITE_RIGHT = 1125;

    private final Client client;
    private final ClientThread clientThread;
    private final int parentGroup;
    private final int parentChild;

    private Widget container;
    private Widget categoryLabel;
    private Widget valueText;

    private Runnable onActivate;

    public CacheViewerSearchBar(
            Client client,
            ClientThread clientThread,
            int parentGroup,
            int parentChild)
    {
        this.client = client;
        this.clientThread = clientThread;
        this.parentGroup = parentGroup;
        this.parentChild = parentChild;
    }

    public void build()
    {
        clientThread.invokeLater(this::buildInternal);
    }

    public void setCategoryLabel(String label)
    {
        if (categoryLabel == null)
        {
            return;
        }

        clientThread.invokeLater(() ->
        {
            categoryLabel.setText(label + ":");
            categoryLabel.revalidate();
        });
    }

    public void updateValue(String text)
    {
        if (valueText == null)
        {
            return;
        }

        clientThread.invokeLater(() ->
        {
            valueText.setText(text);
            valueText.revalidate();
        });
    }

    public void setOnActivate(Runnable r)
    {
        this.onActivate = r;
    }

    public Widget getContainer()
    {
        return container;
    }



    private void buildInternal()
    {
        Widget parent = client.getWidget(parentGroup, parentChild);
        if (parent == null)
        {
            return;
        }

        container = parent.createChild(-1, WidgetType.LAYER);

        container.setOriginalX(270);
        container.setOriginalY(230);
        container.setOriginalWidth(300);
        container.setOriginalHeight(35);

        container.setXPositionMode(WidgetPositionMode.ABSOLUTE_LEFT);
        container.setYPositionMode(WidgetPositionMode.ABSOLUTE_TOP);
        container.setWidthMode(WidgetSizeMode.ABSOLUTE);
        container.setHeightMode(WidgetSizeMode.ABSOLUTE);

        container.revalidate();

        buildCategoryLabel(container);
        buildImage(container);
        buildValueText(container);
    }

    private void buildCategoryLabel(Widget parent)
    {
        categoryLabel = parent.createChild(-1, WidgetType.TEXT);

        categoryLabel.setText("Sprites:");
        categoryLabel.setFontId(FONT);
        categoryLabel.setTextColor(0xff981f);
        categoryLabel.setTextShadowed(true);

        categoryLabel.setOriginalX(0);
        categoryLabel.setOriginalY(0);
        categoryLabel.setOriginalWidth(85);
        categoryLabel.setOriginalHeight(35);

        categoryLabel.setXTextAlignment(WidgetTextAlignment.CENTER);
        categoryLabel.setYTextAlignment(WidgetTextAlignment.CENTER);

        categoryLabel.revalidate();
    }

    private void buildImage(Widget parent)
    {
        Widget image = parent.createChild(-1, WidgetType.LAYER);

        image.setOriginalX(85);
        image.setOriginalY(5);
        image.setOriginalWidth(200);
        image.setOriginalHeight(25);

        image.setWidthMode(WidgetSizeMode.ABSOLUTE);
        image.setHeightMode(WidgetSizeMode.ABSOLUTE);

        buildImageCaps(image);
        image.revalidate();
    }

    private void buildImageCaps(Widget parent)
    {
        Widget mid = parent.createChild(-1, WidgetType.GRAPHIC);
        mid.setSpriteId(SPRITE_MID);
        mid.setOriginalWidth(200);
        mid.setOriginalHeight(25);
        mid.revalidate();

        Widget left = parent.createChild(-1, WidgetType.GRAPHIC);
        left.setSpriteId(SPRITE_LEFT);
        left.setOriginalWidth(4);
        left.setOriginalHeight(25);
        left.revalidate();

        Widget right = parent.createChild(-1, WidgetType.GRAPHIC);
        right.setSpriteId(SPRITE_RIGHT);
        right.setOriginalWidth(4);
        right.setOriginalHeight(25);
        right.setXPositionMode(WidgetPositionMode.ABSOLUTE_RIGHT);
        right.revalidate();
    }


    private void buildValueText(Widget parent)
    {
        valueText = parent.createChild(-1, WidgetType.TEXT);

        valueText.setText("0");
        valueText.setFontId(FONT);
        valueText.setTextColor(0xff981f);
        valueText.setTextShadowed(true);

        valueText.setOriginalX(90);
        valueText.setOriginalY(9);
        valueText.setOriginalWidth(100);
        valueText.setOriginalHeight(17);

        valueText.setXTextAlignment(WidgetTextAlignment.LEFT);
        valueText.setYTextAlignment(WidgetTextAlignment.CENTER);

        valueText.setHasListener(true);
        valueText.setAction(0, "Edit");
        valueText.setOnOpListener((JavaScriptCallback) e ->
        {
            if (onActivate != null)
            {
                onActivate.run();
            }
        });

        valueText.revalidate();
    }
}
