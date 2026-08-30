package rs2.game;

import java.util.Arrays;

import rs2.cache.def.GameObjectDefinition;
import rs2.cache.def.ItemDefinition;
import rs2.cache.def.NpcDefinition;
import rs2.cache.def.SpotAnimation;
import rs2.cache.ondemand.OnDemandFetcher;
import rs2.cache.ondemand.OnDemandRequest;
import rs2.media.sprite.ItemSpriteFactory;
import rs2.media.Rasterizer3D;
import rs2.game.entity.Actor;
import rs2.media.model.Model;
import rs2.game.entity.Npc;
import rs2.game.entity.Player;
import rs2.net.Buffer;
import rs2.scene.Region;

/**
 * Owns revision-377 map-square/instance loading state and local-base shifts.
 */
public final class RegionManager {

	/** Creates a new region manager with its default client state. */
	public RegionManager() {
	}

	/** Constant value for stage unloaded. */
	public static final int STAGE_UNLOADED = 0;

	/** Constant value for stage loading. */
	public static final int STAGE_LOADING = 1;

	/** Constant value for stage loaded. */
	public static final int STAGE_LOADED = 2;

	/** Stores instance templates values. */
	public final int[][][] instanceTemplates = new int[4][13][13];

	/** Stores terrain data values. */
	public byte[][] terrainData;

	/** Stores landscape data values. */
	public byte[][] landscapeData;

	/** Stores region IDs values. */
	public int[] regionIds;

	/** Stores terrain archive IDs values. */
	public int[] terrainArchiveIds;

	/** Stores landscape archive IDs values. */
	public int[] landscapeArchiveIds;

	/** Stores the current region X. */
	public int regionX;

	/** Stores the current region Y. */
	public int regionY;

	/** Stores the current base X. */
	public int baseX;

	/** Stores the current base Y. */
	public int baseY;
	/**
	 * Whether instanced.
	 */
	public boolean instanced;
	/**
	 * Whether special region.
	 */
	public boolean specialRegion;

	/** Stores the current loading stage. */
	public int loadingStage;

	/** Stores the current loading start time. */
	public long loadingStartTime;
	/**
	 * Whether awaiting player update.
	 */
	public boolean awaitingPlayerUpdate;

	/** Stores the current previous base X. */
	private int previousBaseX;

	/** Stores the current previous base Y. */
	private int previousBaseY;

	/** Provides region shift state and behavior. */
	public static final class RegionShift {
		/**
		 * Whether changed.
		 */
		public final boolean changed;

		/** Stores the current delta X. */
		public final int deltaX;

		/** Stores the current delta Y. */
		public final int deltaY;

		/** Stores the current destination X. */
		public final int destinationX;

		/** Stores the current destination Y. */
		public final int destinationY;

		/**
		 * Creates a new region shift.
		 *
		 * @param changed the changed
		 * @param deltaX the delta X
		 * @param deltaY the delta Y
		 * @param destinationX the destination X
		 * @param destinationY the destination Y
		 */
		private RegionShift(boolean changed, int deltaX, int deltaY, int destinationX, int destinationY) {
			this.changed = changed;
			this.deltaX = deltaX;
			this.deltaY = deltaY;
			this.destinationX = destinationX;
			this.destinationY = destinationY;
		}
	}

	/**
	 * Extracted from the revision-377 incoming packet branches 222 (normal) and 53
	 * (constructed/instanced).
	 *
	 * @param buffer       the buffer
	 * @param opcode       the opcode
	 * @param fetcher      the fetcher
	 * @param actors       the actors
	 * @param world        the world
	 * @param destinationX the destination x
	 * @param destinationY the destination y
	 * @return the decoded rebuild value
	 */
	public RegionShift decodeRebuild(Buffer buffer, int opcode, OnDemandFetcher fetcher, ActorSynchronizer actors,
			WorldState world, int destinationX, int destinationY) {
		int nextRegionX = regionX;
		int nextRegionY = regionY;

		if (opcode == 222) {
			nextRegionY = buffer.readUnsignedShort();
			nextRegionX = buffer.readUnsignedShortAddLE();
			instanced = false;
		}
		if (opcode == 53) {
			nextRegionX = buffer.readUnsignedShortAdd();
			buffer.startBitAccess();
			for (int plane = 0; plane < 4; plane++) {
				for (int chunkX = 0; chunkX < 13; chunkX++) {
					for (int chunkY = 0; chunkY < 13; chunkY++) {
						if (buffer.readBits(1) == 1) {
							instanceTemplates[plane][chunkX][chunkY] = buffer.readBits(26);
						} else {
							instanceTemplates[plane][chunkX][chunkY] = -1;
						}
					}
				}
			}
			buffer.finishBitAccess();
			nextRegionY = buffer.readUnsignedShortAdd();
			instanced = true;
		}

		if (regionX == nextRegionX && regionY == nextRegionY && loadingStage == STAGE_LOADED) {
			return new RegionShift(false, 0, 0, destinationX, destinationY);
		}

		regionX = nextRegionX;
		regionY = nextRegionY;
		baseX = (regionX - 6) * 8;
		baseY = (regionY - 6) * 8;

		specialRegion = false;
		if ((regionX / 8 == 48 || regionX / 8 == 49) && regionY / 8 == 48) {
			specialRegion = true;
		}
		if (regionX / 8 == 48 && regionY / 8 == 148) {
			specialRegion = true;
		}

		loadingStage = STAGE_LOADING;
		loadingStartTime = System.currentTimeMillis();

		if (!instanced) {
			prepareNormalRegions(fetcher);
		} else {
			prepareInstancedRegions(fetcher);
		}

		int deltaX = baseX - previousBaseX;
		int deltaY = baseY - previousBaseY;
		previousBaseX = baseX;
		previousBaseY = baseY;

		shiftActors(actors, deltaX, deltaY);
		world.shiftLocalState(deltaX, deltaY);

		int shiftedDestinationX = destinationX;
		int shiftedDestinationY = destinationY;
		if (shiftedDestinationX != 0) {
			shiftedDestinationX -= deltaX;
			shiftedDestinationY -= deltaY;
		}

		awaitingPlayerUpdate = true;
		return new RegionShift(true, deltaX, deltaY, shiftedDestinationX, shiftedDestinationY);
	}

	/**
	 * Performs prepare normal regions.
	 *
	 * @param fetcher the fetcher
	 */
	private void prepareNormalRegions(OnDemandFetcher fetcher) {
		int count = 0;
		for (int mapX = (regionX - 6) / 8; mapX <= (regionX + 6) / 8; mapX++) {
			for (int mapY = (regionY - 6) / 8; mapY <= (regionY + 6) / 8; mapY++) {
				count++;
			}
		}

		terrainData = new byte[count][];
		landscapeData = new byte[count][];
		regionIds = new int[count];
		terrainArchiveIds = new int[count];
		landscapeArchiveIds = new int[count];

		int index = 0;
		for (int mapX = (regionX - 6) / 8; mapX <= (regionX + 6) / 8; mapX++) {
			for (int mapY = (regionY - 6) / 8; mapY <= (regionY + 6) / 8; mapY++) {
				regionIds[index] = (mapX << 8) + mapY;
				if (specialRegion
						&& (mapY == 49 || mapY == 149 || mapY == 147 || mapX == 50 || mapX == 49 && mapY == 47)) {
					terrainArchiveIds[index] = -1;
					landscapeArchiveIds[index] = -1;
					index++;
					continue;
				}

				terrainArchiveIds[index] = fetcher.getMapFileId(mapX, mapY, 0);
				if (terrainArchiveIds[index] != -1) {
					fetcher.request(3, terrainArchiveIds[index]);
				}
				landscapeArchiveIds[index] = fetcher.getMapFileId(mapX, mapY, 1);
				if (landscapeArchiveIds[index] != -1) {
					fetcher.request(3, landscapeArchiveIds[index]);
				}
				index++;
			}
		}
	}

	/**
	 * Performs prepare instanced regions.
	 *
	 * @param fetcher the fetcher
	 */
	private void prepareInstancedRegions(OnDemandFetcher fetcher) {
		int[] uniqueRegions = new int[676];
		int count = 0;
		for (int plane = 0; plane < 4; plane++) {
			for (int chunkX = 0; chunkX < 13; chunkX++) {
				for (int chunkY = 0; chunkY < 13; chunkY++) {
					int template = instanceTemplates[plane][chunkX][chunkY];
					if (template == -1) {
						continue;
					}
					int sourceChunkX = template >> 14 & 0x3ff;
					int sourceChunkY = template >> 3 & 0x7ff;
					int regionId = (sourceChunkX / 8 << 8) + sourceChunkY / 8;
					boolean duplicate = false;
					for (int index = 0; index < count; index++) {
						if (uniqueRegions[index] == regionId) {
							duplicate = true;
							break;
						}
					}
					if (!duplicate) {
						uniqueRegions[count++] = regionId;
					}
				}
			}
		}

		terrainData = new byte[count][];
		landscapeData = new byte[count][];
		regionIds = Arrays.copyOf(uniqueRegions, count);
		terrainArchiveIds = new int[count];
		landscapeArchiveIds = new int[count];
		for (int index = 0; index < count; index++) {
			int regionId = regionIds[index];
			int mapX = regionId >> 8 & 0xff;
			int mapY = regionId & 0xff;
			terrainArchiveIds[index] = fetcher.getMapFileId(mapX, mapY, 0);
			if (terrainArchiveIds[index] != -1) {
				fetcher.request(3, terrainArchiveIds[index]);
			}
			landscapeArchiveIds[index] = fetcher.getMapFileId(mapX, mapY, 1);
			if (landscapeArchiveIds[index] != -1) {
				fetcher.request(3, landscapeArchiveIds[index]);
			}
		}
	}

	/**
	 * Performs shift actors.
	 *
	 * @param actors the actors
	 * @param deltaX the delta x
	 * @param deltaY the delta y
	 */
	private static void shiftActors(ActorSynchronizer actors, int deltaX, int deltaY) {
		for (int index = 0; index < 16384; index++) {
			Npc npc = actors.npcs[index];
			if (npc != null) {
				shiftActor(npc, deltaX, deltaY);
			}
		}
		for (int index = 0; index < ActorSynchronizer.MAX_PLAYERS; index++) {
			Player player = actors.players[index];
			if (player != null) {
				shiftActor(player, deltaX, deltaY);
			}
		}
	}

	/**
	 * Performs shift actor.
	 *
	 * @param actor  the actor
	 * @param deltaX the delta x
	 * @param deltaY the delta y
	 */
	private static void shiftActor(Actor actor, int deltaX, int deltaY) {
		for (int pathIndex = 0; pathIndex < 10; pathIndex++) {
			actor.pathX[pathIndex] -= deltaX;
			actor.pathY[pathIndex] -= deltaY;
		}
		actor.x -= deltaX * 128;
		actor.y -= deltaY * 128;
	}

	/**
	 * Performs accept map file.
	 *
	 * @param request the request
	 */
	public void acceptMapFile(OnDemandRequest request) {
		if (request.type != 3 || loadingStage != STAGE_LOADING || regionIds == null) {
			return;
		}
		for (int index = 0; index < regionIds.length; index++) {
			if (terrainArchiveIds[index] == request.id) {
				terrainData[index] = request.buffer;
				if (request.buffer == null) {
					terrainArchiveIds[index] = -1;
				}
				return;
			}
			if (landscapeArchiveIds[index] == request.id) {
				landscapeData[index] = request.buffer;
				if (request.buffer == null) {
					landscapeArchiveIds[index] = -1;
				}
				return;
			}
		}
	}

	/**
	 * Clears value state.
	 */
	public void clear() {
		terrainData = null;
		landscapeData = null;
		regionIds = null;
		terrainArchiveIds = null;
		landscapeArchiveIds = null;
	}

	/**
	 * Performs player update received.
	 */
	public void playerUpdateReceived() {
		awaitingPlayerUpdate = false;
	}

	/**
	 * Returns the current region-loading readiness code without building the scene.
	 * @return the loading status
	 */
	public int getLoadingStatus() {
		for (int index = 0; index < terrainData.length; index++) {
			if (terrainData[index] == null && terrainArchiveIds[index] != -1) {
				return -1;
			}
			if (landscapeData[index] == null && landscapeArchiveIds[index] != -1) {
				return -2;
			}
		}

		boolean modelsReady = true;
		for (int index = 0; index < terrainData.length; index++) {
			byte[] landscape = landscapeData[index];
			if (landscape != null) {
				int x = (regionIds[index] >> 8) * 64 - baseX;
				int y = (regionIds[index] & 0xff) * 64 - baseY;
				if (instanced) {
					x = 10;
					y = 10;
				}
				modelsReady &= Region.areObjectModelsReady(landscape, x, y);
			}
		}
		if (!modelsReady) {
			return -3;
		}
		if (awaitingPlayerUpdate) {
			return -4;
		}
		return 0;
	}

	/**
	 * Builds terrain, objects, collision, and scene state for the loaded region set.
	 * @param world the world
	 * @param currentPlane the current plane
	 * @param lowMemory whether low-memory mode is active
	 * @param outgoing the outgoing
	 * @param fetcher the fetcher
	 * @param standaloneFrame the standalone frame
	 * @param rebindSceneRaster the rebind scene raster
	 */
	public void buildRegion(WorldState world, int currentPlane, boolean lowMemory, Buffer outgoing,
			OnDemandFetcher fetcher, boolean standaloneFrame, Runnable rebindSceneRaster) {
		try {
			world.graphicsObjects.clear();
			world.projectiles.clear();
			Rasterizer3D.clearTextureCache();
			clearWorldModelCaches();
			world.scene.clear();
			System.gc();
			for (int plane = 0; plane < 4; plane++) {
				world.collisionMaps[plane].reset();
			}
			for (int plane = 0; plane < 4; plane++) {
				for (int x = 0; x < 104; x++) {
					for (int y = 0; y < 104; y++) {
						world.tileFlags[plane][x][y] = 0;
					}
				}
			}

			Region region = new Region(world.tileHeights, world.tileFlags, 104, 104);
			int regionCount = terrainData.length;
			outgoing.writeOpcode(40);
			if (!instanced) {
				for (int index = 0; index < regionCount; index++) {
					int x = (regionIds[index] >> 8) * 64 - baseX;
					int y = (regionIds[index] & 0xff) * 64 - baseY;
					byte[] terrain = terrainData[index];
					if (terrain != null) {
						region.loadTerrainRegion(terrain, x, y, (regionX - 6) * 8, (regionY - 6) * 8,
								world.collisionMaps);
					}
				}
				for (int index = 0; index < regionCount; index++) {
					int x = (regionIds[index] >> 8) * 64 - baseX;
					int y = (regionIds[index] & 0xff) * 64 - baseY;
					if (terrainData[index] == null && regionY < 800) {
						region.fillMissingTerrain(x, y, 64, 64);
					}
				}

				outgoing.writeOpcode(40);
				for (int index = 0; index < regionCount; index++) {
					byte[] landscape = landscapeData[index];
					if (landscape != null) {
						int x = (regionIds[index] >> 8) * 64 - baseX;
						int y = (regionIds[index] & 0xff) * 64 - baseY;
						region.loadObjectRegion(landscape, x, y, world.collisionMaps, world.scene);
					}
				}
			}

			if (instanced) {
				for (int destinationPlane = 0; destinationPlane < 4; destinationPlane++) {
					for (int destinationChunkX = 0; destinationChunkX < 13; destinationChunkX++) {
						for (int destinationChunkY = 0; destinationChunkY < 13; destinationChunkY++) {
							boolean loaded = false;
							int template = instanceTemplates[destinationPlane][destinationChunkX][destinationChunkY];
							if (template != -1) {
								int sourcePlane = template >> 24 & 3;
								int rotation = template >> 1 & 3;
								int sourceChunkX = template >> 14 & 0x3ff;
								int sourceChunkY = template >> 3 & 0x7ff;
								int regionId = (sourceChunkX / 8 << 8) + sourceChunkY / 8;
								for (int index = 0; index < regionIds.length; index++) {
									if (regionIds[index] == regionId && terrainData[index] != null) {
										region.loadTerrainChunk(terrainData[index], sourcePlane, (sourceChunkX & 7) * 8,
												(sourceChunkY & 7) * 8, destinationPlane, destinationChunkX * 8,
												destinationChunkY * 8, rotation, world.collisionMaps);
										loaded = true;
										break;
									}
								}
							}
							if (!loaded) {
								region.clearChunkHeights(destinationPlane, destinationChunkX * 8,
										destinationChunkY * 8);
							}
						}
					}
				}

				for (int chunkX = 0; chunkX < 13; chunkX++) {
					for (int chunkY = 0; chunkY < 13; chunkY++) {
						if (instanceTemplates[0][chunkX][chunkY] == -1) {
							region.fillMissingTerrain(chunkX * 8, chunkY * 8, 8, 8);
						}
					}
				}

				outgoing.writeOpcode(40);
				for (int destinationPlane = 0; destinationPlane < 4; destinationPlane++) {
					for (int destinationChunkX = 0; destinationChunkX < 13; destinationChunkX++) {
						for (int destinationChunkY = 0; destinationChunkY < 13; destinationChunkY++) {
							int template = instanceTemplates[destinationPlane][destinationChunkX][destinationChunkY];
							if (template == -1) {
								continue;
							}
							int sourcePlane = template >> 24 & 3;
							int rotation = template >> 1 & 3;
							int sourceChunkX = template >> 14 & 0x3ff;
							int sourceChunkY = template >> 3 & 0x7ff;
							int regionId = (sourceChunkX / 8 << 8) + sourceChunkY / 8;
							for (int index = 0; index < regionIds.length; index++) {
								if (regionIds[index] == regionId && landscapeData[index] != null) {
									region.loadObjectChunk(landscapeData[index], sourcePlane, (sourceChunkX & 7) * 8,
											(sourceChunkY & 7) * 8, destinationPlane, destinationChunkX * 8,
											destinationChunkY * 8, rotation, world.collisionMaps, world.scene);
									break;
								}
							}
						}
					}
				}
			}

			outgoing.writeOpcode(40);
			region.buildScene(world.collisionMaps, world.scene);
			if (rebindSceneRaster != null) {
				rebindSceneRaster.run();
			}
			outgoing.writeOpcode(40);

			// Legacy code computed a current-plane-clamped minimum into an unused local.
			int unusedClampedMinimumPlane = Region.minimumPlane;
			if (unusedClampedMinimumPlane > currentPlane) {
				unusedClampedMinimumPlane = currentPlane;
			}
			if (unusedClampedMinimumPlane < currentPlane - 1) {
				unusedClampedMinimumPlane = currentPlane - 1;
			}

			if (lowMemory) {
				world.scene.setMinPlane(Region.minimumPlane);
			} else {
				world.scene.setMinPlane(0);
			}
			for (int x = 0; x < 104; x++) {
				for (int y = 0; y < 104; y++) {
					world.updateGroundItemPile(currentPlane, x, y);
				}
			}
			world.resetPendingSpawnsAfterRegionBuild();
		} catch (Exception ignored) {
		}

		GameObjectDefinition.clearModelCaches();
		if (standaloneFrame) {
			outgoing.writeOpcode(78);
			outgoing.writeInt(0x3f008edd);
		}
		if (lowMemory && rs2.sign.Signlink.cacheData != null) {
			int modelCount = fetcher.getFileCount(0);
			for (int modelId = 0; modelId < modelCount; modelId++) {
				int modelIndex = fetcher.getModelIndex(modelId);
				if ((modelIndex & 0x79) == 0) {
					Model.clearModelHeader(modelId);
				}
			}
		}
		System.gc();
		Rasterizer3D.initializeTexturePool(20);
		fetcher.clearExtraRequests();
		queueBorderRegions(fetcher);
	}

	/**
	 * Clears world model caches state.
	 */
	private static void clearWorldModelCaches() {
		GameObjectDefinition.clearModelCaches();
		NpcDefinition.modelCache.clear();
		ItemDefinition.modelCache.clear();
		ItemSpriteFactory.clearCache();
		Player.modelCache.clear();
		SpotAnimation.modelCache.clear();
	}

	/**
	 * Performs queue border regions.
	 *
	 * @param fetcher the fetcher
	 */
	private void queueBorderRegions(OnDemandFetcher fetcher) {
		int minMapX = (regionX - 6) / 8 - 1;
		int maxMapX = (regionX + 6) / 8 + 1;
		int minMapY = (regionY - 6) / 8 - 1;
		int maxMapY = (regionY + 6) / 8 + 1;
		if (specialRegion) {
			minMapX = 49;
			maxMapX = 50;
			minMapY = 49;
			maxMapY = 50;
		}
		for (int mapX = minMapX; mapX <= maxMapX; mapX++) {
			for (int mapY = minMapY; mapY <= maxMapY; mapY++) {
				if (mapX == minMapX || mapX == maxMapX || mapY == minMapY || mapY == maxMapY) {
					int terrainId = fetcher.getMapFileId(mapX, mapY, 0);
					if (terrainId != -1) {
						fetcher.queueExtraRequest(3, terrainId);
					}
					int landscapeId = fetcher.getMapFileId(mapX, mapY, 1);
					if (landscapeId != -1) {
						fetcher.queueExtraRequest(3, landscapeId);
					}
				}
			}
		}
	}
}
