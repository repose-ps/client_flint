package rs2.game.render;

import rs2.ui.ClientLayout;

import java.util.function.Predicate;

import rs2.ui.ClientLayout;
import rs2.cache.def.NpcDefinition;
import rs2.chat.ChatMode;
import rs2.game.ActorSynchronizer;
import rs2.game.CameraController;
import rs2.game.SceneEntityRenderer;
import rs2.game.WorldState;
import rs2.game.entity.Actor;
import rs2.game.entity.Npc;
import rs2.game.entity.Player;
import rs2.media.Rasterizer;
import rs2.media.TypeFace;
import rs2.media.sprite.ImageRGB;

/**
 * Projects and draws transient viewport overlays associated with players, NPCs,
 * and world hint targets.
 *
 * <p>The renderer owns the short-lived projection coordinates and the fixed
 * overhead-text layout buffers that historically lived on the top-level client.
 * World/entity state remains owned by the synchronization and world systems and
 * is supplied through a per-frame {@link Context}.</p>
 */
public final class ActorOverlayRenderer {

    /** Maximum overhead-chat entries retained for one rendered frame. */
    private static final int OVERHEAD_TEXT_LIMIT = 50;
    /** Default overhead-chat lifetime used by revision 377 effects. */
    private static final int OVERHEAD_TEXT_LIFETIME = 150;
    /** Number of client cycles in one flashing hint period. */
    private static final int HINT_FLASH_PERIOD = 20;
    /** Number of visible cycles in one flashing hint period. */
    private static final int HINT_FLASH_VISIBLE_CYCLES = 10;
    /** Width of the classic actor health bar. */
    private static final int HEALTH_BAR_WIDTH = 30;

    /** Most recently projected viewport X coordinate. */
    private int projectedX = -1;
    /** Most recently projected viewport Y coordinate. */
    private int projectedY = -1;
    /** Number of overhead-text entries collected for the current frame. */
    private int overheadTextCount;
    /** Projected X coordinate for each queued overhead-text entry. */
    private final int[] overheadTextXs = new int[OVERHEAD_TEXT_LIMIT];
    /** Projected/overlap-adjusted Y coordinate for each overhead-text entry. */
    private final int[] overheadTextYs = new int[OVERHEAD_TEXT_LIMIT];
    /** Font/effect height used during overhead-text overlap resolution. */
    private final int[] overheadTextHeights = new int[OVERHEAD_TEXT_LIMIT];
    /** Half-width used during overhead-text overlap resolution. */
    private final int[] overheadTextHalfWidths = new int[OVERHEAD_TEXT_LIMIT];
    /** Revision-377 color/effect code for each overhead-text entry. */
    private final int[] overheadTextColorCodes = new int[OVERHEAD_TEXT_LIMIT];
    /** Revision-377 motion effect for each overhead-text entry. */
    private final int[] overheadTextEffects = new int[OVERHEAD_TEXT_LIMIT];
    /** Remaining actor text lifetime captured for each overhead-text entry. */
    private final int[] overheadTextCycles = new int[OVERHEAD_TEXT_LIMIT];
    /** Text content for each queued overhead-text entry. */
    private final String[] overheadTexts = new String[OVERHEAD_TEXT_LIMIT];

    /** Creates an overlay renderer with empty per-frame scratch state. */
    public ActorOverlayRenderer() {
    }

    /**
     * Projects and draws actor head icons, hints, overhead text, health bars and
     * hitmarks for one viewport frame.
     *
     * @param context current frame state and rendering assets
     */
    public void drawActors(Context context) {
        overheadTextCount = 0;
        ActorSynchronizer actors = context.actors;
        for (int actorListIndex = -1; actorListIndex < actors.playerCount + actors.npcCount; actorListIndex++) {
            Object object;
            if (actorListIndex == -1) {
                object = context.localPlayer;
            } else if (actorListIndex < actors.playerCount) {
                object = actors.players[actors.playerIndices[actorListIndex]];
            } else {
                object = actors.npcs[actors.npcIndices[actorListIndex - actors.playerCount]];
            }
            if (object == null || !((Actor) object).isVisible()) {
                continue;
            }
            if (object instanceof Npc) {
                NpcDefinition npcDefinition = ((Npc) object).definition;
                if (npcDefinition.morphIds != null) {
                    npcDefinition = npcDefinition.transform();
                }
                if (npcDefinition == null) {
                    continue;
                }
            }
            if (actorListIndex < actors.playerCount) {
                int iconY = 30;
                Player player = (Player) object;
                if (player.skullIcon != -1 || player.prayerIcon != -1) {
                    projectActor(context, (Actor) object, ((Actor) object).height + 15);
                    if (projectedX > -1) {
                        if (player.skullIcon != -1) {
                            context.skullIconSprites[player.skullIcon].drawImage(projectedX - 12, projectedY - iconY);
                            iconY += 25;
                        }
                        if (player.prayerIcon != -1) {
                            context.prayerIconSprites[player.prayerIcon].drawImage(projectedX - 12, projectedY - iconY);
                            iconY += 25;
                        }
                    }
                }
                if (actorListIndex >= 0 && context.hintIconType == 10
                        && context.hintPlayerIndex == actors.playerIndices[actorListIndex]) {
                    projectActor(context, (Actor) object, ((Actor) object).height + 15);
                    if (projectedX > -1) {
                        context.hintIconSprites[1].drawImage(projectedX - 12, projectedY - iconY);
                    }
                }
            } else {
                NpcDefinition npcDefinition = ((Npc) object).definition;
                if (npcDefinition.prayerIcon >= 0 && npcDefinition.prayerIcon < context.prayerIconSprites.length) {
                    projectActor(context, (Actor) object, ((Actor) object).height + 15);
                    if (projectedX > -1) {
                        context.prayerIconSprites[npcDefinition.prayerIcon].drawImage(projectedX - 12, projectedY - 30);
                    }
                }
                if (context.hintIconType == 1
                        && context.hintNpcIndex == actors.npcIndices[actorListIndex - actors.playerCount]
                        && context.gameCycle % HINT_FLASH_PERIOD < HINT_FLASH_VISIBLE_CYCLES) {
                    projectActor(context, (Actor) object, ((Actor) object).height + 15);
                    if (projectedX > -1) {
                        context.hintIconSprites[0].drawImage(projectedX - 12, projectedY - 28);
                    }
                }
            }

            Actor actor = (Actor) object;
            if (actor.overheadText != null
                    && (actorListIndex >= actors.playerCount || context.publicChatMode == ChatMode.ON
                            || context.publicChatMode == ChatMode.HIDE
                            || context.publicChatMode == ChatMode.FRIENDS
                                    && context.friendOrSelf.test(((Player) object).name))) {
                projectActor(context, actor, actor.height);
                if (projectedX > -1 && overheadTextCount < OVERHEAD_TEXT_LIMIT) {
                    overheadTextHalfWidths[overheadTextCount] = context.boldFont.getTextWidth(actor.overheadText) / 2;
                    overheadTextHeights[overheadTextCount] = context.boldFont.lineHeight;
                    overheadTextXs[overheadTextCount] = projectedX;
                    overheadTextYs[overheadTextCount] = projectedY;
                    overheadTextColorCodes[overheadTextCount] = actor.overheadTextColor;
                    overheadTextEffects[overheadTextCount] = actor.overheadTextEffect;
                    overheadTextCycles[overheadTextCount] = actor.overheadTextCyclesRemaining;
                    overheadTexts[overheadTextCount++] = actor.overheadText;
                    // Preserve the original revision-377 indexing behavior exactly.
                    if (context.chatEffects == 0 && actor.overheadTextEffect >= 1 && actor.overheadTextEffect <= 3) {
                        overheadTextHeights[overheadTextCount] += 10;
                        overheadTextYs[overheadTextCount] += 5;
                    }
                    if (context.chatEffects == 0 && actor.overheadTextEffect == 4) {
                        overheadTextHalfWidths[overheadTextCount] = 60;
                    }
                    if (context.chatEffects == 0 && actor.overheadTextEffect == 5) {
                        overheadTextHeights[overheadTextCount] += 5;
                    }
                }
            }
            if (actor.healthBarCycle > context.gameCycle) {
                projectActor(context, actor, actor.height + 15);
                if (projectedX > -1) {
                    int healthBarWidth = (actor.currentHealth * HEALTH_BAR_WIDTH) / actor.maxHealth;
                    if (healthBarWidth > HEALTH_BAR_WIDTH) {
                        healthBarWidth = HEALTH_BAR_WIDTH;
                    }
                    Rasterizer.drawFilledRectangle(projectedX - 15, projectedY - 3, healthBarWidth, 5, 65280);
                    Rasterizer.drawFilledRectangle((projectedX - 15) + healthBarWidth, projectedY - 3,
                            HEALTH_BAR_WIDTH - healthBarWidth, 5, 0xff0000);
                }
            }
            for (int hitIndex = 0; hitIndex < Actor.HIT_SPLAT_COUNT; hitIndex++) {
                if (actor.hitCycles[hitIndex] > context.gameCycle) {
                    projectActor(context, actor, actor.height / 2);
                    if (projectedX > -1) {
                        if (hitIndex == 1) {
                            projectedY -= 20;
                        }
                        if (hitIndex == 2) {
                            projectedX -= 15;
                            projectedY -= 10;
                        }
                        if (hitIndex == 3) {
                            projectedX += 15;
                            projectedY -= 10;
                        }
                        context.hitmarkSprites[actor.hitTypes[hitIndex]].drawImage(projectedX - 12, projectedY - 12);
                        context.smallFont.drawCenteredText(String.valueOf(actor.hitDamages[hitIndex]), projectedX,
                                projectedY + 4, 0);
                        context.smallFont.drawCenteredText(String.valueOf(actor.hitDamages[hitIndex]), projectedX - 1,
                                projectedY + 3, 0xffffff);
                    }
                }
            }
        }

        drawOverheadText(context);
    }

    /**
     * Draws the flashing world-coordinate hint icon when one is active.
     *
     * @param context current frame state and rendering assets
     */
    public void drawWorldHint(Context context) {
        if (context.hintIconType != 2) {
            return;
        }
        projectWorld(context, (context.hintTileX - context.regionBaseX << 7) + context.hintOffsetX,
                context.hintHeight * 2, (context.hintTileY - context.regionBaseY << 7) + context.hintOffsetY);
        if (projectedX > -1 && context.gameCycle % HINT_FLASH_PERIOD < HINT_FLASH_VISIBLE_CYCLES) {
            context.hintIconSprites[0].drawImage(projectedX - 12, projectedY - 28);
        }
    }

    /** Draws queued overhead text after resolving vertical overlap.
     * @param context current frame state and rendering assets
     */
    private void drawOverheadText(Context context) {
        for (int overheadIndex = 0; overheadIndex < overheadTextCount; overheadIndex++) {
            int textX = overheadTextXs[overheadIndex];
            int textY = overheadTextYs[overheadIndex];
            int textHalfWidth = overheadTextHalfWidths[overheadIndex];
            int textHeight = overheadTextHeights[overheadIndex];
            boolean adjustingOverlap = true;
            while (adjustingOverlap) {
                adjustingOverlap = false;
                for (int previousTextIndex = 0; previousTextIndex < overheadIndex; previousTextIndex++) {
                    if (textY + 2 > overheadTextYs[previousTextIndex] - overheadTextHeights[previousTextIndex]
                            && textY - textHeight < overheadTextYs[previousTextIndex] + 2
                            && textX - textHalfWidth < overheadTextXs[previousTextIndex]
                                    + overheadTextHalfWidths[previousTextIndex]
                            && textX + textHalfWidth > overheadTextXs[previousTextIndex]
                                    - overheadTextHalfWidths[previousTextIndex]
                            && overheadTextYs[previousTextIndex] - overheadTextHeights[previousTextIndex] < textY) {
                        textY = overheadTextYs[previousTextIndex] - overheadTextHeights[previousTextIndex];
                        adjustingOverlap = true;
                    }
                }
            }
            projectedX = overheadTextXs[overheadIndex];
            projectedY = overheadTextYs[overheadIndex] = textY;
            String overheadText = overheadTexts[overheadIndex];
            if (context.chatEffects == 0) {
                int textColor = 0xffff00;
                if (overheadTextColorCodes[overheadIndex] < 6) {
                    textColor = context.overheadTextColors[overheadTextColorCodes[overheadIndex]];
                }
                if (overheadTextColorCodes[overheadIndex] == 6) {
                    textColor = context.renderCycle % 20 >= 10 ? 0xffff00 : 0xff0000;
                }
                if (overheadTextColorCodes[overheadIndex] == 7) {
                    textColor = context.renderCycle % 20 >= 10 ? 65535 : 255;
                }
                if (overheadTextColorCodes[overheadIndex] == 8) {
                    textColor = context.renderCycle % 20 >= 10 ? 0x80ff80 : 45056;
                }
                if (overheadTextColorCodes[overheadIndex] == 9) {
                    int colorAge = OVERHEAD_TEXT_LIFETIME - overheadTextCycles[overheadIndex];
                    if (colorAge < 50) {
                        textColor = 0xff0000 + 1280 * colorAge;
                    } else if (colorAge < 100) {
                        textColor = 0xffff00 - 0x50000 * (colorAge - 50);
                    } else if (colorAge < OVERHEAD_TEXT_LIFETIME) {
                        textColor = 65280 + 5 * (colorAge - 100);
                    }
                }
                if (overheadTextColorCodes[overheadIndex] == 10) {
                    int colorAge = OVERHEAD_TEXT_LIFETIME - overheadTextCycles[overheadIndex];
                    if (colorAge < 50) {
                        textColor = 0xff0000 + 5 * colorAge;
                    } else if (colorAge < 100) {
                        textColor = 0xff00ff - 0x50000 * (colorAge - 50);
                    } else if (colorAge < OVERHEAD_TEXT_LIFETIME) {
                        textColor = (255 + 0x50000 * (colorAge - 100)) - 5 * (colorAge - 100);
                    }
                }
                if (overheadTextColorCodes[overheadIndex] == 11) {
                    int colorAge = OVERHEAD_TEXT_LIFETIME - overheadTextCycles[overheadIndex];
                    if (colorAge < 50) {
                        textColor = 0xffffff - 0x50005 * colorAge;
                    } else if (colorAge < 100) {
                        textColor = 65280 + 0x50005 * (colorAge - 50);
                    } else if (colorAge < OVERHEAD_TEXT_LIFETIME) {
                        textColor = 0xffffff - 0x50000 * (colorAge - 100);
                    }
                }
                int effect = overheadTextEffects[overheadIndex];
                if (effect == 0) {
                    context.boldFont.drawCenteredText(overheadText, projectedX, projectedY + 1, 0);
                    context.boldFont.drawCenteredText(overheadText, projectedX, projectedY, textColor);
                }
                if (effect == 1) {
                    context.boldFont.drawWaveText(overheadText, projectedX, projectedY + 1, 0, context.renderCycle);
                    context.boldFont.drawWaveText(overheadText, projectedX, projectedY, textColor, context.renderCycle);
                }
                if (effect == 2) {
                    context.boldFont.drawWave2Text(overheadText, projectedX, projectedY + 1, 0, context.renderCycle);
                    context.boldFont.drawWave2Text(overheadText, projectedX, projectedY, textColor, context.renderCycle);
                }
                if (effect == 3) {
                    context.boldFont.drawWaveAmplitudeText(overheadText, projectedX, projectedY + 1, 0,
                            OVERHEAD_TEXT_LIFETIME - overheadTextCycles[overheadIndex], context.renderCycle);
                    context.boldFont.drawWaveAmplitudeText(overheadText, projectedX, projectedY, textColor,
                            OVERHEAD_TEXT_LIFETIME - overheadTextCycles[overheadIndex], context.renderCycle);
                }
                if (effect == 4) {
                    int textWidth = context.boldFont.getTextWidth(overheadText);
                    int scrollOffset = ((OVERHEAD_TEXT_LIFETIME - overheadTextCycles[overheadIndex])
                            * (textWidth + 100)) / OVERHEAD_TEXT_LIFETIME;
                    Rasterizer.setCoordinates(projectedX - 50, 0, projectedX + 50, context.layout.viewportHeight());
                    context.boldFont.drawText(overheadText, (projectedX + 50) - scrollOffset, projectedY + 1, 0);
                    context.boldFont.drawText(overheadText, (projectedX + 50) - scrollOffset, projectedY, textColor);
                    Rasterizer.resetCoordinates();
                }
                if (effect == 5) {
                    int effectAge = OVERHEAD_TEXT_LIFETIME - overheadTextCycles[overheadIndex];
                    int verticalOffset = 0;
                    if (effectAge < 25) {
                        verticalOffset = effectAge - 25;
                    } else if (effectAge > 125) {
                        verticalOffset = effectAge - 125;
                    }
                    Rasterizer.setCoordinates(0, projectedY - context.boldFont.lineHeight - 1,
                            context.layout.viewportWidth(), projectedY + 5);
                    context.boldFont.drawCenteredText(overheadText, projectedX, projectedY + 1 + verticalOffset, 0);
                    context.boldFont.drawCenteredText(overheadText, projectedX, projectedY + verticalOffset, textColor);
                    Rasterizer.resetCoordinates();
                }
            } else {
                context.boldFont.drawCenteredText(overheadText, projectedX, projectedY + 1, 0);
                context.boldFont.drawCenteredText(overheadText, projectedX, projectedY, 0xffff00);
            }
        }
    }

    /** Projects an actor into viewport coordinates.
     * @param context current frame state
     * @param actor actor to project
     * @param heightOffset vertical offset from terrain height
     */
    private void projectActor(Context context, Actor actor, int heightOffset) {
        projectWorld(context, actor.x, heightOffset, actor.y);
    }

    /** Projects a world-space point into viewport coordinates.
     * @param context current frame state
     * @param worldX local world X
     * @param heightOffset vertical offset from terrain height
     * @param worldY local world Y
     */
    private void projectWorld(Context context, int worldX, int heightOffset, int worldY) {
        CameraController.ScreenPoint point = context.cameraController.project(context.worldState, context.currentPlane,
                worldX, heightOffset, worldY);
        projectedX = point.x;
        projectedY = point.y;
    }

    /** Immutable collection of state and assets used during one overlay frame. */
    public static final class Context {
        /** Current fixed/resizable layout. */
        public final ClientLayout layout;
        /** Synchronized actor registry. */
        public final ActorSynchronizer actors;
        /** Local player instance. */
        public final Player localPlayer;
        /** Camera projection state. */
        public final CameraController cameraController;
        /** Current scene/world state. */
        public final WorldState worldState;
        /** Current scene plane. */
        public final int currentPlane;
        /** Current client cycle. */
        public final int gameCycle;
        /** Current scene entity render cycle. */
        public final int renderCycle;
        /** Current public-chat visibility mode. */
        public final int publicChatMode;
        /** Chat-effect preference flag. */
        public final int chatEffects;
        /** Predicate identifying friend/self names for public-chat filtering. */
        public final Predicate<String> friendOrSelf;
        /** Small hitmark-number font. */
        public final TypeFace smallFont;
        /** Bold overhead-text font. */
        public final TypeFace boldFont;
        /** Player skull icons. */
        public final ImageRGB[] skullIconSprites;
        /** Player/NPC prayer icons. */
        public final ImageRGB[] prayerIconSprites;
        /** Player/NPC/world hint icons. */
        public final ImageRGB[] hintIconSprites;
        /** Hit-splat sprites. */
        public final ImageRGB[] hitmarkSprites;
        /** Base overhead-text color palette. */
        public final int[] overheadTextColors;
        /** Active hint target type. */
        public final int hintIconType;
        /** Active hinted player index. */
        public final int hintPlayerIndex;
        /** Active hinted NPC index. */
        public final int hintNpcIndex;
        /** Active hinted absolute tile X. */
        public final int hintTileX;
        /** Active hinted absolute tile Y. */
        public final int hintTileY;
        /** Hint height offset. */
        public final int hintHeight;
        /** Fine X offset for tile hints. */
        public final int hintOffsetX;
        /** Fine Y offset for tile hints. */
        public final int hintOffsetY;
        /** Absolute X coordinate of the local region base. */
        public final int regionBaseX;
        /** Absolute Y coordinate of the local region base. */
        public final int regionBaseY;

        /**
         * Creates one actor-overlay rendering context.
         *
         * @param layout client layout
         * @param actors synchronized actors
         * @param localPlayer local player
         * @param cameraController camera controller
         * @param worldState world state
         * @param currentPlane current scene plane
         * @param gameCycle current client cycle
         * @param renderCycle current scene render cycle
         * @param publicChatMode public chat mode
         * @param chatEffects chat effect preference
         * @param friendOrSelf friend/self predicate
         * @param smallFont small font
         * @param boldFont bold font
         * @param skullIconSprites skull icons
         * @param prayerIconSprites prayer icons
         * @param hintIconSprites hint icons
         * @param hitmarkSprites hitmark sprites
         * @param overheadTextColors overhead-text palette
         * @param hintIconType hint target type
         * @param hintPlayerIndex hinted player index
         * @param hintNpcIndex hinted NPC index
         * @param hintTileX hinted absolute tile X
         * @param hintTileY hinted absolute tile Y
         * @param hintHeight hint height
         * @param hintOffsetX hint X offset
         * @param hintOffsetY hint Y offset
         * @param regionBaseX region base X
         * @param regionBaseY region base Y
         */
        public Context(ClientLayout layout, ActorSynchronizer actors, Player localPlayer,
                CameraController cameraController, WorldState worldState, int currentPlane, int gameCycle,
                int renderCycle, int publicChatMode, int chatEffects, Predicate<String> friendOrSelf,
                TypeFace smallFont, TypeFace boldFont, ImageRGB[] skullIconSprites, ImageRGB[] prayerIconSprites,
                ImageRGB[] hintIconSprites, ImageRGB[] hitmarkSprites, int[] overheadTextColors, int hintIconType,
                int hintPlayerIndex, int hintNpcIndex, int hintTileX, int hintTileY, int hintHeight, int hintOffsetX,
                int hintOffsetY, int regionBaseX, int regionBaseY) {
            this.layout = layout;
            this.actors = actors;
            this.localPlayer = localPlayer;
            this.cameraController = cameraController;
            this.worldState = worldState;
            this.currentPlane = currentPlane;
            this.gameCycle = gameCycle;
            this.renderCycle = renderCycle;
            this.publicChatMode = publicChatMode;
            this.chatEffects = chatEffects;
            this.friendOrSelf = friendOrSelf;
            this.smallFont = smallFont;
            this.boldFont = boldFont;
            this.skullIconSprites = skullIconSprites;
            this.prayerIconSprites = prayerIconSprites;
            this.hintIconSprites = hintIconSprites;
            this.hitmarkSprites = hitmarkSprites;
            this.overheadTextColors = overheadTextColors;
            this.hintIconType = hintIconType;
            this.hintPlayerIndex = hintPlayerIndex;
            this.hintNpcIndex = hintNpcIndex;
            this.hintTileX = hintTileX;
            this.hintTileY = hintTileY;
            this.hintHeight = hintHeight;
            this.hintOffsetX = hintOffsetX;
            this.hintOffsetY = hintOffsetY;
            this.regionBaseX = regionBaseX;
            this.regionBaseY = regionBaseY;
        }
    }
}
