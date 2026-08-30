package rs2.game;

import rs2.cache.def.NpcDefinition;
import rs2.game.entity.Actor;
import rs2.game.entity.Npc;
import rs2.game.entity.Player;
import rs2.scene.SceneConstants;
import rs2.scene.SceneUid;

/**
 * Adds synchronized actors to the Scene using the revision-377 ordering rules.
 */
public final class SceneEntityRenderer {

	/** Creates a new scene entity renderer with its default client state. */
	public SceneEntityRenderer() {
	}

	/** Stores tile render cycles values. */
	private final int[][] tileRenderCycles = new int[SceneConstants.SIZE][SceneConstants.SIZE];

	/** Stores the current render cycle. */
	private int renderCycle;

	/**
	 * Returns render cycle.
	 *
	 * @return the resulting int
	 */
	public int getRenderCycle() {
		return renderCycle;
	}

	/**
	 * Performs begin frame.
	 *
	 * @return the resulting int
	 * @param localPlayer  the local player
	 * @param destinationX the destination x
	 * @param destinationY the destination y
	 */
	public int beginFrame(Actor localPlayer, int destinationX, int destinationY) {
		renderCycle++;
		return localPlayer.x >> SceneConstants.TILE_BITS == destinationX && localPlayer.y >> SceneConstants.TILE_BITS == destinationY ? 0 : destinationX;
	}

	/**
	 * Adds players.
	 *
	 * @param world       the world
	 * @param actors      the actors
	 * @param localPlayer the local player
	 * @param plane       the plane
	 * @param cycle       the cycle
	 * @param lowMemory   the low memory
	 * @param localOnly   the local only
	 */
	public void addPlayers(WorldState world, ActorSynchronizer actors, Player localPlayer, int plane, int cycle,
			boolean lowMemory, boolean localOnly) {
		int count = localOnly ? 1 : actors.playerCount;
		for (int index = 0; index < count; index++) {
			Player player;
			int uid;
			if (localOnly) {
				player = localPlayer;
				uid = ActorSynchronizer.LOCAL_PLAYER_INDEX << SceneUid.ENTITY_ID_SHIFT;
			} else {
				int playerIndex = actors.playerIndices[index];
				player = actors.players[playerIndex];
				uid = playerIndex << SceneUid.ENTITY_ID_SHIFT;
			}
			if (player == null || !player.isVisible()) {
				continue;
			}
			player.isUnanimated = false;
			if ((lowMemory && actors.playerCount > 50 || actors.playerCount > 200) && !localOnly
					&& player.movementSequence == player.idleSequence) {
				player.isUnanimated = true;
			}
			int tileX = player.x >> SceneConstants.TILE_BITS;
			int tileY = player.y >> SceneConstants.TILE_BITS;
			if (tileX < 0 || tileX >= SceneConstants.SIZE || tileY < 0 || tileY >= SceneConstants.SIZE) {
				continue;
			}
			if (player.attachedModel != null && cycle >= player.attachedModelStartCycle
					&& cycle < player.attachedModelEndCycle) {
				player.isUnanimated = false;
				player.tileHeight = world.getTileHeight(player.x, player.y, plane);
				world.scene.addEntityBounds(plane, player.attachedModelMinX, player.attachedModelMinY,
						player.attachedModelMaxX, player.attachedModelMaxY, player.x, player.y, player.tileHeight,
						player, player.rotation, uid);
				continue;
			}
			if ((player.x & SceneConstants.TILE_OFFSET_MASK) == SceneConstants.TILE_CENTER && (player.y & SceneConstants.TILE_OFFSET_MASK) == SceneConstants.TILE_CENTER) {
				if (tileRenderCycles[tileX][tileY] == renderCycle) {
					continue;
				}
				tileRenderCycles[tileX][tileY] = renderCycle;
			}
			player.tileHeight = world.getTileHeight(player.x, player.y, plane);
			world.scene.addEntity(plane, player.x, player.y, player.tileHeight, player, uid, 60,
					player.animationStretches, player.rotation);
		}
	}

	/**
	 * Adds npcs.
	 *
	 * @param world          the world
	 * @param actors         the actors
	 * @param plane          the plane
	 * @param priorityRender the priority render
	 */
	public void addNpcs(WorldState world, ActorSynchronizer actors, int plane, boolean priorityRender) {
		for (int index = 0; index < actors.npcCount; index++) {
			int npcIndex = actors.npcIndices[index];
			Npc npc = actors.npcs[npcIndex];
			int uid = SceneUid.NPC_TYPE_BITS + (npcIndex << SceneUid.ENTITY_ID_SHIFT);
			if (npc == null || !npc.isVisible() || npc.definition.priorityRender != priorityRender
					|| !npc.definition.isMorphVisible()) {
				continue;
			}
			int tileX = npc.x >> SceneConstants.TILE_BITS;
			int tileY = npc.y >> SceneConstants.TILE_BITS;
			if (tileX < 0 || tileX >= SceneConstants.SIZE || tileY < 0 || tileY >= SceneConstants.SIZE) {
				continue;
			}
			if (npc.size == 1 && (npc.x & SceneConstants.TILE_OFFSET_MASK) == SceneConstants.TILE_CENTER && (npc.y & SceneConstants.TILE_OFFSET_MASK) == SceneConstants.TILE_CENTER) {
				if (tileRenderCycles[tileX][tileY] == renderCycle) {
					continue;
				}
				tileRenderCycles[tileX][tileY] = renderCycle;
			}
			NpcDefinition definition = npc.definition;
			if (!definition.clickable) {
				uid += SceneUid.NON_INTERACTIVE_FLAG;
			}
			world.scene.addEntity(plane, npc.x, npc.y, world.getTileHeight(npc.x, npc.y, plane), npc, uid,
					(npc.size - 1) * SceneConstants.TILE_CENTER + 60, npc.animationStretches, npc.rotation);
		}
	}
}
