package rs2.ui.menu;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import rs2.cache.def.GameObjectDefinition;
import rs2.cache.def.ItemDefinition;
import rs2.cache.def.NpcDefinition;
import rs2.chat.ChatController;
import rs2.chat.ChatMessageType;
import rs2.chat.ChatMode;
import rs2.chat.SocialManager;
import rs2.collection.NodeDeque;
import rs2.game.ActorSynchronizer;
import rs2.game.WorldState;
import rs2.game.entity.Actor;
import rs2.game.entity.Npc;
import rs2.game.entity.Player;
import rs2.media.TypeFace;
import rs2.media.model.Model;
import rs2.scene.SceneUid;
import rs2.scene.entity.GroundItem;
import rs2.ui.ClientLayout;
import rs2.ui.InterfaceController;
import rs2.ui.Widget;
import rs2.ui.WidgetContentType;

/**
 * Owns revision-377 context-menu state and cache/widget/entity menu
 * construction.
 *
 * <p>
 * Packet emission and gameplay action dispatch deliberately remain outside this
 * class. The controller builds the menu model from current client state; a
 * later protocol phase can then consume the selected action without coupling
 * menu construction to network encoding.
 * </p>
 */
public final class MenuController {

	/** Mutable menu entries and open-menu geometry. */
	private final MenuState state = new MenuState();
	/** Current fixed/resizable client geometry. */
	private final ClientLayout layout;
	/** Interface lifecycle and interaction-state owner. */
	private final InterfaceController interfaces;
	/** Friend and ignore-list state used when constructing social actions. */
	private final SocialManager socialManager;
	/** Chat state used when constructing chat-related actions. */
	private final ChatController chatController;
	/** Supplies the current duration of a held mouse button. */
	private final IntSupplier mouseButtonHoldTicks;
	/** Redraw callbacks used when menu interaction invalidates a UI region. */
	private final InterfaceController.RedrawSink redrawSink;

	/**
	 * Creates a menu controller.
	 *
	 * @param layout               current client layout
	 * @param interfaces           interface interaction owner
	 * @param socialManager        friend/ignore state owner
	 * @param chatController       chat state owner
	 * @param mouseButtonHoldTicks supplies the current mouse hold duration
	 * @param redrawSink           redraw callbacks used by menu and scrollbar
	 *                             interaction
	 */
	public MenuController(ClientLayout layout, InterfaceController interfaces, SocialManager socialManager,
			ChatController chatController, IntSupplier mouseButtonHoldTicks,
			InterfaceController.RedrawSink redrawSink) {
		this.layout = layout;
		this.interfaces = interfaces;
		this.socialManager = socialManager;
		this.chatController = chatController;
		this.mouseButtonHoldTicks = mouseButtonHoldTicks;
		this.redrawSink = redrawSink;
	}

	/**
	 * Returns the mutable revision-377 menu model.
	 *
	 * @return current menu state
	 */
	public MenuState state() {
		return state;
	}

	/**
	 * Builds friend/ignore context-menu entries for a social-list widget.
	 *
	 * @param widget the widget being processed
	 * @return true when the requested condition/action succeeds; otherwise false
	 */
	public boolean buildSocialWidgetMenu(Widget widget) {
		int contentType = widget.contentType;
		if (contentType >= WidgetContentType.FRIEND_NAME_FIRST && contentType <= WidgetContentType.FRIEND_WORLD_LAST
				|| contentType >= WidgetContentType.FRIEND_NAME_ALTERNATE_FIRST
						&& contentType <= WidgetContentType.FRIEND_WORLD_ALTERNATE_LAST) {
			if (contentType >= WidgetContentType.FRIEND_WORLD_ALTERNATE_FIRST)
				contentType -= WidgetContentType.FRIEND_WORLD_ALTERNATE_INDEX_OFFSET;
			else if (contentType >= WidgetContentType.FRIEND_NAME_ALTERNATE_FIRST)
				contentType -= WidgetContentType.FRIEND_NAME_ALTERNATE_INDEX_OFFSET;
			else if (contentType >= WidgetContentType.FRIEND_WORLD_FIRST)
				contentType -= WidgetContentType.FRIEND_WORLD_FIRST;
			else
				contentType--;
			state.add(new MenuEntry("Remove @whi@" + socialManager.friendNames[contentType], MenuState.REMOVE_FRIEND, 0,
					0, 0));
			state.add(new MenuEntry("Message @whi@" + socialManager.friendNames[contentType], MenuState.MESSAGE_FRIEND,
					0, 0, 0));
			return true;
		}
		if (contentType >= WidgetContentType.IGNORE_NAME_FIRST && contentType <= WidgetContentType.IGNORE_NAME_LAST) {
			state.add(new MenuEntry("Remove @whi@" + widget.text, MenuState.REMOVE_IGNORE, 0, 0, 0));
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Adds context-menu actions for a player at the supplied scene tile.
	 *
	 * @param playerIndex             the player index
	 * @param tileY                   the local scene-tile Y coordinate
	 * @param tileX                   the local scene-tile X coordinate
	 * @param player                  the target player
	 * @param localPlayer             the local player used for combat/team
	 *                                comparisons
	 * @param playerActions           configured player action labels
	 * @param playerActionLowPriority whether each configured action is low priority
	 */
	public void buildPlayerMenu(int playerIndex, int tileY, int tileX, Player player, Player localPlayer,
			String[] playerActions, boolean[] playerActionLowPriority) {
		if (player == localPlayer)
			return;
		if (state.count >= MenuState.BUILD_LIMIT)
			return;
		String displayName;
		if (player.skillLevel == 0)
			displayName = player.name + getCombatLevelColorTag(player.combatLevel, localPlayer.combatLevel) + " (level-"
					+ player.combatLevel + ")";
		else
			displayName = player.name + " (skill-" + player.skillLevel + ")";
		if (interfaces.state().itemSelected == 1) {
			state.add(new MenuEntry("Use " + interfaces.state().selectedItemName + " with @whi@" + displayName,
					MenuState.USE_ITEM_ON_PLAYER, playerIndex, tileX, tileY));
		} else if (interfaces.state().spellSelected == 1) {
			if ((interfaces.state().selectedSpellTargetMask & 8) == 8) {
				state.add(new MenuEntry(interfaces.state().selectedSpellAction + " @whi@" + displayName,
						MenuState.CAST_SPELL_ON_PLAYER, playerIndex, tileX, tileY));
			}
		} else {
			for (int actionIndex = 4; actionIndex >= 0; actionIndex--)
				if (playerActions[actionIndex] != null) {
					int priorityOffset = 0;
					if (playerActions[actionIndex].equalsIgnoreCase("attack")) {
						if (player.combatLevel > localPlayer.combatLevel)
							priorityOffset = MenuState.LOW_PRIORITY_OFFSET;
						if (localPlayer.team != 0 && player.team != 0)
							if (localPlayer.team == player.team)
								priorityOffset = MenuState.LOW_PRIORITY_OFFSET;
							else
								priorityOffset = 0;
					} else if (playerActionLowPriority[actionIndex])
						priorityOffset = MenuState.LOW_PRIORITY_OFFSET;
					int actionId = MenuState.playerOptionAction(actionIndex) + priorityOffset;
					state.add(new MenuEntry(playerActions[actionIndex] + " @whi@" + displayName, actionId, playerIndex,
							tileX, tileY));
				}

		}
		for (int menuIndex = 0; menuIndex < state.count; menuIndex++)
			if (state.entry(menuIndex).action() == MenuState.WALK_HERE) {
				state.replace(menuIndex, state.entry(menuIndex).withText("Walk here @whi@" + displayName));
				return;
			}

	}

	/**
	 * Recursively builds context-menu entries for widgets and inventory slots under
	 * the mouse.
	 *
	 * @param y          the Y coordinate
	 * @param widget     the widget being processed
	 * @param screenArea the fixed client screen area identifier
	 * @param scrollY    the current scroll offset
	 * @param x          the X coordinate
	 * @param mouseX     the mouse X coordinate
	 * @param mouseY     the mouse Y coordinate
	 */
	public void buildInterfaceMenu(int y, Widget widget, int screenArea, int scrollY, int x, int mouseX, int mouseY) {
		if (widget.type != Widget.TYPE_CONTAINER || widget.children == null || widget.mouseoverTriggered)
			return;
		if (mouseX < x || mouseY < y || mouseX > x + widget.width || mouseY > y + widget.height)
			return;
		int childCount = widget.children.length;
		for (int childIndex = 0; childIndex < childCount; childIndex++) {
			int childX = widget.childX[childIndex] + x;
			int childY = (widget.childY[childIndex] + y) - scrollY;
			Widget childWidget = Widget.get(widget.children[childIndex]);
			childX += childWidget.xOffset;
			childY += childWidget.yOffset;
			if ((childWidget.mouseoverTargetId >= 0 || childWidget.mouseoverColor != 0) && mouseX >= childX
					&& mouseY >= childY && mouseX < childX + childWidget.width && mouseY < childY + childWidget.height)
				if (childWidget.mouseoverTargetId >= 0)
					interfaces.setCurrentHoveredWidgetId(childWidget.mouseoverTargetId);
				else
					interfaces.setCurrentHoveredWidgetId(childWidget.id);
			if (childWidget.type == Widget.TYPE_TOOLTIP && mouseX >= childX && mouseY >= childY
					&& mouseX < childX + childWidget.width && mouseY < childY + childWidget.height)
				interfaces.setCurrentTooltipWidgetId(childWidget.id);
			if (childWidget.type == Widget.TYPE_CONTAINER) {
				buildInterfaceMenu(childY, childWidget, screenArea, childWidget.scrollY, childX, mouseX, mouseY);
				if (childWidget.scrollHeight > childWidget.height)
					interfaces.handleScrollbarInput(childWidget.scrollHeight, childY, childWidget, mouseY, screenArea,
							mouseX, childWidget.height, childX + childWidget.width, mouseButtonHoldTicks.getAsInt(),
							redrawSink);
			} else {
				if (childWidget.buttonType == Widget.BUTTON_ACTION && mouseX >= childX && mouseY >= childY
						&& mouseX < childX + childWidget.width && mouseY < childY + childWidget.height) {
					boolean contentHandled = false;
					if (childWidget.contentType != WidgetContentType.NONE)
						contentHandled = buildSocialWidgetMenu(childWidget);
					if (!contentHandled) {
						state.add(new MenuEntry(childWidget.tooltip, MenuState.WIDGET_BUTTON, 0, 0, childWidget.id));
					}
				}
				if (childWidget.buttonType == Widget.BUTTON_SPELL && interfaces.state().spellSelected == 0
						&& mouseX >= childX && mouseY >= childY && mouseX < childX + childWidget.width
						&& mouseY < childY + childWidget.height) {
					String spellAction = childWidget.selectedActionName;
					if (spellAction.indexOf(" ") != -1)
						spellAction = spellAction.substring(0, spellAction.indexOf(" "));
					state.add(new MenuEntry(spellAction + " @gre@" + childWidget.spellName, MenuState.SELECT_SPELL, 0,
							0, childWidget.id));
				}
				if (childWidget.buttonType == Widget.BUTTON_CLOSE && mouseX >= childX && mouseY >= childY
						&& mouseX < childX + childWidget.width && mouseY < childY + childWidget.height) {
					int closeAction = screenArea == 3 ? MenuState.CLOSE_DIALOGUE : MenuState.CLOSE_INTERFACE;
					state.add(new MenuEntry("Close", closeAction, 0, 0, childWidget.id));
				}
				if (childWidget.buttonType == Widget.BUTTON_TOGGLE_VARP && mouseX >= childX && mouseY >= childY
						&& mouseX < childX + childWidget.width && mouseY < childY + childWidget.height) {
					state.add(new MenuEntry(childWidget.tooltip, MenuState.WIDGET_TOGGLE_VARP, 0, 0, childWidget.id));
				}
				if (childWidget.buttonType == Widget.BUTTON_SET_VARP && mouseX >= childX && mouseY >= childY
						&& mouseX < childX + childWidget.width && mouseY < childY + childWidget.height) {
					state.add(new MenuEntry(childWidget.tooltip, MenuState.WIDGET_SET_VARP, 0, 0, childWidget.id));
				}
				if (childWidget.buttonType == Widget.BUTTON_CONTINUE && !interfaces.actionPending() && mouseX >= childX
						&& mouseY >= childY && mouseX < childX + childWidget.width
						&& mouseY < childY + childWidget.height) {
					state.add(new MenuEntry(childWidget.tooltip, MenuState.WIDGET_CONTINUE, 0, 0, childWidget.id));
				}
				if (childWidget.type == Widget.TYPE_INVENTORY) {
					int slot = 0;
					for (int row = 0; row < childWidget.height; row++) {
						for (int column = 0; column < childWidget.width; column++) {
							int slotX = childX
									+ column * (Widget.INVENTORY_SLOT_SIZE + childWidget.inventorySpritePaddingX);
							int slotY = childY
									+ row * (Widget.INVENTORY_SLOT_SIZE + childWidget.inventorySpritePaddingY);
							if (slot < Widget.INVENTORY_SPRITE_OFFSET_COUNT) {
								slotX += childWidget.spriteXOffsets[slot];
								slotY += childWidget.spriteYOffsets[slot];
							}
							if (mouseX >= slotX && mouseY >= slotY && mouseX < slotX + Widget.INVENTORY_SLOT_SIZE
									&& mouseY < slotY + Widget.INVENTORY_SLOT_SIZE) {
								interfaces.state().hoveredInventorySlot = slot;
								interfaces.state().hoveredInventoryWidgetId = childWidget.id;
								if (childWidget.itemIds[slot] > 0) {
									ItemDefinition itemDefinition = ItemDefinition
											.lookup(childWidget.itemIds[slot] - 1);
									if (interfaces.state().itemSelected == 1 && childWidget.inventoryHasOptions) {
										if (childWidget.id != interfaces.state().selectedItemWidgetId
												|| slot != interfaces.state().selectedItemSlot) {
											state.add(new MenuEntry(
													"Use " + interfaces.state().selectedItemName + " with @lre@"
															+ itemDefinition.name,
													MenuState.USE_ITEM_ON_INVENTORY_ITEM, itemDefinition.id, slot,
													childWidget.id));
										}
									} else if (interfaces.state().spellSelected == 1
											&& childWidget.inventoryHasOptions) {
										if ((interfaces.state().selectedSpellTargetMask & 0x10) == 16) {
											state.add(new MenuEntry(
													interfaces.state().selectedSpellAction + " @lre@"
															+ itemDefinition.name,
													MenuState.CAST_SPELL_ON_INVENTORY_ITEM, itemDefinition.id, slot,
													childWidget.id));
										}
									} else {
										if (childWidget.inventoryHasOptions) {
											for (int inventoryActionIndex = 4; inventoryActionIndex >= 3; inventoryActionIndex--)
												if (itemDefinition.inventoryActions != null
														&& itemDefinition.inventoryActions[inventoryActionIndex] != null) {
													int actionId = MenuState
															.inventoryItemOptionAction(inventoryActionIndex);
													state.add(new MenuEntry(
															itemDefinition.inventoryActions[inventoryActionIndex]
																	+ " @lre@" + itemDefinition.name,
															actionId, itemDefinition.id, slot, childWidget.id));
												} else if (inventoryActionIndex == 4) {
													state.add(new MenuEntry("Drop @lre@" + itemDefinition.name,
															MenuState.INVENTORY_ITEM_OPTION_5, itemDefinition.id, slot,
															childWidget.id));
												}

										}
										if (childWidget.inventoryUsableItems) {
											state.add(new MenuEntry("Use @lre@" + itemDefinition.name,
													MenuState.SELECT_ITEM, itemDefinition.id, slot, childWidget.id));
										}
										if (childWidget.inventoryHasOptions
												&& itemDefinition.inventoryActions != null) {
											for (int inventoryActionIndex2 = 2; inventoryActionIndex2 >= 0; inventoryActionIndex2--)
												if (itemDefinition.inventoryActions[inventoryActionIndex2] != null) {
													int actionId = MenuState
															.inventoryItemOptionAction(inventoryActionIndex2);
													state.add(new MenuEntry(
															itemDefinition.inventoryActions[inventoryActionIndex2]
																	+ " @lre@" + itemDefinition.name,
															actionId, itemDefinition.id, slot, childWidget.id));
												}

										}
										if (childWidget.actions != null) {
											for (int widgetActionIndex = 4; widgetActionIndex >= 0; widgetActionIndex--)
												if (childWidget.actions[widgetActionIndex] != null) {
													int actionId = MenuState.widgetItemOptionAction(widgetActionIndex);
													state.add(new MenuEntry(
															childWidget.actions[widgetActionIndex] + " @lre@"
																	+ itemDefinition.name,
															actionId, itemDefinition.id, slot, childWidget.id));
												}

										}
										state.add(new MenuEntry("Examine @lre@" + itemDefinition.name,
												MenuState.EXAMINE_INVENTORY_ITEM, itemDefinition.id, slot,
												childWidget.id));
									}
								}
							}
							slot++;
						}

					}

				}
			}
		}

	}

	/**
	 * Adds context-menu actions for an NPC at the supplied scene tile.
	 *
	 * @param definition  the definition
	 * @param tileY       the local scene-tile Y coordinate
	 * @param tileX       the local scene-tile X coordinate
	 * @param npcIndex    the NPC index
	 * @param localPlayer the local player used for combat-level comparison
	 */
	public void buildNpcMenu(NpcDefinition definition, int tileY, int tileX, int npcIndex, Player localPlayer) {
		if (state.count >= MenuState.BUILD_LIMIT)
			return;
		if (definition.morphIds != null)
			definition = definition.transform();
		if (definition == null)
			return;
		if (!definition.clickable)
			return;
		String displayName = definition.name;
		if (definition.combatLevel != 0)
			displayName = displayName + getCombatLevelColorTag(definition.combatLevel, localPlayer.combatLevel)
					+ " (level-" + definition.combatLevel + ")";
		if (interfaces.state().itemSelected == 1) {
			state.add(new MenuEntry("Use " + interfaces.state().selectedItemName + " with @yel@" + displayName,
					MenuState.USE_ITEM_ON_NPC, npcIndex, tileX, tileY));
			return;
		}
		if (interfaces.state().spellSelected == 1) {
			if ((interfaces.state().selectedSpellTargetMask & 2) == 2) {
				state.add(new MenuEntry(interfaces.state().selectedSpellAction + " @yel@" + displayName,
						MenuState.CAST_SPELL_ON_NPC, npcIndex, tileX, tileY));
				return;
			}
		} else {
			if (definition.actions != null) {
				for (int actionIndex = 4; actionIndex >= 0; actionIndex--)
					if (definition.actions[actionIndex] != null
							&& !definition.actions[actionIndex].equalsIgnoreCase("attack")) {
						int actionId = MenuState.npcOptionAction(actionIndex);
						state.add(new MenuEntry(definition.actions[actionIndex] + " @yel@" + displayName, actionId,
								npcIndex, tileX, tileY));
					}

			}
			if (definition.actions != null) {
				for (int actionIndex2 = 4; actionIndex2 >= 0; actionIndex2--)
					if (definition.actions[actionIndex2] != null
							&& definition.actions[actionIndex2].equalsIgnoreCase("attack")) {
						int priorityOffset = 0;
						if (definition.combatLevel > localPlayer.combatLevel)
							priorityOffset = MenuState.LOW_PRIORITY_OFFSET;
						int actionId = MenuState.npcOptionAction(actionIndex2) + priorityOffset;
						state.add(new MenuEntry(definition.actions[actionIndex2] + " @yel@" + displayName, actionId,
								npcIndex, tileX, tileY));
					}

			}
			state.add(new MenuEntry("Examine @yel@" + displayName, MenuState.EXAMINE_NPC, npcIndex, tileX, tileY));
		}
	}

	/**
	 * Processes mouse interaction with either the open context menu or the default
	 * menu action.
	 *
	 * @param clickButton             current click button code
	 * @param clickX                  click X coordinate in client space
	 * @param clickY                  click Y coordinate in client space
	 * @param mouseX                  current mouse X coordinate in client space
	 * @param mouseY                  current mouse Y coordinate in client space
	 * @param oneButtonMouseMode      one-button mouse preference
	 * @param boldFontForOpenMenu     font used to measure an opened context menu
	 * @param dispatchAction          callback that dispatches a selected menu entry
	 * @param resetInventoryDragMoved callback that clears prior inventory-drag
	 *                                movement
	 */
	public void processClick(int clickButton, int clickX, int clickY, int mouseX, int mouseY, int oneButtonMouseMode,
			TypeFace boldFontForOpenMenu, IntConsumer dispatchAction, Runnable resetInventoryDragMoved) {
		if (interfaces.state().inventoryDragArea != 0)
			return;
		if (interfaces.state().spellSelected == 1 && layout.isTopTabsSpellBlockPoint(clickX, clickY))
			clickButton = 0;
		if (state.open) {
			if (clickButton != 1) {
				int menuMouseX = mouseX;
				int menuMouseY = mouseY;
				if (state.screenArea == 0) {
					menuMouseX -= layout.viewportX();
					menuMouseY -= layout.viewportY();
				}
				if (state.screenArea == 1) {
					menuMouseX -= layout.sidebarX();
					menuMouseY -= layout.sidebarY();
				}
				if (state.screenArea == 2) {
					menuMouseX -= layout.chatboxX();
					menuMouseY -= layout.chatboxY();
				}
				if (menuMouseX < state.offsetX - ClientLayout.CONTEXT_MENU_CLOSE_PADDING
						|| menuMouseX > state.offsetX + state.width + ClientLayout.CONTEXT_MENU_CLOSE_PADDING
						|| menuMouseY < state.offsetY - ClientLayout.CONTEXT_MENU_CLOSE_PADDING
						|| menuMouseY > state.offsetY + state.height + ClientLayout.CONTEXT_MENU_CLOSE_PADDING) {
					state.open = false;
					if (state.screenArea == 1)
						redrawSink.redrawSidebar();
					if (state.screenArea == 2)
						redrawSink.redrawChatbox();
				}
			}
			if (clickButton == 1) {
				int menuX = state.offsetX;
				int menuY = state.offsetY;
				int menuWidth = state.width;
				int menuClickX = clickX;
				int menuClickY = clickY;
				if (state.screenArea == 0) {
					menuClickX -= layout.viewportX();
					menuClickY -= layout.viewportY();
				}
				if (state.screenArea == 1) {
					menuClickX -= layout.sidebarX();
					menuClickY -= layout.sidebarY();
				}
				if (state.screenArea == 2) {
					menuClickX -= layout.chatboxX();
					menuClickY -= layout.chatboxY();
				}
				int selectedEntry = -1;
				for (int entryIndex = 0; entryIndex < state.count; entryIndex++) {
					int entryY = layout.contextMenuEntryY(menuY, state.count, entryIndex);
					if (layout.isContextMenuEntryHit(menuClickX, menuClickY, menuX, menuWidth, entryY))
						selectedEntry = entryIndex;
				}

				if (selectedEntry != -1)
					dispatchAction.accept(selectedEntry);
				state.open = false;
				if (state.screenArea == 1)
					redrawSink.redrawSidebar();
				if (state.screenArea == 2) {
					redrawSink.redrawChatbox();
					return;
				}
			}
		} else {
			if (clickButton == 1 && state.count > 0) {
				int actionId = state.entry(state.count - 1).action();
				if (actionId == MenuState.WIDGET_ITEM_OPTION_1 || actionId == MenuState.WIDGET_ITEM_OPTION_2
						|| actionId == MenuState.WIDGET_ITEM_OPTION_3 || actionId == MenuState.WIDGET_ITEM_OPTION_4
						|| actionId == MenuState.WIDGET_ITEM_OPTION_5 || actionId == MenuState.INVENTORY_ITEM_OPTION_1
						|| actionId == MenuState.INVENTORY_ITEM_OPTION_2
						|| actionId == MenuState.INVENTORY_ITEM_OPTION_3
						|| actionId == MenuState.INVENTORY_ITEM_OPTION_4
						|| actionId == MenuState.INVENTORY_ITEM_OPTION_5 || actionId == MenuState.SELECT_ITEM
						|| actionId == MenuState.EXAMINE_INVENTORY_ITEM) {
					int slot = state.entry(state.count - 1).argument1();
					int widgetId = state.entry(state.count - 1).argument2();
					Widget inventoryWidget = Widget.get(widgetId);
					if (inventoryWidget.inventoryAllowSwap || inventoryWidget.inventoryReplaceItems) {
						resetInventoryDragMoved.run();
						interfaces.state().inventoryDragDuration = 0;
						interfaces.state().draggedInventoryWidgetId = widgetId;
						interfaces.state().draggedInventorySlot = slot;
						interfaces.state().inventoryDragArea = 2;
						interfaces.state().inventoryDragStartX = clickX;
						interfaces.state().inventoryDragStartY = clickY;
						if (Widget.get(widgetId).parentId == interfaces.state().openInterfaceId)
							interfaces.state().inventoryDragArea = 1;
						if (Widget.get(widgetId).parentId == interfaces.state().chatboxInterfaceId)
							interfaces.state().inventoryDragArea = 3;
						return;
					}
				}
			}
			if (clickButton == 1 && (oneButtonMouseMode == 1 || state.isAddFriendAction(state.count - 1))
					&& state.count > 2)
				clickButton = 2;
			if (clickButton == 1 && state.count > 0)
				dispatchAction.accept(state.count - 1);
			if (clickButton == 2 && state.count > 0)
				openContextMenu(boldFontForOpenMenu, clickX, clickY);
		}
	}

	/**
	 * Computes context-menu dimensions and opens it in the appropriate client area.
	 *
	 * @param boldFont font used to measure menu labels
	 * @param clickX   click X coordinate in client space
	 * @param clickY   click Y coordinate in client space
	 */
	public void openContextMenu(TypeFace boldFont, int clickX, int clickY) {
		int textWidth = boldFont.getFormattedTextWidth("Choose Option");
		for (int menuIndex = 0; menuIndex < state.count; menuIndex++) {
			int textWidth2 = boldFont.getFormattedTextWidth(state.entry(menuIndex).text());
			if (textWidth2 > textWidth)
				textWidth = textWidth2;
		}

		textWidth += ClientLayout.CONTEXT_MENU_WIDTH_PADDING;
		int menuHeight = layout.contextMenuClampHeight(state.count);
		if (layout.isViewportInteractionPoint(clickX, clickY)) {
			int menuX = clickX - layout.viewportX() - textWidth / 2;
			if (menuX + textWidth > layout.viewportWidth())
				menuX = layout.viewportWidth() - textWidth;
			if (menuX < 0)
				menuX = 0;
			int menuY = clickY - layout.viewportY();
			if (menuY + menuHeight > layout.viewportHeight())
				menuY = layout.viewportHeight() - menuHeight;
			if (menuY < 0)
				menuY = 0;
			state.open = true;
			state.screenArea = 0;
			state.offsetX = menuX;
			state.offsetY = menuY;
			state.width = textWidth;
			state.height = layout.contextMenuHeight(state.count);
		}
		if (layout.isSidebarInteractionPoint(clickX, clickY)) {
			int clickX2 = clickX - layout.sidebarX() - textWidth / 2;
			if (clickX2 < 0)
				clickX2 = 0;
			else if (clickX2 + textWidth > ClientLayout.SIDEBAR_WIDTH)
				clickX2 = ClientLayout.SIDEBAR_WIDTH - textWidth;
			int clickY2 = clickY - layout.sidebarY();
			if (clickY2 < 0)
				clickY2 = 0;
			else if (clickY2 + menuHeight > ClientLayout.SIDEBAR_HEIGHT)
				clickY2 = ClientLayout.SIDEBAR_HEIGHT - menuHeight;
			state.open = true;
			state.screenArea = 1;
			state.offsetX = clickX2;
			state.offsetY = clickY2;
			state.width = textWidth;
			state.height = layout.contextMenuHeight(state.count);
		}
		if (layout.isChatboxInteractionPoint(clickX, clickY)) {
			int clickX3 = clickX - layout.chatboxX() - textWidth / 2;
			if (clickX3 < 0)
				clickX3 = 0;
			else if (clickX3 + textWidth > ClientLayout.CHATBOX_WIDTH)
				clickX3 = ClientLayout.CHATBOX_WIDTH - textWidth;
			int clickY3 = clickY - layout.chatboxY();
			if (clickY3 < 0)
				clickY3 = 0;
			else if (clickY3 + menuHeight > ClientLayout.CHATBOX_HEIGHT)
				clickY3 = ClientLayout.CHATBOX_HEIGHT - menuHeight;
			state.open = true;
			state.screenArea = 2;
			state.offsetX = clickX3;
			state.offsetY = clickY3;
			state.width = textWidth;
			state.height = layout.contextMenuHeight(state.count);
		}
	}

	/**
	 * Builds world-view menu entries from the scene picking results.
	 *
	 * @param worldState              current local world state
	 * @param actorSynchronizer       synchronized player/NPC index state
	 * @param currentPlane            current scene plane
	 * @param localPlayer             local player
	 * @param playerActions           configured player action labels
	 * @param playerActionLowPriority whether each configured player action is low
	 *                                priority
	 * @param mouseX                  current mouse X coordinate in client space
	 * @param mouseY                  current mouse Y coordinate in client space
	 */
	public void buildViewportMenu(WorldState worldState, ActorSynchronizer actorSynchronizer, int currentPlane,
			Player localPlayer, String[] playerActions, boolean[] playerActionLowPriority, int mouseX, int mouseY) {
		if (interfaces.state().itemSelected == 0 && interfaces.state().spellSelected == 0) {
			state.add(new MenuEntry("Walk here", MenuState.WALK_HERE, 0, mouseX, mouseY));
		}
		int previousUid = -1;
		for (int pickedIndex = 0; pickedIndex < Model.pickedCount; pickedIndex++) {
			int packedUid = Model.pickedUids[pickedIndex];
			int tileX = packedUid & SceneUid.TILE_COORDINATE_MASK;
			int tileY = packedUid >> SceneUid.TILE_Y_SHIFT & SceneUid.TILE_COORDINATE_MASK;
			int entityType = packedUid >> SceneUid.ENTITY_TYPE_SHIFT & SceneUid.ENTITY_TYPE_MASK;
			int entityId = packedUid >> SceneUid.ENTITY_ID_SHIFT & SceneUid.ENTITY_ID_MASK;
			if (packedUid == previousUid)
				continue;
			previousUid = packedUid;
			if (entityType == SceneUid.TYPE_OBJECT
					&& worldState.scene.getConfig(currentPlane, tileX, tileY, packedUid) >= 0) {
				GameObjectDefinition objectDefinition = GameObjectDefinition.lookup(entityId);
				if (objectDefinition.morphIds != null)
					objectDefinition = objectDefinition.transform();
				if (objectDefinition == null)
					continue;
				if (interfaces.state().itemSelected == 1) {
					state.add(new MenuEntry(
							"Use " + interfaces.state().selectedItemName + " with @cya@" + objectDefinition.name,
							MenuState.USE_ITEM_ON_OBJECT, packedUid, tileX, tileY));
				} else if (interfaces.state().spellSelected == 1) {
					if ((interfaces.state().selectedSpellTargetMask & 4) == 4) {
						state.add(
								new MenuEntry(interfaces.state().selectedSpellAction + " @cya@" + objectDefinition.name,
										MenuState.CAST_SPELL_ON_OBJECT, packedUid, tileX, tileY));
					}
				} else {
					if (objectDefinition.actions != null) {
						for (int objectActionIndex = 4; objectActionIndex >= 0; objectActionIndex--)
							if (objectDefinition.actions[objectActionIndex] != null) {
								int actionId = MenuState.objectOptionAction(objectActionIndex);
								state.add(new MenuEntry(
										objectDefinition.actions[objectActionIndex] + " @cya@" + objectDefinition.name,
										actionId, packedUid, tileX, tileY));
							}

					}
					state.add(new MenuEntry("Examine @cya@" + objectDefinition.name, MenuState.EXAMINE_OBJECT,
							objectDefinition.id << 14, tileX, tileY));
				}
			}
			if (entityType == 1) {
				Npc npc = actorSynchronizer.npcs[entityId];
				if (npc.definition.size == 1 && (((Actor) (npc)).x & 0x7f) == 64 && (((Actor) (npc)).y & 0x7f) == 64) {
					for (int activeNpcIndex = 0; activeNpcIndex < actorSynchronizer.npcCount; activeNpcIndex++) {
						Npc stackedNpc = actorSynchronizer.npcs[actorSynchronizer.npcIndices[activeNpcIndex]];
						if (stackedNpc != null && stackedNpc != npc && stackedNpc.definition.size == 1
								&& ((Actor) (stackedNpc)).x == ((Actor) (npc)).x
								&& ((Actor) (stackedNpc)).y == ((Actor) (npc)).y)
							buildNpcMenu(stackedNpc.definition, tileY, tileX,
									actorSynchronizer.npcIndices[activeNpcIndex], localPlayer);
					}

					for (int activePlayerIndex = 0; activePlayerIndex < actorSynchronizer.playerCount; activePlayerIndex++) {
						Player stackedPlayer = actorSynchronizer.players[actorSynchronizer.playerIndices[activePlayerIndex]];
						if (stackedPlayer != null && ((Actor) (stackedPlayer)).x == ((Actor) (npc)).x
								&& ((Actor) (stackedPlayer)).y == ((Actor) (npc)).y)
							buildPlayerMenu(actorSynchronizer.playerIndices[activePlayerIndex], tileY, tileX,
									stackedPlayer, localPlayer, playerActions, playerActionLowPriority);
					}

				}
				buildNpcMenu(npc.definition, tileY, tileX, entityId, localPlayer);
			}
			if (entityType == 0) {
				Player player = actorSynchronizer.players[entityId];
				if ((((Actor) (player)).x & 0x7f) == 64 && (((Actor) (player)).y & 0x7f) == 64) {
					for (int activeNpcIndex2 = 0; activeNpcIndex2 < actorSynchronizer.npcCount; activeNpcIndex2++) {
						Npc stackedNpc2 = actorSynchronizer.npcs[actorSynchronizer.npcIndices[activeNpcIndex2]];
						if (stackedNpc2 != null && stackedNpc2.definition.size == 1
								&& ((Actor) (stackedNpc2)).x == ((Actor) (player)).x
								&& ((Actor) (stackedNpc2)).y == ((Actor) (player)).y)
							buildNpcMenu(stackedNpc2.definition, tileY, tileX,
									actorSynchronizer.npcIndices[activeNpcIndex2], localPlayer);
					}

					for (int activePlayerIndex2 = 0; activePlayerIndex2 < actorSynchronizer.playerCount; activePlayerIndex2++) {
						Player stackedPlayer2 = actorSynchronizer.players[actorSynchronizer.playerIndices[activePlayerIndex2]];
						if (stackedPlayer2 != null && stackedPlayer2 != player
								&& ((Actor) (stackedPlayer2)).x == ((Actor) (player)).x
								&& ((Actor) (stackedPlayer2)).y == ((Actor) (player)).y)
							buildPlayerMenu(actorSynchronizer.playerIndices[activePlayerIndex2], tileY, tileX,
									stackedPlayer2, localPlayer, playerActions, playerActionLowPriority);
					}

				}
				buildPlayerMenu(entityId, tileY, tileX, player, localPlayer, playerActions, playerActionLowPriority);
			}
			if (entityType == 3) {
				NodeDeque groundItems = worldState.groundItems[currentPlane][tileX][tileY];
				if (groundItems != null) {
					for (GroundItem groundItem = (GroundItem) groundItems
							.last(); groundItem != null; groundItem = (GroundItem) groundItems.previous()) {
						ItemDefinition itemDefinition = ItemDefinition.lookup(groundItem.id);
						if (interfaces.state().itemSelected == 1) {
							state.add(new MenuEntry(
									"Use " + interfaces.state().selectedItemName + " with @lre@" + itemDefinition.name,
									MenuState.USE_ITEM_ON_GROUND_ITEM, groundItem.id, tileX, tileY));
						} else if (interfaces.state().spellSelected == 1) {
							if ((interfaces.state().selectedSpellTargetMask & 1) == 1) {
								state.add(new MenuEntry(
										interfaces.state().selectedSpellAction + " @lre@" + itemDefinition.name,
										MenuState.CAST_SPELL_ON_GROUND_ITEM, groundItem.id, tileX, tileY));
							}
						} else {
							for (int groundActionIndex = 4; groundActionIndex >= 0; groundActionIndex--)
								if (itemDefinition.groundActions != null
										&& itemDefinition.groundActions[groundActionIndex] != null) {
									int actionId = MenuState.groundItemOptionAction(groundActionIndex);
									state.add(new MenuEntry(itemDefinition.groundActions[groundActionIndex] + " @lre@"
											+ itemDefinition.name, actionId, groundItem.id, tileX, tileY));
								} else if (groundActionIndex == 2) {
									state.add(new MenuEntry("Take @lre@" + itemDefinition.name,
											MenuState.GROUND_ITEM_OPTION_3, groundItem.id, tileX, tileY));
								}

							state.add(new MenuEntry("Examine @lre@" + itemDefinition.name,
									MenuState.EXAMINE_GROUND_ITEM, groundItem.id, tileX, tileY));
						}
					}

				}
			}
		}

	}

	/**
	 * Builds context-menu actions for names visible in the split-private-chat
	 * overlay.
	 *
	 * @param systemUpdateTimer nonzero when the system-update line occupies an
	 *                          overlay row
	 * @param mouseX            current mouse X coordinate in client space
	 * @param mouseY            current mouse Y coordinate in client space
	 * @param plainFont         font used to measure private-message lines
	 * @param playerRights      local player's moderator-rights level
	 * @param localPlayerName   local player's display name
	 */
	public void buildSplitPrivateChatMenu(int systemUpdateTimer, int mouseX, int mouseY, TypeFace plainFont,
			int playerRights, String localPlayerName) {
		if (chatController.splitPrivateChat() == 0)
			return;
		int visibleLine = 0;
		if (systemUpdateTimer != 0)
			visibleLine = 1;
		for (int messageIndex = 0; messageIndex < 100; messageIndex++)
			if (chatController.history().messages[messageIndex] != null) {
				int messageType = chatController.history().types[messageIndex];
				String sender = chatController.history().senders[messageIndex];
				if (sender != null && sender.startsWith("@cr1@")) {
					sender = sender.substring(5);
				}
				if (sender != null && sender.startsWith("@cr2@")) {
					sender = sender.substring(5);
				}
				if ((messageType == ChatMessageType.PRIVATE_RECEIVED
						|| messageType == ChatMessageType.PRIVATE_RECEIVED_PRIVILEGED)
						&& (messageType == ChatMessageType.PRIVATE_RECEIVED_PRIVILEGED
								|| chatController.privateMode() == ChatMode.ON
								|| chatController.privateMode() == ChatMode.FRIENDS
										&& socialManager.isFriendOrSelf(sender, localPlayerName))) {
					int lineY = layout.unobscuredViewportHeight() - 5 - visibleLine * 13;
					if (mouseX > layout.viewportX() && mouseY - layout.viewportY() > lineY - 10
							&& mouseY - layout.viewportY() <= lineY + 3) {
						int messageWidth = plainFont.getFormattedTextWidth(
								"From:  " + sender + chatController.history().messages[messageIndex]) + 25;
						if (messageWidth > 450)
							messageWidth = 450;
						if (mouseX < layout.viewportX() + messageWidth) {
							if (playerRights >= 1) {
								state.add(new MenuEntry("Report abuse @whi@" + sender,
										MenuState.lowPriority(MenuState.REPORT_ABUSE), 0, 0, 0));
							}
							state.add(new MenuEntry("Add ignore @whi@" + sender,
									MenuState.lowPriority(MenuState.ADD_IGNORE), 0, 0, 0));
							state.add(new MenuEntry("Add friend @whi@" + sender,
									MenuState.lowPriority(MenuState.ADD_FRIEND), 0, 0, 0));
						}
					}
					if (++visibleLine >= 5)
						return;
				}
				if ((messageType == ChatMessageType.PRIVATE_STATUS || messageType == ChatMessageType.PRIVATE_SENT)
						&& chatController.privateMode() < ChatMode.OFF && ++visibleLine >= 5)
					return;
			}

	}

	/**
	 * Builds context-menu actions for player names under the chatbox mouse
	 * position.
	 *
	 * @param mouseY          mouse Y coordinate relative to the chatbox
	 * @param playerRights    local player's moderator-rights level
	 * @param localPlayerName local player's display name
	 */
	public void buildChatboxMessageMenu(int mouseY, int playerRights, String localPlayerName) {
		int visibleLine = 0;
		for (int messageIndex = 0; messageIndex < 100; messageIndex++) {
			if (chatController.history().messages[messageIndex] == null)
				continue;
			int messageType = chatController.history().types[messageIndex];
			int lineY = (ClientLayout.CHATBOX_MESSAGE_BASELINE_Y
					- visibleLine * ClientLayout.CHATBOX_MESSAGE_LINE_HEIGHT) + chatController.scrollOffset()
					+ ClientLayout.CHATBOX_MESSAGE_MENU_BASELINE_OFFSET;
			if (lineY < -20)
				break;
			String sender = chatController.history().senders[messageIndex];
			if (sender != null && sender.startsWith("@cr1@")) {
				sender = sender.substring(5);
			}
			if (sender != null && sender.startsWith("@cr2@")) {
				sender = sender.substring(5);
			}
			if (messageType == ChatMessageType.GAME)
				visibleLine++;
			if ((messageType == ChatMessageType.PUBLIC_PRIVILEGED || messageType == ChatMessageType.PUBLIC)
					&& (messageType == ChatMessageType.PUBLIC_PRIVILEGED || chatController.publicMode() == ChatMode.ON
							|| chatController.publicMode() == ChatMode.FRIENDS
									&& socialManager.isFriendOrSelf(sender, localPlayerName))) {
				if (mouseY > lineY - ClientLayout.CHATBOX_MESSAGE_LINE_HEIGHT && mouseY <= lineY
						&& !sender.equals(localPlayerName)) {
					if (playerRights >= 1) {
						state.add(new MenuEntry("Report abuse @whi@" + sender, MenuState.REPORT_ABUSE, 0, 0, 0));
					}
					state.add(new MenuEntry("Add ignore @whi@" + sender, MenuState.ADD_IGNORE, 0, 0, 0));
					state.add(new MenuEntry("Add friend @whi@" + sender, MenuState.ADD_FRIEND, 0, 0, 0));
				}
				visibleLine++;
			}
			if ((messageType == ChatMessageType.PRIVATE_RECEIVED
					|| messageType == ChatMessageType.PRIVATE_RECEIVED_PRIVILEGED)
					&& chatController.splitPrivateChat() == 0
					&& (messageType == ChatMessageType.PRIVATE_RECEIVED_PRIVILEGED
							|| chatController.privateMode() == ChatMode.ON
							|| chatController.privateMode() == ChatMode.FRIENDS
									&& socialManager.isFriendOrSelf(sender, localPlayerName))) {
				if (mouseY > lineY - ClientLayout.CHATBOX_MESSAGE_LINE_HEIGHT && mouseY <= lineY) {
					if (playerRights >= 1) {
						state.add(new MenuEntry("Report abuse @whi@" + sender, MenuState.REPORT_ABUSE, 0, 0, 0));
					}
					state.add(new MenuEntry("Add ignore @whi@" + sender, MenuState.ADD_IGNORE, 0, 0, 0));
					state.add(new MenuEntry("Add friend @whi@" + sender, MenuState.ADD_FRIEND, 0, 0, 0));
				}
				visibleLine++;
			}
			if (messageType == ChatMessageType.TRADE_REQUEST
					&& (chatController.tradeMode() == ChatMode.ON || chatController.tradeMode() == ChatMode.FRIENDS
							&& socialManager.isFriendOrSelf(sender, localPlayerName))) {
				if (mouseY > lineY - ClientLayout.CHATBOX_MESSAGE_LINE_HEIGHT && mouseY <= lineY) {
					state.add(new MenuEntry("Accept trade @whi@" + sender, MenuState.ACCEPT_TRADE, 0, 0, 0));
				}
				visibleLine++;
			}
			if ((messageType == ChatMessageType.PRIVATE_STATUS || messageType == ChatMessageType.PRIVATE_SENT)
					&& chatController.splitPrivateChat() == 0 && chatController.privateMode() < ChatMode.OFF)
				visibleLine++;
			if (messageType == ChatMessageType.CHALLENGE_REQUEST
					&& (chatController.tradeMode() == ChatMode.ON || chatController.tradeMode() == ChatMode.FRIENDS
							&& socialManager.isFriendOrSelf(sender, localPlayerName))) {
				if (mouseY > lineY - ClientLayout.CHATBOX_MESSAGE_LINE_HEIGHT && mouseY <= lineY) {
					state.add(new MenuEntry("Accept challenge @whi@" + sender, MenuState.ACCEPT_CHALLENGE, 0, 0, 0));
				}
				visibleLine++;
			}
		}

	}

	/**
	 * Rebuilds and priority-partitions the context menu for the current mouse
	 * location.
	 *
	 * @param mouseX                  current mouse X coordinate in client space
	 * @param mouseY                  current mouse Y coordinate in client space
	 * @param systemUpdateTimer       nonzero when a system-update message is active
	 * @param plainFont               font used for chat-line measurements
	 * @param playerRights            local player's moderator-rights level
	 * @param localPlayer             local player
	 * @param worldState              current local world state
	 * @param actorSynchronizer       synchronized player/NPC index state
	 * @param currentPlane            current scene plane
	 * @param playerActions           configured player action labels
	 * @param playerActionLowPriority whether each configured player action is low
	 *                                priority
	 */
	public void buildContextMenu(int mouseX, int mouseY, int systemUpdateTimer, TypeFace plainFont, int playerRights,
			Player localPlayer, WorldState worldState, ActorSynchronizer actorSynchronizer, int currentPlane,
			String[] playerActions, boolean[] playerActionLowPriority) {
		if (interfaces.state().inventoryDragArea != 0)
			return;
		state.reset();
		if (interfaces.state().fullscreenInterfaceId != -1) {
			interfaces.setCurrentHoveredWidgetId(0);
			interfaces.setCurrentTooltipWidgetId(0);
			buildInterfaceMenu(0, Widget.get(interfaces.state().fullscreenInterfaceId), 0, 0, 0, mouseX, mouseY);
			if (interfaces.currentHoveredWidgetId() != interfaces.viewportHoveredWidgetId())
				interfaces.setViewportHoveredWidgetId(interfaces.currentHoveredWidgetId());
			if (interfaces.currentTooltipWidgetId() != interfaces.viewportTooltipWidgetId())
				interfaces.setViewportTooltipWidgetId(interfaces.currentTooltipWidgetId());
			return;
		}
		buildSplitPrivateChatMenu(systemUpdateTimer, mouseX, mouseY, plainFont, playerRights, localPlayer.name);
		interfaces.setCurrentHoveredWidgetId(0);
		interfaces.setCurrentTooltipWidgetId(0);
		if (layout.isViewportInteractionPoint(mouseX, mouseY))
			if (interfaces.state().openInterfaceId != -1) {
				Widget openInterface = Widget.get(interfaces.state().openInterfaceId);
				int interfaceX = layout.viewportX() + layout.centeredInterfaceX(openInterface.width);
				int interfaceY = layout.viewportY() + layout.centeredInterfaceY(openInterface.height);
				buildInterfaceMenu(interfaceY, openInterface, 0, 0, interfaceX, mouseX, mouseY);
			} else
				buildViewportMenu(worldState, actorSynchronizer, currentPlane, localPlayer, playerActions,
						playerActionLowPriority, mouseX, mouseY);
		if (interfaces.currentHoveredWidgetId() != interfaces.viewportHoveredWidgetId())
			interfaces.setViewportHoveredWidgetId(interfaces.currentHoveredWidgetId());
		if (interfaces.currentTooltipWidgetId() != interfaces.viewportTooltipWidgetId())
			interfaces.setViewportTooltipWidgetId(interfaces.currentTooltipWidgetId());
		interfaces.setCurrentHoveredWidgetId(0);
		interfaces.setCurrentTooltipWidgetId(0);
		if (layout.isSidebarInteractionPoint(mouseX, mouseY))
			if (interfaces.state().sidebarOverlayInterfaceId != -1)
				buildInterfaceMenu(layout.sidebarY(), Widget.get(interfaces.state().sidebarOverlayInterfaceId), 1, 0,
						layout.sidebarX(), mouseX, mouseY);
			else if (interfaces.state().tabInterfaceIds[interfaces.state().selectedTab] != -1)
				buildInterfaceMenu(layout.sidebarY(),
						Widget.get(interfaces.state().tabInterfaceIds[interfaces.state().selectedTab]), 1, 0,
						layout.sidebarX(), mouseX, mouseY);
		if (interfaces.currentHoveredWidgetId() != interfaces.sidebarHoveredWidgetId()) {
			redrawSink.redrawSidebar();
			interfaces.setSidebarHoveredWidgetId(interfaces.currentHoveredWidgetId());
		}
		if (interfaces.currentTooltipWidgetId() != interfaces.sidebarTooltipWidgetId()) {
			redrawSink.redrawSidebar();
			interfaces.setSidebarTooltipWidgetId(interfaces.currentTooltipWidgetId());
		}
		interfaces.setCurrentHoveredWidgetId(0);
		interfaces.setCurrentTooltipWidgetId(0);
		if (layout.isChatboxInteractionPoint(mouseX, mouseY))
			if (interfaces.state().chatboxInterfaceId != -1)
				buildInterfaceMenu(layout.chatboxY(), Widget.get(interfaces.state().chatboxInterfaceId), 2, 0,
						layout.chatboxX(), mouseX, mouseY);
			else if (interfaces.state().dialogueInterfaceId != -1)
				buildInterfaceMenu(layout.chatboxY(), Widget.get(interfaces.state().dialogueInterfaceId), 3, 0,
						layout.chatboxX(), mouseX, mouseY);
			else if (layout.isChatboxMessageMenuPoint(mouseX, mouseY) && chatController.inputDialogState() == 0)
				buildChatboxMessageMenu(mouseY - layout.chatboxY(), playerRights, localPlayer.name);
		if ((interfaces.state().chatboxInterfaceId != -1 || interfaces.state().dialogueInterfaceId != -1)
				&& interfaces.currentHoveredWidgetId() != interfaces.chatboxHoveredWidgetId()) {
			redrawSink.redrawChatbox();
			interfaces.setChatboxHoveredWidgetId(interfaces.currentHoveredWidgetId());
		}
		if ((interfaces.state().chatboxInterfaceId != -1 || interfaces.state().dialogueInterfaceId != -1)
				&& interfaces.currentTooltipWidgetId() != interfaces.chatboxTooltipWidgetId()) {
			redrawSink.redrawChatbox();
			interfaces.setChatboxTooltipWidgetId(interfaces.currentTooltipWidgetId());
		}
		state.prioritizeActions();

	}

	/**
	 * Returns the classic combat-level difference color tag.
	 *
	 * @param playerLevel target player's combat level
	 * @param localLevel  local player's combat level
	 * @return revision-377 color tag for the combat-level difference
	 */
	public static String getCombatLevelColorTag(int playerLevel, int localLevel) {
		int levelDifference = localLevel - playerLevel;
		if (levelDifference < -9)
			return "@red@";
		if (levelDifference < -6)
			return "@or3@";
		if (levelDifference < -3)
			return "@or2@";
		if (levelDifference < 0)
			return "@or1@";
		if (levelDifference > 9)
			return "@gre@";
		if (levelDifference > 6)
			return "@gr3@";
		if (levelDifference > 3)
			return "@gr2@";
		if (levelDifference > 0)
			return "@gr1@";
		return "@yel@";
	}
}
