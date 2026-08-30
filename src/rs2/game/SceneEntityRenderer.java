package rs2.game;

import rs2.cache.def.NpcDefinition;
import rs2.media.renderable.Actor;
import rs2.media.renderable.Npc;
import rs2.media.renderable.Player;

/**
 * Adds synchronized actors to the Scene using the revision-377 ordering rules.
 */
public final class SceneEntityRenderer {

	/** Creates a new scene entity renderer with its default client state. */
	public SceneEntityRenderer() {
	}

	/** Stores tile render cycles values. */
	private final int[][] tileRenderCycles = new int[104][104];

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
		return localPlayer.x >> 7 == destinationX && localPlayer.y >> 7 == destinationY ? 0 : destinationX;
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
				uid = ActorSynchronizer.LOCAL_PLAYER_INDEX << 14;
			} else {
				int playerIndex = actors.playerIndices[index];
				player = actors.players[playerIndex];
				uid = playerIndex << 14;
			}
			if (player == null || !player.isVisible()) {
				continue;
			}
			player.isUnanimated = false;
			if ((lowMemory && actors.playerCount > 50 || actors.playerCount > 200) && !localOnly
					&& player.movementSequence == player.idleSequence) {
				player.isUnanimated = true;
			}
			int tileX = player.x >> 7;
			int tileY = player.y >> 7;
			if (tileX < 0 || tileX >= 104 || tileY < 0 || tileY >= 104) {
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
			if ((player.x & 0x7f) == 64 && (player.y & 0x7f) == 64) {
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
			int uid = 0x20000000 + (npcIndex << 14);
			if (npc == null || !npc.isVisible() || npc.definition.priorityRender != priorityRender
					|| !npc.definition.isMorphVisible()) {
				continue;
			}
			int tileX = npc.x >> 7;
			int tileY = npc.y >> 7;
			if (tileX < 0 || tileX >= 104 || tileY < 0 || tileY >= 104) {
				continue;
			}
			if (npc.size == 1 && (npc.x & 0x7f) == 64 && (npc.y & 0x7f) == 64) {
				if (tileRenderCycles[tileX][tileY] == renderCycle) {
					continue;
				}
				tileRenderCycles[tileX][tileY] = renderCycle;
			}
			NpcDefinition definition = npc.definition;
			if (!definition.clickable) {
				uid += 0x80000000;
			}
			world.scene.addEntity(plane, npc.x, npc.y, world.getTileHeight(npc.x, npc.y, plane), npc, uid,
					(npc.size - 1) * 64 + 60, npc.animationStretches, npc.rotation);
		}
	}
}
