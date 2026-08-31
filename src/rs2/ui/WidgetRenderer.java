package rs2.ui;

import java.util.function.Consumer;

import rs2.ClientLayout;
import rs2.cache.def.AnimationSequence;
import rs2.cache.def.ItemDefinition;
import rs2.media.Rasterizer;
import rs2.media.Rasterizer3D;
import rs2.media.TypeFace;
import rs2.media.model.Model;
import rs2.media.sprite.ImageRGB;
import rs2.media.sprite.IndexedImage;
import rs2.media.sprite.ItemSpriteFactory;

/**
 * Recursively renders revision-377 widgets and fixed-style scrollbars.
 *
 * <p>The renderer consumes interface interaction state from
 * {@link InterfaceController}; widget lifecycle and menu construction remain
 * separate concerns. Mutable per-frame rendering inputs are supplied through a
 * compact {@link RenderContext} rather than by reaching back into the top-level
 * client.</p>
 */
public final class WidgetRenderer {

    /**
     * Per-frame values required while drawing one widget tree.
     *
     * @param mouseX current mouse X coordinate
     * @param mouseY current mouse Y coordinate
     * @param animationCycleDelta elapsed client animation cycles
     * @param smallFont small interface font
     * @param plainFont normal interface font
     * @param scrollbarTop top scrollbar cap sprite
     * @param scrollbarBottom bottom scrollbar cap sprite
     * @param scrollbarTrackColor scrollbar track color
     * @param scrollbarThumbColor scrollbar thumb color
     * @param scrollbarHighlightColor scrollbar highlight color
     * @param scrollbarShadowColor scrollbar shadow color
     */
    public record RenderContext(
            int mouseX,
            int mouseY,
            int animationCycleDelta,
            TypeFace smallFont,
            TypeFace plainFont,
            IndexedImage scrollbarTop,
            IndexedImage scrollbarBottom,
            int scrollbarTrackColor,
            int scrollbarThumbColor,
            int scrollbarHighlightColor,
            int scrollbarShadowColor) {
    }

    /** Interface interaction state consulted while drawing hover/drag effects. */
    private final InterfaceController interfaceController;
    /** Widget CS1 evaluator and active-state helper. */
    private final WidgetRuntime widgetRuntime;
    /** Refreshes dynamic widget content before each child is rendered. */
    private final Consumer<Widget> contentUpdater;

    /**
     * Creates a widget renderer.
     *
     * @param interfaceController interface interaction-state owner
     * @param widgetRuntime widget CS1/runtime evaluator
     * @param contentUpdater callback that refreshes dynamic widget content
     */
    public WidgetRenderer(InterfaceController interfaceController, WidgetRuntime widgetRuntime,
            Consumer<Widget> contentUpdater) {
        this.interfaceController = interfaceController;
        this.widgetRuntime = widgetRuntime;
        this.contentUpdater = contentUpdater;
    }

    /**
     * Draws one complete widget tree.
     *
     * @param context current rendering inputs
     * @param y root Y coordinate
     * @param x root X coordinate
     * @param widget root container widget
     * @param scrollY root scroll offset
     */
    public void drawInterface(RenderContext context, int y, int x, Widget widget, int scrollY) {
        drawInterfaceRecursive(context, y, x, widget, scrollY);
    }

    /**
     * Draws the original fixed-width scrollbar for a scrollable widget.
     *
     * @param context current rendering inputs
     * @param scrollY current scroll offset
     * @param x X coordinate
     * @param height visible height
     * @param scrollHeight full scrollable content height
     * @param y Y coordinate
     */
    public void drawScrollbar(RenderContext context, int scrollY, int x, int height, int scrollHeight, int y) {
            int arrowHeight = ClientLayout.SCROLLBAR_ARROW_HEIGHT;
            int scrollbarWidth = ClientLayout.SCROLLBAR_WIDTH;
            int trackHeight = height - arrowHeight * 2;
            context.scrollbarTop().draw(x, y);
            context.scrollbarBottom().draw(x, (y + height) - arrowHeight);
            Rasterizer.drawFilledRectangle(x, y + arrowHeight, scrollbarWidth, trackHeight,
                    context.scrollbarTrackColor());
            int thumbHeight = (trackHeight * height) / scrollHeight;
            if (thumbHeight < ClientLayout.SCROLLBAR_MIN_THUMB_HEIGHT)
                thumbHeight = ClientLayout.SCROLLBAR_MIN_THUMB_HEIGHT;
            int thumbY = ((trackHeight - thumbHeight) * scrollY) / (scrollHeight - height);
            Rasterizer.drawFilledRectangle(x, y + arrowHeight + thumbY, scrollbarWidth, thumbHeight,
                    context.scrollbarThumbColor());
            Rasterizer.drawVerticalLine(x, y + arrowHeight + thumbY, thumbHeight, context.scrollbarHighlightColor());
            Rasterizer.drawVerticalLine(x + 1, y + arrowHeight + thumbY, thumbHeight, context.scrollbarHighlightColor());
            Rasterizer.drawHorizontalLine(x, y + arrowHeight + thumbY, scrollbarWidth, context.scrollbarHighlightColor());
            Rasterizer.drawHorizontalLine(x, y + arrowHeight + 1 + thumbY, scrollbarWidth,
                    context.scrollbarHighlightColor());
            Rasterizer.drawVerticalLine(x + scrollbarWidth - 1, y + arrowHeight + thumbY, thumbHeight,
                    context.scrollbarShadowColor());
            Rasterizer.drawVerticalLine(x + scrollbarWidth - 2, y + arrowHeight + 1 + thumbY, thumbHeight - 1,
                    context.scrollbarShadowColor());
            Rasterizer.drawHorizontalLine(x, y + arrowHeight - 1 + thumbY + thumbHeight, scrollbarWidth,
                    context.scrollbarShadowColor());
            Rasterizer.drawHorizontalLine(x + 1, y + arrowHeight - 2 + thumbY + thumbHeight, scrollbarWidth - 1,
                    context.scrollbarShadowColor());
        }

    /**
     * Recursively renders containers, inventories, text, sprites, models and
     * tooltips under one widget root.
     *
     * @param context current rendering inputs
     * @param y root Y coordinate
     * @param x root X coordinate
     * @param widget root widget
     * @param scrollY root scroll offset
     */
    private void drawInterfaceRecursive(RenderContext context, int y, int x, Widget widget, int scrollY) {
            if (widget.type != Widget.TYPE_CONTAINER || widget.children == null)
                return;
            if (widget.mouseoverTriggered && interfaceController.viewportHoveredWidgetId() != widget.id && interfaceController.sidebarHoveredWidgetId() != widget.id
                    && interfaceController.chatboxHoveredWidgetId() != widget.id)
                return;
            int previousClipLeft = Rasterizer.topX;
            int previousClipTop = Rasterizer.topY;
            int previousClipRight = Rasterizer.bottomX;
            int previousClipBottom = Rasterizer.bottomY;
            Rasterizer.setCoordinates(x, y, x + widget.width, y + widget.height);
            int childCount = widget.children.length;
            for (int childIndex = 0; childIndex < childCount; childIndex++) {
                int childX = widget.childX[childIndex] + x;
                int childY = (widget.childY[childIndex] + y) - scrollY;
                Widget childWidget = Widget.get(widget.children[childIndex]);
                childX += childWidget.xOffset;
                childY += childWidget.yOffset;
                if (childWidget.contentType > WidgetContentType.NONE)
                    contentUpdater.accept(childWidget);
                if (childWidget.type == Widget.TYPE_CONTAINER) {
                    if (childWidget.scrollY > childWidget.scrollHeight - childWidget.height)
                        childWidget.scrollY = childWidget.scrollHeight - childWidget.height;
                    if (childWidget.scrollY < 0)
                        childWidget.scrollY = 0;
                    drawInterfaceRecursive(context, childY, childX, childWidget, childWidget.scrollY);
                    if (childWidget.scrollHeight > childWidget.height)
                        drawScrollbar(context, childWidget.scrollY, childX + childWidget.width, childWidget.height,
                                childWidget.scrollHeight, childY);
                } else if (childWidget.type != Widget.TYPE_UNKNOWN)
                    if (childWidget.type == Widget.TYPE_INVENTORY) {
                        int slot = 0;
                        for (int row = 0; row < childWidget.height; row++) {
                            for (int column = 0; column < childWidget.width; column++) {
                                int slotX = childX + column * (Widget.INVENTORY_SLOT_SIZE + childWidget.inventorySpritePaddingX);
                                int slotY = childY + row * (Widget.INVENTORY_SLOT_SIZE + childWidget.inventorySpritePaddingY);
                                if (slot < Widget.INVENTORY_SPRITE_OFFSET_COUNT) {
                                    slotX += childWidget.spriteXOffsets[slot];
                                    slotY += childWidget.spriteYOffsets[slot];
                                }
                                if (childWidget.itemIds[slot] > 0) {
                                    int dragOffsetX = 0;
                                    int dragOffsetY = 0;
                                    int itemId = childWidget.itemIds[slot] - 1;
                                    if (slotX > Rasterizer.topX - 32 && slotX < Rasterizer.bottomX
                                            && slotY > Rasterizer.topY - 32 && slotY < Rasterizer.bottomY
                                            || interfaceController.state().inventoryDragArea != 0
                                                    && interfaceController.state().draggedInventorySlot == slot) {
                                        // Selected inventory items request the white outline variant from
                                        // ItemSpriteFactory.
                                        int spriteOutlineColor = 0;
                                        if (interfaceController.state().itemSelected == 1 && interfaceController.state().selectedItemSlot == slot
                                                && interfaceController.state().selectedItemWidgetId == childWidget.id)
                                            spriteOutlineColor = 0xffffff;
                                        ImageRGB itemSprite = ItemSpriteFactory.getSprite(itemId,
                                                childWidget.itemAmounts[slot], spriteOutlineColor);
                                        if (itemSprite != null) {
                                            if (interfaceController.state().inventoryDragArea != 0
                                                    && interfaceController.state().draggedInventorySlot == slot
                                                    && interfaceController.state().draggedInventoryWidgetId == childWidget.id) {
                                                dragOffsetX = context.mouseX() - interfaceController.state().inventoryDragStartX;
                                                dragOffsetY = context.mouseY() - interfaceController.state().inventoryDragStartY;
                                                if (dragOffsetX < 5 && dragOffsetX > -5)
                                                    dragOffsetX = 0;
                                                if (dragOffsetY < 5 && dragOffsetY > -5)
                                                    dragOffsetY = 0;
                                                if (interfaceController.state().inventoryDragDuration < 5) {
                                                    dragOffsetX = 0;
                                                    dragOffsetY = 0;
                                                }
                                                itemSprite.drawImageAlpha(slotX + dragOffsetX, slotY + dragOffsetY, 128);
                                                if (slotY + dragOffsetY < Rasterizer.topY && widget.scrollY > 0) {
                                                    int scrollAmount = (context.animationCycleDelta()
                                                            * (Rasterizer.topY - slotY - dragOffsetY)) / 3;
                                                    if (scrollAmount > context.animationCycleDelta() * 10)
                                                        scrollAmount = context.animationCycleDelta() * 10;
                                                    if (scrollAmount > widget.scrollY)
                                                        scrollAmount = widget.scrollY;
                                                    widget.scrollY -= scrollAmount;
                                                    interfaceController.state().inventoryDragStartY += scrollAmount;
                                                }
                                                if (slotY + dragOffsetY + 32 > Rasterizer.bottomY
                                                        && widget.scrollY < widget.scrollHeight - widget.height) {
                                                    int scrollAmount2 = (context.animationCycleDelta()
                                                            * ((slotY + dragOffsetY + 32) - Rasterizer.bottomY)) / 3;
                                                    if (scrollAmount2 > context.animationCycleDelta() * 10)
                                                        scrollAmount2 = context.animationCycleDelta() * 10;
                                                    if (scrollAmount2 > widget.scrollHeight - widget.height
                                                            - widget.scrollY)
                                                        scrollAmount2 = widget.scrollHeight - widget.height
                                                                - widget.scrollY;
                                                    widget.scrollY += scrollAmount2;
                                                    interfaceController.state().inventoryDragStartY -= scrollAmount2;
                                                }
                                            } else if (interfaceController.state().pressedInventoryArea != 0
                                                    && interfaceController.state().pressedInventorySlot == slot
                                                    && interfaceController.state().pressedInventoryWidgetId == childWidget.id)
                                                itemSprite.drawImageAlpha(slotX, slotY, 128);
                                            else
                                                itemSprite.drawImage(slotX, slotY);
                                            if (itemSprite.maxWidth == 33 || childWidget.itemAmounts[slot] != 1) {
                                                int itemAmount = childWidget.itemAmounts[slot];
                                                context.smallFont().drawText(formatItemStackAmount(itemAmount),
                                                        slotX + 1 + dragOffsetX, slotY + 10 + dragOffsetY, 0);
                                                context.smallFont().drawText(formatItemStackAmount(itemAmount), slotX + dragOffsetX,
                                                        slotY + 9 + dragOffsetY, 0xffff00);
                                            }
                                        }
                                    }
                                } else if (childWidget.inventorySprites != null && slot < Widget.INVENTORY_SPRITE_OFFSET_COUNT) {
                                    ImageRGB inventorySprite = childWidget.inventorySprites[slot];
                                    if (inventorySprite != null)
                                        inventorySprite.drawImage(slotX, slotY);
                                }
                                slot++;
                            }

                        }

                    } else if (childWidget.type == Widget.TYPE_RECTANGLE) {
                        boolean hovered = false;
                        if (interfaceController.chatboxHoveredWidgetId() == childWidget.id || interfaceController.sidebarHoveredWidgetId() == childWidget.id
                                || interfaceController.viewportHoveredWidgetId() == childWidget.id)
                            hovered = true;
                        int color;
                        if (widgetRuntime.isActive(childWidget)) {
                            color = childWidget.activeColor;
                            if (hovered && childWidget.activeMouseoverColor != 0)
                                color = childWidget.activeMouseoverColor;
                        } else {
                            color = childWidget.color;
                            if (hovered && childWidget.mouseoverColor != 0)
                                color = childWidget.mouseoverColor;
                        }
                        if (childWidget.transparency == 0) {
                            if (childWidget.filled)
                                Rasterizer.drawFilledRectangle(childX, childY, childWidget.width, childWidget.height,
                                        color);
                            else
                                Rasterizer.drawUnfilledRectangle(childX, childY, childWidget.width, childWidget.height,
                                        color);
                        } else if (childWidget.filled)
                            Rasterizer.drawFilledRectangleAlpha(childX, childY, childWidget.width, childWidget.height,
                                    color, 256 - (childWidget.transparency & 0xff));
                        else
                            Rasterizer.drawUnfilledRectangleAlpha(childX, childY, childWidget.width, childWidget.height,
                                    color, 256 - (childWidget.transparency & 0xff));
                    } else if (childWidget.type == Widget.TYPE_TEXT) {
                        TypeFace font = childWidget.font;
                        String widgetText = childWidget.text;
                        boolean hovered2 = false;
                        if (interfaceController.chatboxHoveredWidgetId() == childWidget.id || interfaceController.sidebarHoveredWidgetId() == childWidget.id
                                || interfaceController.viewportHoveredWidgetId() == childWidget.id)
                            hovered2 = true;
                        int textColor;
                        if (widgetRuntime.isActive(childWidget)) {
                            textColor = childWidget.activeColor;
                            if (hovered2 && childWidget.activeMouseoverColor != 0)
                                textColor = childWidget.activeMouseoverColor;
                            if (childWidget.activeText.length() > 0)
                                widgetText = childWidget.activeText;
                        } else {
                            textColor = childWidget.color;
                            if (hovered2 && childWidget.mouseoverColor != 0)
                                textColor = childWidget.mouseoverColor;
                        }
                        if (childWidget.buttonType == Widget.BUTTON_CONTINUE && interfaceController.actionPending()) {
                            widgetText = "Please wait...";
                            textColor = childWidget.color;
                        }
                        if (Rasterizer.width == ClientLayout.CHATBOX_WIDTH) {
                            if (textColor == 0xffff00)
                                textColor = 255;
                            if (textColor == 49152)
                                textColor = 0xffffff;
                        }
                        for (int lineY = childY + font.lineHeight; widgetText.length() > 0; lineY += font.lineHeight) {
                            if (widgetText.indexOf("%") != -1) {
                                do {
                                    int placeholderIndex = widgetText.indexOf("%1");
                                    if (placeholderIndex == -1)
                                        break;
                                    widgetText = widgetText.substring(0, placeholderIndex)
                                            + formatWidgetScriptValue(widgetRuntime.evaluateScript(childWidget, 0))
                                            + widgetText.substring(placeholderIndex + 2);
                                } while (true);
                                do {
                                    int placeholderIndex2 = widgetText.indexOf("%2");
                                    if (placeholderIndex2 == -1)
                                        break;
                                    widgetText = widgetText.substring(0, placeholderIndex2)
                                            + formatWidgetScriptValue(widgetRuntime.evaluateScript(childWidget, 1))
                                            + widgetText.substring(placeholderIndex2 + 2);
                                } while (true);
                                do {
                                    int placeholderIndex3 = widgetText.indexOf("%3");
                                    if (placeholderIndex3 == -1)
                                        break;
                                    widgetText = widgetText.substring(0, placeholderIndex3)
                                            + formatWidgetScriptValue(widgetRuntime.evaluateScript(childWidget, 2))
                                            + widgetText.substring(placeholderIndex3 + 2);
                                } while (true);
                                do {
                                    int placeholderIndex4 = widgetText.indexOf("%4");
                                    if (placeholderIndex4 == -1)
                                        break;
                                    widgetText = widgetText.substring(0, placeholderIndex4)
                                            + formatWidgetScriptValue(widgetRuntime.evaluateScript(childWidget, 3))
                                            + widgetText.substring(placeholderIndex4 + 2);
                                } while (true);
                                do {
                                    int placeholderIndex5 = widgetText.indexOf("%5");
                                    if (placeholderIndex5 == -1)
                                        break;
                                    widgetText = widgetText.substring(0, placeholderIndex5)
                                            + formatWidgetScriptValue(widgetRuntime.evaluateScript(childWidget, 4))
                                            + widgetText.substring(placeholderIndex5 + 2);
                                } while (true);
                            }
                            int lineBreakIndex = widgetText.indexOf("\\n");
                            String lineText;
                            if (lineBreakIndex != -1) {
                                lineText = widgetText.substring(0, lineBreakIndex);
                                widgetText = widgetText.substring(lineBreakIndex + 2);
                            } else {
                                lineText = widgetText;
                                widgetText = "";
                            }
                            if (childWidget.textCentered)
                                font.drawCenteredTextWithTags(lineText, childX + childWidget.width / 2, lineY, textColor,
                                        childWidget.textShadowed);
                            else
                                font.drawTextWithTags(lineText, childX, lineY, textColor, childWidget.textShadowed);
                        }

                    } else if (childWidget.type == Widget.TYPE_SPRITE) {
                        ImageRGB sprite;
                        if (widgetRuntime.isActive(childWidget))
                            sprite = childWidget.activeSprite;
                        else
                            sprite = childWidget.sprite;
                        if (sprite != null)
                            sprite.drawImage(childX, childY);
                    } else if (childWidget.type == Widget.TYPE_MODEL) {
                        int previousCenterX = Rasterizer3D.centerX;
                        int previousCenterY = Rasterizer3D.centerY;
                        Rasterizer3D.centerX = childX + childWidget.width / 2;
                        Rasterizer3D.centerY = childY + childWidget.height / 2;
                        int pitchSineOffset = Rasterizer3D.SINE[childWidget.modelPitch] * childWidget.modelZoom >> 16;
                        int pitchCosineOffset = Rasterizer3D.COSINE[childWidget.modelPitch] * childWidget.modelZoom >> 16;
                        boolean active = widgetRuntime.isActive(childWidget);
                        int animationId;
                        if (active)
                            animationId = childWidget.activeAnimationId;
                        else
                            animationId = childWidget.animationId;
                        Model model;
                        if (animationId == -1) {
                            model = childWidget.getAnimatedModel(-1, -1, active);
                        } else {
                            AnimationSequence sequence = AnimationSequence.sequences[animationId];
                            model = childWidget.getAnimatedModel(sequence.primaryFrameIds[childWidget.animationFrame],
                                    sequence.secondaryFrameIds[childWidget.animationFrame], active);
                        }
                        if (model != null)
                            model.renderSimple(0, childWidget.modelYaw, 0, childWidget.modelPitch, 0, pitchSineOffset,
                                    pitchCosineOffset);
                        Rasterizer3D.centerX = previousCenterX;
                        Rasterizer3D.centerY = previousCenterY;
                    } else {
                        if (childWidget.type == Widget.TYPE_INVENTORY_TEXT) {
                            TypeFace font2 = childWidget.font;
                            int slot2 = 0;
                            for (int row2 = 0; row2 < childWidget.height; row2++) {
                                for (int column2 = 0; column2 < childWidget.width; column2++) {
                                    if (childWidget.itemIds[slot2] > 0) {
                                        ItemDefinition itemDefinition = ItemDefinition
                                                .lookup(childWidget.itemIds[slot2] - 1);
                                        String itemText = String.valueOf(itemDefinition.name);
                                        if (itemDefinition.stackable || childWidget.itemAmounts[slot2] != 1)
                                            itemText = itemText + " x"
                                                    + formatAmountWithCommas(childWidget.itemAmounts[slot2]);
                                        int itemX = childX + column2 * (115 + childWidget.inventorySpritePaddingX);
                                        int itemY = childY + row2 * (12 + childWidget.inventorySpritePaddingY);
                                        if (childWidget.textCentered)
                                            font2.drawCenteredTextWithTags(itemText, itemX + childWidget.width / 2, itemY,
                                                    childWidget.color, childWidget.textShadowed);
                                        else
                                            font2.drawTextWithTags(itemText, itemX, itemY, childWidget.color,
                                                    childWidget.textShadowed);
                                    }
                                    slot2++;
                                }

                            }

                        }
                        if (childWidget.type == Widget.TYPE_TOOLTIP && (interfaceController.chatboxTooltipWidgetId() == childWidget.id
                                || interfaceController.sidebarTooltipWidgetId() == childWidget.id || interfaceController.viewportTooltipWidgetId() == childWidget.id)
                                && interfaceController.tooltipHoverTicks() == 100) {
                            int tooltipWidth = 0;
                            int tooltipHeight = 0;
                            TypeFace font3 = context.plainFont();
                            for (String remainingText = childWidget.text; remainingText.length() > 0;) {
                                int lineBreakIndex2 = remainingText.indexOf("\\n");
                                String lineText2;
                                if (lineBreakIndex2 != -1) {
                                    lineText2 = remainingText.substring(0, lineBreakIndex2);
                                    remainingText = remainingText.substring(lineBreakIndex2 + 2);
                                } else {
                                    lineText2 = remainingText;
                                    remainingText = "";
                                }
                                int lineWidth = font3.getFormattedTextWidth(lineText2);
                                if (lineWidth > tooltipWidth)
                                    tooltipWidth = lineWidth;
                                tooltipHeight += font3.lineHeight + 1;
                            }

                            tooltipWidth += 6;
                            tooltipHeight += 7;
                            int tooltipX = (childX + childWidget.width) - 5 - tooltipWidth;
                            int tooltipY = childY + childWidget.height + 5;
                            if (tooltipX < childX + 5)
                                tooltipX = childX + 5;
                            if (tooltipX + tooltipWidth > x + widget.width)
                                tooltipX = (x + widget.width) - tooltipWidth;
                            if (tooltipY + tooltipHeight > y + widget.height)
                                tooltipY = (y + widget.height) - tooltipHeight;
                            Rasterizer.drawFilledRectangle(tooltipX, tooltipY, tooltipWidth, tooltipHeight, 0xffffa0);
                            Rasterizer.drawUnfilledRectangle(tooltipX, tooltipY, tooltipWidth, tooltipHeight, 0);
                            String remainingText2 = childWidget.text;
                            for (int textY = tooltipY + font3.lineHeight + 2; remainingText2
                                    .length() > 0; textY += font3.lineHeight + 1) {
                                int lineBreakIndex3 = remainingText2.indexOf("\\n");
                                String lineText3;
                                if (lineBreakIndex3 != -1) {
                                    lineText3 = remainingText2.substring(0, lineBreakIndex3);
                                    remainingText2 = remainingText2.substring(lineBreakIndex3 + 2);
                                } else {
                                    lineText3 = remainingText2;
                                    remainingText2 = "";
                                }
                                font3.drawTextWithTags(lineText3, tooltipX + 3, textY, 0, false);
                            }

                        }
                    }
            }

            Rasterizer.setCoordinates(previousClipLeft, previousClipTop, previousClipRight, previousClipBottom);
        }

    /**
     * Formats an item stack using the original K/M abbreviations.
     *
     * @param amount item amount
     * @return formatted amount
     */
    public static String formatItemStackAmount(int amount) {
        if (amount < 0x186a0)
            return String.valueOf(amount);
        if (amount < 0x989680)
            return amount / 1000 + "K";
        return amount / 0xf4240 + "M";
    }

    /**
     * Formats an inventory-text amount using the original comma/K/million style.
     *
     * @param amount item amount
     * @return formatted amount
     */
    public static String formatAmountWithCommas(int amount) {
        String amountText = String.valueOf(amount);
        for (int separatorIndex = amountText.length() - 3; separatorIndex > 0; separatorIndex -= 3)
            amountText = amountText.substring(0, separatorIndex) + "," + amountText.substring(separatorIndex);
        if (amountText.length() > 8)
            amountText = "@gre@" + amountText.substring(0, amountText.length() - 8) + " million @whi@(" + amountText + ")";
        else if (amountText.length() > 4)
            amountText = "@cya@" + amountText.substring(0, amountText.length() - 4) + "K @whi@(" + amountText + ")";
        return " " + amountText;
    }

    /**
     * Formats a CS1 value using the original overflow placeholder.
     *
     * @param value script value
     * @return formatted value
     */
    public static String formatWidgetScriptValue(int value) {
        return value < 0x3b9ac9ff ? String.valueOf(value) : "*";
    }
}
