package rs2.game;

import rs2.cache.def.GameObjectDefinition;
import rs2.cache.def.NpcDefinition;
import rs2.cache.media.ImageRGB;
import rs2.cache.media.IndexedImage;
import rs2.collection.NodeDeque;
import rs2.media.GraphicsBuffer;
import rs2.media.Rasterizer;
import rs2.media.Rasterizer3D;
import rs2.media.renderable.Model;
import rs2.media.renderable.Npc;
import rs2.media.renderable.Player;
import rs2.net.Buffer;

/** Owns the revision-377 minimap raster, map icons and minimap transform state. */
public final class MinimapRenderer {
    private static final int MAX_MAP_FUNCTIONS = 1000;

    public ImageRGB mapImage;
    public int state;
    public int rotationOffset;
    public int zoomOffset;
    private int rotationStep = 2;
    private int zoomStep = 1;
    private int offsetCycle;
    private int rebuildKeepaliveCycle;

    private int mapFunctionCount;
    private final ImageRGB[] mapFunctionIcons = new ImageRGB[MAX_MAP_FUNCTIONS];
    private final int[] mapFunctionX = new int[MAX_MAP_FUNCTIONS];
    private final int[] mapFunctionY = new int[MAX_MAP_FUNCTIONS];

    public void initializeMapImage() {
        mapImage = new ImageRGB(512, 512);
    }

    public void clear() {
        mapImage = null;
        for (int i = 0; i < mapFunctionIcons.length; i++) {
            mapFunctionIcons[i] = null;
        }
        mapFunctionCount = 0;
    }

    public void randomizeLoginOffsets() {
        rotationOffset = (int) (Math.random() * 120D) - 60;
        zoomOffset = (int) (Math.random() * 30D) - 20;
    }

    public void tickRandomOffsets() {
        offsetCycle++;
        if (offsetCycle > 500) {
            offsetCycle = 0;
            int random = (int) (Math.random() * 8D);
            if ((random & 1) == 1) {
                rotationOffset += rotationStep;
            }
            if ((random & 2) == 2) {
                zoomOffset += zoomStep;
            }
        }
        if (rotationOffset < -60) rotationStep = 2;
        if (rotationOffset > 60) rotationStep = -2;
        if (zoomOffset < -20) zoomStep = 1;
        if (zoomOffset > 10) zoomStep = -1;
    }

    public void rebuild(WorldState world, int plane, IndexedImage[] mapSceneSprites, ImageRGB[] mapFunctionSprites,
            GraphicsBuffer sceneBuffer, int[] sceneScanlineOffsets, Buffer outgoing) {
        int[] pixels = mapImage.pixels;
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = 0;
        }
        for (int y = 1; y < 103; y++) {
            int pixelOffset = 24628 + (103 - y) * 512 * 4;
            for (int x = 1; x < 103; x++) {
                if ((world.tileFlags[plane][x][y] & 0x18) == 0) {
                    world.scene.drawMinimapTile(pixels, pixelOffset, 512, plane, x, y);
                }
                if (plane < 3 && (world.tileFlags[plane + 1][x][y] & 8) != 0) {
                    world.scene.drawMinimapTile(pixels, pixelOffset, 512, plane + 1, x, y);
                }
                pixelOffset += 4;
            }
        }

        int wallColor = ((238 + (int) (Math.random() * 20D)) - 10 << 16)
                + ((238 + (int) (Math.random() * 20D)) - 10 << 8)
                + ((238 + (int) (Math.random() * 20D)) - 10);
        int positiveWallColor = (238 + (int) (Math.random() * 20D)) - 10 << 16;
        mapImage.createRasterizer();
        for (int y = 1; y < 103; y++) {
            for (int x = 1; x < 103; x++) {
                if ((world.tileFlags[plane][x][y] & 0x18) == 0) {
                    drawMapLocation(world, y, plane, x, positiveWallColor, wallColor, mapSceneSprites);
                }
                if (plane < 3 && (world.tileFlags[plane + 1][x][y] & 8) != 0) {
                    drawMapLocation(world, y, plane + 1, x, positiveWallColor, wallColor, mapSceneSprites);
                }
            }
        }
        if (sceneBuffer != null) {
            sceneBuffer.bindRaster();
            Rasterizer3D.scanlineOffsets = sceneScanlineOffsets;
        }

        rebuildKeepaliveCycle++;
        if (rebuildKeepaliveCycle > 177) {
            rebuildKeepaliveCycle = 0;
            outgoing.writeOpcode(173);
            outgoing.writeMedium(0x288b80);
        }

        mapFunctionCount = 0;
        for (int x = 0; x < 104; x++) {
            for (int y = 0; y < 104; y++) {
                int uid = world.scene.getFloorDecorationUid(plane, x, y);
                if (uid == 0) {
                    continue;
                }
                int objectId = uid >> 14 & 0x7fff;
                int functionId = GameObjectDefinition.lookup(objectId).mapFunctionId;
                if (functionId < 0) {
                    continue;
                }
                int iconX = x;
                int iconY = y;
                if (functionId != 22 && functionId != 29 && functionId != 34 && functionId != 36
                        && functionId != 46 && functionId != 47 && functionId != 48) {
                    int[][] collisionFlags = world.collisionMaps[plane].flags;
                    for (int step = 0; step < 10; step++) {
                        int direction = (int) (Math.random() * 4D);
                        if (direction == 0 && iconX > 0 && iconX > x - 3
                                && (collisionFlags[iconX - 1][iconY] & 0x1280108) == 0) {
                            iconX--;
                        }
                        if (direction == 1 && iconX < 103 && iconX < x + 3
                                && (collisionFlags[iconX + 1][iconY] & 0x1280180) == 0) {
                            iconX++;
                        }
                        if (direction == 2 && iconY > 0 && iconY > y - 3
                                && (collisionFlags[iconX][iconY - 1] & 0x1280102) == 0) {
                            iconY--;
                        }
                        if (direction == 3 && iconY < 103 && iconY < y + 3
                                && (collisionFlags[iconX][iconY + 1] & 0x1280120) == 0) {
                            iconY++;
                        }
                    }
                }
                mapFunctionIcons[mapFunctionCount] = mapFunctionSprites[functionId];
                mapFunctionX[mapFunctionCount] = iconX;
                mapFunctionY[mapFunctionCount] = iconY;
                mapFunctionCount++;
            }
        }
    }

    private void drawMapLocation(WorldState world, int tileY, int plane, int tileX, int positiveColor,
            int normalColor, IndexedImage[] mapSceneSprites) {
        int uid = world.scene.getWallUid(plane, tileX, tileY);
        if (uid != 0) {
            int config = world.scene.getConfig(plane, tileX, tileY, uid);
            int orientation = config >> 6 & 3;
            int type = config & 0x1f;
            int color = uid > 0 ? positiveColor : normalColor;
            int[] pixels = mapImage.pixels;
            int offset = 24624 + tileX * 4 + (103 - tileY) * 512 * 4;
            GameObjectDefinition definition = GameObjectDefinition.lookup(uid >> 14 & 0x7fff);
            if (definition.mapSceneId != -1) {
                drawMapSceneSprite(definition, mapSceneSprites[definition.mapSceneId], tileX, tileY);
            } else {
                if (type == 0 || type == 2) {
                    if (orientation == 0) {
                        pixels[offset] = color; pixels[offset + 512] = color; pixels[offset + 1024] = color; pixels[offset + 1536] = color;
                    } else if (orientation == 1) {
                        pixels[offset] = color; pixels[offset + 1] = color; pixels[offset + 2] = color; pixels[offset + 3] = color;
                    } else if (orientation == 2) {
                        pixels[offset + 3] = color; pixels[offset + 515] = color; pixels[offset + 1027] = color; pixels[offset + 1539] = color;
                    } else {
                        pixels[offset + 1536] = color; pixels[offset + 1537] = color; pixels[offset + 1538] = color; pixels[offset + 1539] = color;
                    }
                }
                if (type == 3) {
                    if (orientation == 0) pixels[offset] = color;
                    else if (orientation == 1) pixels[offset + 3] = color;
                    else if (orientation == 2) pixels[offset + 1539] = color;
                    else pixels[offset + 1536] = color;
                }
                if (type == 2) {
                    if (orientation == 3) {
                        pixels[offset] = color; pixels[offset + 512] = color; pixels[offset + 1024] = color; pixels[offset + 1536] = color;
                    } else if (orientation == 0) {
                        pixels[offset] = color; pixels[offset + 1] = color; pixels[offset + 2] = color; pixels[offset + 3] = color;
                    } else if (orientation == 1) {
                        pixels[offset + 3] = color; pixels[offset + 515] = color; pixels[offset + 1027] = color; pixels[offset + 1539] = color;
                    } else {
                        pixels[offset + 1536] = color; pixels[offset + 1537] = color; pixels[offset + 1538] = color; pixels[offset + 1539] = color;
                    }
                }
            }
        }

        uid = world.scene.getInteractiveObjectUid(plane, tileX, tileY);
        if (uid != 0) {
            int config = world.scene.getConfig(plane, tileX, tileY, uid);
            int orientation = config >> 6 & 3;
            int type = config & 0x1f;
            GameObjectDefinition definition = GameObjectDefinition.lookup(uid >> 14 & 0x7fff);
            if (definition.mapSceneId != -1) {
                drawMapSceneSprite(definition, mapSceneSprites[definition.mapSceneId], tileX, tileY);
            } else if (type == 9) {
                int color = uid > 0 ? 0xee0000 : 0xeeeeee;
                int[] pixels = mapImage.pixels;
                int offset = 24624 + tileX * 4 + (103 - tileY) * 512 * 4;
                if (orientation == 0 || orientation == 2) {
                    pixels[offset + 1536] = color;
                    pixels[offset + 1025] = color;
                    pixels[offset + 514] = color;
                    pixels[offset + 3] = color;
                } else {
                    pixels[offset] = color;
                    pixels[offset + 513] = color;
                    pixels[offset + 1026] = color;
                    pixels[offset + 1539] = color;
                }
            }
        }

        uid = world.scene.getFloorDecorationUid(plane, tileX, tileY);
        if (uid != 0) {
            GameObjectDefinition definition = GameObjectDefinition.lookup(uid >> 14 & 0x7fff);
            if (definition.mapSceneId != -1) {
                drawMapSceneSprite(definition, mapSceneSprites[definition.mapSceneId], tileX, tileY);
            }
        }
    }

    private static void drawMapSceneSprite(GameObjectDefinition definition, IndexedImage sprite, int tileX, int tileY) {
        if (sprite == null) {
            return;
        }
        int xOffset = (definition.sizeX * 4 - sprite.width) / 2;
        int yOffset = (definition.sizeY * 4 - sprite.height) / 2;
        sprite.draw(48 + tileX * 4 + xOffset, 48 + (104 - tileY - definition.sizeY) * 4 + yOffset);
    }

    public void draw(WorldState world, ActorSynchronizer actors, Player localPlayer, int plane,
            int cameraYaw, int destinationX, int destinationY, int hintType, int hintNpcIndex,
            int hintTileX, int hintTileY, int hintPlayerIndex, int gameCycle, int baseX, int baseY,
            Assets assets, FriendLookup friends) {
        assets.minimapBuffer.bindRaster();
        if (state == 2) {
            byte[] mask = assets.minimapMask.pixels;
            int[] raster = Rasterizer.pixels;
            for (int i = 0; i < mask.length; i++) {
                if (mask[i] == 0) raster[i] = 0;
            }
            assets.compass.shapeImageToPixels(0, 0, 33, 33, 256, 25, assets.compassMaskWidths, cameraYaw,
                    assets.compassMaskOffsets, 25);
            assets.sceneBuffer.bindRaster();
            Rasterizer3D.scanlineOffsets = assets.sceneScanlineOffsets;
            return;
        }

        int rotation = cameraYaw + rotationOffset & 0x7ff;
        int mapX = 48 + localPlayer.x / 32;
        int mapY = 464 - localPlayer.y / 32;
        mapImage.shapeImageToPixels(25, 5, 146, 151, 256 + zoomOffset, mapX, assets.minimapMaskWidths,
                rotation, assets.minimapMaskOffsets, mapY);
        assets.compass.shapeImageToPixels(0, 0, 33, 33, 256, 25, assets.compassMaskWidths, cameraYaw,
                assets.compassMaskOffsets, 25);

        for (int i = 0; i < mapFunctionCount; i++) {
            int dx = mapFunctionX[i] * 4 + 2 - localPlayer.x / 32;
            int dy = mapFunctionY[i] * 4 + 2 - localPlayer.y / 32;
            drawOnMinimap(dy, mapFunctionIcons[i], dx, cameraYaw, assets);
        }

        for (int x = 0; x < 104; x++) {
            for (int y = 0; y < 104; y++) {
                NodeDeque items = world.groundItems[plane][x][y];
                if (items != null) {
                    drawOnMinimap(y * 4 + 2 - localPlayer.y / 32, assets.groundItemDot,
                            x * 4 + 2 - localPlayer.x / 32, cameraYaw, assets);
                }
            }
        }

        for (int i = 0; i < actors.npcCount; i++) {
            Npc npc = actors.npcs[actors.npcIndices[i]];
            if (npc != null && npc.isVisible()) {
                NpcDefinition definition = npc.definition;
                if (definition.morphIds != null) definition = definition.transform();
                if (definition != null && definition.visibleOnMinimap && definition.clickable) {
                    drawOnMinimap(npc.y / 32 - localPlayer.y / 32, assets.npcDot,
                            npc.x / 32 - localPlayer.x / 32, cameraYaw, assets);
                }
            }
        }

        for (int i = 0; i < actors.playerCount; i++) {
            Player player = actors.players[actors.playerIndices[i]];
            if (player == null || !player.isVisible()) continue;
            int dx = player.x / 32 - localPlayer.x / 32;
            int dy = player.y / 32 - localPlayer.y / 32;
            boolean friend = friends.isFriend(player.name);
            boolean teammate = localPlayer.team != 0 && player.team != 0 && localPlayer.team == player.team;
            drawOnMinimap(dy, friend ? assets.friendDot : teammate ? assets.teamDot : assets.playerDot,
                    dx, cameraYaw, assets);
        }

        if (hintType != 0 && gameCycle % 20 < 10) {
            if (hintType == 1 && hintNpcIndex >= 0 && hintNpcIndex < actors.npcs.length) {
                Npc npc = actors.npcs[hintNpcIndex];
                if (npc != null) {
                    drawHint(npc.y / 32 - localPlayer.y / 32, assets.hintMarker,
                            npc.x / 32 - localPlayer.x / 32, cameraYaw, assets);
                }
            } else if (hintType == 2) {
                drawHint((hintTileY - baseY) * 4 + 2 - localPlayer.y / 32, assets.hintMarker,
                        (hintTileX - baseX) * 4 + 2 - localPlayer.x / 32, cameraYaw, assets);
            } else if (hintType == 10 && hintPlayerIndex >= 0 && hintPlayerIndex < actors.players.length) {
                Player player = actors.players[hintPlayerIndex];
                if (player != null) {
                    drawHint(player.y / 32 - localPlayer.y / 32, assets.hintMarker,
                            player.x / 32 - localPlayer.x / 32, cameraYaw, assets);
                }
            }
        }
        if (destinationX != 0) {
            drawOnMinimap(destinationY * 4 + 2 - localPlayer.y / 32, assets.destinationMarker,
                    destinationX * 4 + 2 - localPlayer.x / 32, cameraYaw, assets);
        }
        Rasterizer.drawFilledRectangle(97, 78, 3, 3, 0xffffff);
        assets.sceneBuffer.bindRaster();
        Rasterizer3D.scanlineOffsets = assets.sceneScanlineOffsets;
    }

    private void drawHint(int dy, ImageRGB sprite, int dx, int cameraYaw, Assets assets) {
        int distance = dx * dx + dy * dy;
        if (distance > 4225 && distance < 0x15f90) {
            int angle = cameraYaw + rotationOffset & 0x7ff;
            int sine = Model.SINE[angle] * 256 / (zoomOffset + 256);
            int cosine = Model.COSINE[angle] * 256 / (zoomOffset + 256);
            int rotatedX = dy * sine + dx * cosine >> 16;
            int rotatedY = dy * cosine - dx * sine >> 16;
            double radians = Math.atan2(rotatedX, rotatedY);
            int x = (int) (Math.sin(radians) * 63D);
            int y = (int) (Math.cos(radians) * 57D);
            assets.edgeArrow.drawRotated((94 + x + 4) - 10, 83 - y - 20, 15, 15, 20, 20, 256, radians);
        } else {
            drawOnMinimap(dy, sprite, dx, cameraYaw, assets);
        }
    }

    private void drawOnMinimap(int dy, ImageRGB sprite, int dx, int cameraYaw, Assets assets) {
        if (sprite == null) return;
        int angle = cameraYaw + rotationOffset & 0x7ff;
        int distance = dx * dx + dy * dy;
        if (distance > 6400) return;
        int sine = Model.SINE[angle] * 256 / (zoomOffset + 256);
        int cosine = Model.COSINE[angle] * 256 / (zoomOffset + 256);
        int rotatedX = dy * sine + dx * cosine >> 16;
        int rotatedY = dy * cosine - dx * sine >> 16;
        if (distance > 2500) {
            sprite.drawTo(assets.minimapMask,
                    ((94 + rotatedX) - sprite.maxWidth / 2) + 4,
                    83 - rotatedY - sprite.maxHeight / 2 - 4);
        } else {
            sprite.drawImage(((94 + rotatedX) - sprite.maxWidth / 2) + 4,
                    83 - rotatedY - sprite.maxHeight / 2 - 4);
        }
    }

    public Click transformClick(int clickX, int clickY, Player localPlayer, int cameraYaw) {
        int x = clickX - 25 - 550;
        int y = clickY - 5 - 4;
        if (x < 0 || y < 0 || x >= 146 || y >= 151) return null;
        x -= 73;
        y -= 75;
        int angle = cameraYaw + rotationOffset & 0x7ff;
        int sine = Rasterizer3D.SINE[angle] * (zoomOffset + 256) >> 8;
        int cosine = Rasterizer3D.COSINE[angle] * (zoomOffset + 256) >> 8;
        int worldOffsetX = y * sine + x * cosine >> 11;
        int worldOffsetY = y * cosine - x * sine >> 11;
        int tileX = localPlayer.x + worldOffsetX >> 7;
        int tileY = localPlayer.y - worldOffsetY >> 7;
        return new Click(x, y, tileX, tileY);
    }

    public static final class Click {
        public final int localX;
        public final int localY;
        public final int tileX;
        public final int tileY;
        private Click(int localX, int localY, int tileX, int tileY) {
            this.localX = localX; this.localY = localY; this.tileX = tileX; this.tileY = tileY;
        }
    }

    public interface FriendLookup {
        boolean isFriend(String name);
    }

    public static final class Assets {
        public GraphicsBuffer minimapBuffer;
        public GraphicsBuffer sceneBuffer;
        public IndexedImage minimapMask;
        public ImageRGB compass;
        public int[] compassMaskWidths;
        public int[] compassMaskOffsets;
        public int[] minimapMaskWidths;
        public int[] minimapMaskOffsets;
        public int[] sceneScanlineOffsets;
        public ImageRGB groundItemDot;
        public ImageRGB npcDot;
        public ImageRGB playerDot;
        public ImageRGB friendDot;
        public ImageRGB teamDot;
        public ImageRGB hintMarker;
        public ImageRGB destinationMarker;
        public ImageRGB edgeArrow;
    }
}