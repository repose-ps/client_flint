package rs2.game;

import rs2.cache.def.NpcDefinition;
import rs2.cache.media.AnimationSequence;
import rs2.chat.ChatCodec;
import rs2.chat.Censor;
import rs2.media.renderable.Actor;
import rs2.media.renderable.Npc;
import rs2.media.renderable.Player;
import rs2.net.Buffer;
import rs2.sign.Signlink;
import rs2.text.Base37;

/**
 * Owns the revision-377 player/NPC registries and decodes their bit-packed
 * synchronization packets and update masks.
 *
 * <p>
 * The client still decides which incoming opcode represents a player or NPC
 * synchronization packet. Once selected, the complete packet is handed here so
 * entity-list bookkeeping, bit access, removals, additions, appearance caches
 * and update-mask order remain one coherent subsystem.
 * </p>
 */
public final class ActorSynchronizer {

	public static final int MAX_PLAYERS = 2048;
	public static final int LOCAL_PLAYER_INDEX = 2047;
	public static final int MAX_NPCS = 16384;

	public final Player[] players = new Player[MAX_PLAYERS];
	public int playerCount;
	public final int[] playerIndices = new int[MAX_PLAYERS];
	public final Buffer[] playerAppearanceBuffers = new Buffer[MAX_PLAYERS];

	public final Npc[] npcs = new Npc[MAX_NPCS];
	public int npcCount;
	public final int[] npcIndices = new int[MAX_NPCS];

	private int updateCount;
	private final int[] updateIndices = new int[MAX_PLAYERS];
	private int removedCount;
	private final int[] removedIndices = new int[1000];

	public Player localPlayer;

	/**
	 * Callback boundary for the chat/social behavior embedded in player update
	 * masks.
	 */
	public interface ChatHandler {
		boolean isIgnored(long encodedName);

		boolean isChatSuppressed();

		void addChatMessage(String sender, String message, int type);
	}

	public ActorSynchronizer() {
	}

	/**
	 * Clears the runtime registries and recreates the local player in slot 2047.
	 */
	public Player reset() {
		playerCount = 0;
		npcCount = 0;
		updateCount = 0;
		removedCount = 0;
		for (int index = 0; index < MAX_PLAYERS; index++) {
			players[index] = null;
			playerAppearanceBuffers[index] = null;
		}
		for (int index = 0; index < MAX_NPCS; index++) {
			npcs[index] = null;
		}
		localPlayer = new Player();
		players[LOCAL_PLAYER_INDEX] = localPlayer;
		return localPlayer;
	}

	/**
	 * Decodes one complete player synchronization packet.
	 *
	 * <p>
	 * Legacy client entry point:
	 * {@code method96(int packetSize, int divideSentinel, Buffer buffer)}. The
	 * divide sentinel is removed; its valid caller supplied 69.
	 * </p>
	 *
	 * @return the plane selected by the local-player movement block
	 */
	public int decodePlayerUpdate(Buffer buffer, int packetSize, int cycle, int currentPlane, String username,
			Buffer chatScratch, ChatHandler chatHandler) {
		removedCount = 0;
		updateCount = 0;

		int plane = decodeLocalPlayerMovement(buffer, currentPlane);
		decodeExistingPlayers(buffer, cycle, username);
		decodeNewPlayers(buffer, packetSize, cycle);
		decodePlayerMasks(buffer, cycle, chatScratch, chatHandler);

		for (int index = 0; index < removedCount; index++) {
			int playerIndex = removedIndices[index];
			if (players[playerIndex].lastUpdateCycle != cycle) {
				players[playerIndex] = null;
			}
		}

		if (buffer.position != packetSize) {
			Signlink.reportError(
					"Error packet size mismatch in getplayer pos:" + buffer.position + " psize:" + packetSize);
			throw new RuntimeException("eek");
		}
		for (int index = 0; index < playerCount; index++) {
			if (players[playerIndices[index]] == null) {
				Signlink.reportError(username + " null entry in pl list - pos:" + index + " size:" + playerCount);
				throw new RuntimeException("eek");
			}
		}
		return plane;
	}

	/**
	 * Decodes one complete NPC synchronization packet.
	 *
	 * <p>
	 * Legacy client entry point:
	 * {@code method48(Buffer buffer, boolean unused, int packetSize)}. The boolean
	 * is removed because it was never read.
	 * </p>
	 */
	public void decodeNpcUpdate(Buffer buffer, int packetSize, int cycle, String username) {
		removedCount = 0;
		updateCount = 0;

		decodeExistingNpcs(buffer, cycle, username);
		decodeNewNpcs(buffer, packetSize, cycle);
		decodeNpcMasks(buffer, cycle);

		for (int index = 0; index < removedCount; index++) {
			int npcIndex = removedIndices[index];
			if (npcs[npcIndex].lastUpdateCycle != cycle) {
				npcs[npcIndex].definition = null;
				npcs[npcIndex] = null;
			}
		}

		if (buffer.position != packetSize) {
			Signlink.reportError(
					username + " size mismatch in getnpcpos - pos:" + buffer.position + " psize:" + packetSize);
			throw new RuntimeException("eek");
		}
		for (int index = 0; index < npcCount; index++) {
			if (npcs[npcIndices[index]] == null) {
				Signlink.reportError(username + " null entry in npc list - pos:" + index + " size:" + npcCount);
				throw new RuntimeException("eek");
			}
		}
	}

	public void updatePlayers(ActorUpdater updater, int cycle, int localPlayerServerIndex, int regionBaseX,
			int regionBaseY) {
		for (int index = -1; index < playerCount; index++) {
			int playerIndex = index == -1 ? LOCAL_PLAYER_INDEX : playerIndices[index];
			Player player = players[playerIndex];
			if (player != null) {
				updater.update(player, cycle, localPlayer, players, npcs, localPlayerServerIndex, LOCAL_PLAYER_INDEX,
						regionBaseX, regionBaseY);
			}
		}
	}

	public void updateNpcs(ActorUpdater updater, int cycle, int localPlayerServerIndex, int regionBaseX,
			int regionBaseY) {
		for (int index = 0; index < npcCount; index++) {
			Npc npc = npcs[npcIndices[index]];
			if (npc != null) {
				updater.update(npc, cycle, localPlayer, players, npcs, localPlayerServerIndex, LOCAL_PLAYER_INDEX,
						regionBaseX, regionBaseY);
			}
		}
	}

	/**
	 * Legacy {@code method41(int unused, boolean unusedFlag, Buffer buffer)}. Both
	 * non-buffer parameters are removed. Bit access is intentionally left open for
	 * the existing-player and new-player blocks.
	 */
	private int decodeLocalPlayerMovement(Buffer buffer, int currentPlane) {
		buffer.startBitAccess();
		int hasUpdate = buffer.readBits(1);
		if (hasUpdate == 0) {
			return currentPlane;
		}

		int movementType = buffer.readBits(2);
		if (movementType == 0) {
			updateIndices[updateCount++] = LOCAL_PLAYER_INDEX;
			return currentPlane;
		}
		if (movementType == 1) {
			localPlayer.moveInDirection(false, buffer.readBits(3));
			if (buffer.readBits(1) == 1) {
				updateIndices[updateCount++] = LOCAL_PLAYER_INDEX;
			}
			return currentPlane;
		}
		if (movementType == 2) {
			localPlayer.moveInDirection(true, buffer.readBits(3));
			localPlayer.moveInDirection(true, buffer.readBits(3));
			if (buffer.readBits(1) == 1) {
				updateIndices[updateCount++] = LOCAL_PLAYER_INDEX;
			}
			return currentPlane;
		}

		boolean teleport = buffer.readBits(1) == 1;
		int plane = buffer.readBits(2);
		int tileY = buffer.readBits(7);
		int tileX = buffer.readBits(7);
		if (buffer.readBits(1) == 1) {
			updateIndices[updateCount++] = LOCAL_PLAYER_INDEX;
		}
		localPlayer.setPosition(tileX, tileY, teleport);
		return plane;
	}

	/**
	 * Legacy {@code method114(int unused, int negativeSentinel, Buffer buffer)}.
	 * The first integer was unused; the valid negative sentinel only avoided
	 * resetting the incoming opcode and is removed from this subsystem API.
	 */
	private void decodeExistingPlayers(Buffer buffer, int cycle, String username) {
		int count = buffer.readBits(8);
		if (count < playerCount) {
			for (int index = count; index < playerCount; index++) {
				removedIndices[removedCount++] = playerIndices[index];
			}
		}
		if (count > playerCount) {
			Signlink.reportError(username + " Too many players");
			throw new RuntimeException("eek");
		}

		playerCount = 0;
		for (int index = 0; index < count; index++) {
			int playerIndex = playerIndices[index];
			Player player = players[playerIndex];
			if (buffer.readBits(1) == 0) {
				playerIndices[playerCount++] = playerIndex;
				player.lastUpdateCycle = cycle;
				continue;
			}

			int updateType = buffer.readBits(2);
			if (updateType == 0) {
				playerIndices[playerCount++] = playerIndex;
				player.lastUpdateCycle = cycle;
				updateIndices[updateCount++] = playerIndex;
			} else if (updateType == 1) {
				playerIndices[playerCount++] = playerIndex;
				player.lastUpdateCycle = cycle;
				player.moveInDirection(false, buffer.readBits(3));
				if (buffer.readBits(1) == 1) {
					updateIndices[updateCount++] = playerIndex;
				}
			} else if (updateType == 2) {
				playerIndices[playerCount++] = playerIndex;
				player.lastUpdateCycle = cycle;
				player.moveInDirection(true, buffer.readBits(3));
				player.moveInDirection(true, buffer.readBits(3));
				if (buffer.readBits(1) == 1) {
					updateIndices[updateCount++] = playerIndex;
				}
			} else {
				removedIndices[removedCount++] = playerIndex;
			}
		}
	}

	/**
	 * Legacy {@code method16(int packetSize, byte sentinel, Buffer buffer)}. The
	 * valid sentinel was 6. Finishes bit access before update masks are decoded.
	 */
	private void decodeNewPlayers(Buffer buffer, int packetSize, int cycle) {
		while (buffer.bitPosition + 10 < packetSize * 8) {
			int playerIndex = buffer.readBits(11);
			if (playerIndex == 2047) {
				break;
			}
			if (players[playerIndex] == null) {
				players[playerIndex] = new Player();
				if (playerAppearanceBuffers[playerIndex] != null) {
					players[playerIndex].updateAppearance(playerAppearanceBuffers[playerIndex]);
				}
			}
			playerIndices[playerCount++] = playerIndex;
			Player player = players[playerIndex];
			player.lastUpdateCycle = cycle;

			int deltaX = buffer.readBits(5);
			if (deltaX > 15) {
				deltaX -= 32;
			}
			if (buffer.readBits(1) == 1) {
				updateIndices[updateCount++] = playerIndex;
			}
			boolean teleport = buffer.readBits(1) == 1;
			int deltaY = buffer.readBits(5);
			if (deltaY > 15) {
				deltaY -= 32;
			}
			player.setPosition(localPlayer.pathX[0] + deltaX, localPlayer.pathY[0] + deltaY, teleport);
		}
		buffer.finishBitAccess();
	}

	/**
	 * Legacy {@code method40(int divideSentinel, Buffer buffer, int unused)}. Only
	 * the buffer is meaningful; the valid caller supplied 808 for the division
	 * argument.
	 */
	private void decodePlayerMasks(Buffer buffer, int cycle, Buffer chatScratch, ChatHandler chatHandler) {
		for (int index = 0; index < updateCount; index++) {
			int playerIndex = updateIndices[index];
			Player player = players[playerIndex];
			int mask = buffer.readUnsignedByte();
			if ((mask & 0x20) != 0) {
				mask += buffer.readUnsignedByte() << 8;
			}
			decodePlayerMask(buffer, cycle, playerIndex, player, mask, chatScratch, chatHandler);
		}
	}

	/**
	 * Legacy
	 * {@code method63(int sentinel, int playerIndex, Player player, int mask, Buffer buffer)}.
	 * The required sentinel value 2 is removed; meaningful parameters are named
	 * explicitly.
	 */
	private void decodePlayerMask(Buffer buffer, int cycle, int playerIndex, Player player, int mask,
			Buffer chatScratch, ChatHandler chatHandler) {
		if ((mask & 8) != 0) {
			int sequence = buffer.readUnsignedShort();
			if (sequence == 65535) {
				sequence = -1;
			}
			applySequence(player, sequence, buffer.readUnsignedByteSub());
		}
		if ((mask & 0x10) != 0) {
			player.overheadText = buffer.readString();
			if (player.overheadText.charAt(0) == '~') {
				player.overheadText = player.overheadText.substring(1);
				chatHandler.addChatMessage(player.name, player.overheadText, 2);
			} else if (player == localPlayer) {
				chatHandler.addChatMessage(player.name, player.overheadText, 2);
			}
			player.overheadTextColor = 0;
			player.overheadTextEffect = 0;
			player.overheadTextCyclesRemaining = 150;
		}
		if ((mask & 0x100) != 0) {
			player.forceMoveStartX = buffer.readUnsignedByteAdd();
			player.forceMoveStartY = buffer.readUnsignedByteNeg();
			player.forceMoveEndX = buffer.readUnsignedByteSub();
			player.forceMoveEndY = buffer.readUnsignedByte();
			player.forceMoveStartCycle = buffer.readUnsignedShort() + cycle;
			player.forceMoveEndCycle = buffer.readUnsignedShortAdd() + cycle;
			player.forceMoveDirection = buffer.readUnsignedByte();
			player.resetPath();
		}
		if ((mask & 1) != 0) {
			player.targetIndex = buffer.readUnsignedShortAdd();
			if (player.targetIndex == 65535) {
				player.targetIndex = -1;
			}
		}
		if ((mask & 2) != 0) {
			player.faceX = buffer.readUnsignedShort();
			player.faceY = buffer.readUnsignedShort();
		}
		if ((mask & 0x200) != 0) {
			player.spotAnimation = buffer.readUnsignedShortAdd();
			int packed = buffer.readIntME();
			player.spotAnimationHeight = packed >> 16;
			player.spotAnimationStartCycle = cycle + (packed & 0xffff);
			player.spotAnimationFrame = 0;
			player.spotAnimationFrameCycle = 0;
			if (player.spotAnimationStartCycle > cycle) {
				player.spotAnimationFrame = -1;
			}
			if (player.spotAnimation == 65535) {
				player.spotAnimation = -1;
			}
		}
		if ((mask & 4) != 0) {
			int length = buffer.readUnsignedByte();
			byte[] appearance = new byte[length];
			Buffer appearanceBuffer = new Buffer(appearance);
			buffer.readBytesReverse(appearance, 0, length);
			playerAppearanceBuffers[playerIndex] = appearanceBuffer;
			player.updateAppearance(appearanceBuffer);
		}
		if ((mask & 0x400) != 0) {
			int damage = buffer.readUnsignedByteAdd();
			int type = buffer.readUnsignedByteSub();
			player.addHit(cycle, damage, type);
			player.healthBarCycle = cycle + 300;
			player.currentHealth = buffer.readUnsignedByteNeg();
			player.maxHealth = buffer.readUnsignedByte();
		}
		if ((mask & 0x40) != 0) {
			int textInfo = buffer.readUnsignedShort();
			int rights = buffer.readUnsignedByteNeg();
			int length = buffer.readUnsignedByteAdd();
			int messageStart = buffer.position;
			if (player.name != null && player.visible) {
				long encodedName = Base37.encode(player.name);
				boolean ignored = false;
				if (rights <= 1) {
					ignored = chatHandler.isIgnored(encodedName);
				}
				if (!ignored && !chatHandler.isChatSuppressed()) {
					try {
						chatScratch.position = 0;
						buffer.readBytesAdd(chatScratch.payload, 0, length);
						chatScratch.position = 0;
						String text = ChatCodec.decode(chatScratch, length);
						text = Censor.censor(text);
						player.overheadText = text;
						player.overheadTextColor = textInfo >> 8;
						player.overheadTextEffect = textInfo & 0xff;
						player.overheadTextCyclesRemaining = 150;
						if (rights == 2 || rights == 3) {
							chatHandler.addChatMessage("@cr2@" + player.name, text, 1);
						} else if (rights == 1) {
							chatHandler.addChatMessage("@cr1@" + player.name, text, 1);
						} else {
							chatHandler.addChatMessage(player.name, text, 2);
						}
					} catch (Exception exception) {
						Signlink.reportError("cde2");
					}
				}
			}
			buffer.position = messageStart + length;
		}
		if ((mask & 0x80) != 0) {
			int damage = buffer.readUnsignedByteSub();
			int type = buffer.readUnsignedByteNeg();
			player.addHit(cycle, damage, type);
			player.healthBarCycle = cycle + 300;
			player.currentHealth = buffer.readUnsignedByteSub();
			player.maxHealth = buffer.readUnsignedByte();
		}
	}

	/**
	 * Legacy {@code method46(int unused, byte sentinel, Buffer buffer)}. The first
	 * integer was unused and the valid byte was -58. Bit access remains open for
	 * new-NPC additions.
	 */
	private void decodeExistingNpcs(Buffer buffer, int cycle, String username) {
		buffer.startBitAccess();
		int count = buffer.readBits(8);
		if (count < npcCount) {
			for (int index = count; index < npcCount; index++) {
				removedIndices[removedCount++] = npcIndices[index];
			}
		}
		if (count > npcCount) {
			Signlink.reportError(username + " Too many npcs");
			throw new RuntimeException("eek");
		}

		npcCount = 0;
		for (int index = 0; index < count; index++) {
			int npcIndex = npcIndices[index];
			Npc npc = npcs[npcIndex];
			if (buffer.readBits(1) == 0) {
				npcIndices[npcCount++] = npcIndex;
				npc.lastUpdateCycle = cycle;
				continue;
			}

			int updateType = buffer.readBits(2);
			if (updateType == 0) {
				npcIndices[npcCount++] = npcIndex;
				npc.lastUpdateCycle = cycle;
				updateIndices[updateCount++] = npcIndex;
			} else if (updateType == 1) {
				npcIndices[npcCount++] = npcIndex;
				npc.lastUpdateCycle = cycle;
				npc.moveInDirection(false, buffer.readBits(3));
				if (buffer.readBits(1) == 1) {
					updateIndices[updateCount++] = npcIndex;
				}
			} else if (updateType == 2) {
				npcIndices[npcCount++] = npcIndex;
				npc.lastUpdateCycle = cycle;
				npc.moveInDirection(true, buffer.readBits(3));
				npc.moveInDirection(true, buffer.readBits(3));
				if (buffer.readBits(1) == 1) {
					updateIndices[updateCount++] = npcIndex;
				}
			} else {
				removedIndices[removedCount++] = npcIndex;
			}
		}
	}

	/**
	 * Legacy {@code method132(Buffer buffer, int packetSize)}. Meaningful parameter
	 * order is unchanged.
	 */
	private void decodeNewNpcs(Buffer buffer, int packetSize, int cycle) {
		while (buffer.bitPosition + 21 < packetSize * 8) {
			int npcIndex = buffer.readBits(14);
			if (npcIndex == 16383) {
				break;
			}
			if (npcs[npcIndex] == null) {
				npcs[npcIndex] = new Npc();
			}
			Npc npc = npcs[npcIndex];
			npcIndices[npcCount++] = npcIndex;
			npc.lastUpdateCycle = cycle;
			if (buffer.readBits(1) == 1) {
				updateIndices[updateCount++] = npcIndex;
			}

			int deltaY = buffer.readBits(5);
			if (deltaY > 15) {
				deltaY -= 32;
			}
			int deltaX = buffer.readBits(5);
			if (deltaX > 15) {
				deltaX -= 32;
			}
			boolean teleport = buffer.readBits(1) == 1;
			applyNpcDefinition(npc, NpcDefinition.lookup(buffer.readBits(13)));
			npc.setPosition(localPlayer.pathX[0] + deltaX, localPlayer.pathY[0] + deltaY, teleport);
		}
		buffer.finishBitAccess();
	}

	/**
	 * Legacy {@code method62(Buffer buffer, int unused, int divideSentinel)}. Only
	 * the buffer is meaningful; the valid caller supplied 838 for the division
	 * argument.
	 */
	private void decodeNpcMasks(Buffer buffer, int cycle) {
		for (int index = 0; index < updateCount; index++) {
			Npc npc = npcs[updateIndices[index]];
			int mask = buffer.readUnsignedByte();
			if ((mask & 1) != 0) {
				applyNpcDefinition(npc, NpcDefinition.lookup(buffer.readUnsignedShortAdd()));
			}
			if ((mask & 0x40) != 0) {
				npc.targetIndex = buffer.readUnsignedShortLE();
				if (npc.targetIndex == 65535) {
					npc.targetIndex = -1;
				}
			}
			if ((mask & 0x80) != 0) {
				int damage = buffer.readUnsignedByteAdd();
				int type = buffer.readUnsignedByteAdd();
				npc.addHit(cycle, damage, type);
				npc.healthBarCycle = cycle + 300;
				npc.currentHealth = buffer.readUnsignedByte();
				npc.maxHealth = buffer.readUnsignedByteSub();
			}
			if ((mask & 4) != 0) {
				npc.spotAnimation = buffer.readUnsignedShort();
				int packed = buffer.readIntME();
				npc.spotAnimationHeight = packed >> 16;
				npc.spotAnimationStartCycle = cycle + (packed & 0xffff);
				npc.spotAnimationFrame = 0;
				npc.spotAnimationFrameCycle = 0;
				if (npc.spotAnimationStartCycle > cycle) {
					npc.spotAnimationFrame = -1;
				}
				if (npc.spotAnimation == 65535) {
					npc.spotAnimation = -1;
				}
			}
			if ((mask & 0x20) != 0) {
				npc.overheadText = buffer.readString();
				npc.overheadTextCyclesRemaining = 100;
			}
			if ((mask & 8) != 0) {
				npc.faceX = buffer.readUnsignedShortAddLE();
				npc.faceY = buffer.readUnsignedShortLE();
			}
			if ((mask & 2) != 0) {
				int sequence = buffer.readUnsignedShort();
				if (sequence == 65535) {
					sequence = -1;
				}
				applySequence(npc, sequence, buffer.readUnsignedByteSub());
			}
			if ((mask & 0x10) != 0) {
				int damage = buffer.readUnsignedByteSub();
				int type = buffer.readUnsignedByteSub();
				npc.addHit(cycle, damage, type);
				npc.healthBarCycle = cycle + 300;
				npc.currentHealth = buffer.readUnsignedByte();
				npc.maxHealth = buffer.readUnsignedByteNeg();
			}
		}
	}

	private static void applyNpcDefinition(Npc npc, NpcDefinition definition) {
		npc.definition = definition;
		npc.size = definition.size;
		npc.turnSpeed = definition.turnSpeed;
		npc.walkSequence = definition.walkSequence;
		npc.walkBackSequence = definition.walkBackSequence;
		npc.walkRightSequence = definition.turn90CwSequence;
		npc.walkLeftSequence = definition.turn90CcwSequence;
		npc.idleSequence = definition.idleSequence;
	}

	private static void applySequence(Actor actor, int sequence, int delay) {
		if (sequence == actor.sequence && sequence != -1) {
			int replayMode = AnimationSequence.sequences[sequence].replayMode;
			if (replayMode == 1) {
				actor.sequenceFrame = 0;
				actor.sequenceFrameCycle = 0;
				actor.sequenceDelay = delay;
				actor.sequenceLoopCount = 0;
			}
			if (replayMode == 2) {
				actor.sequenceLoopCount = 0;
			}
		} else if (sequence == -1 || actor.sequence == -1
				|| AnimationSequence.sequences[sequence].forcedPriority >= AnimationSequence.sequences[actor.sequence].forcedPriority) {
			actor.sequence = sequence;
			actor.sequenceFrame = 0;
			actor.sequenceFrameCycle = 0;
			actor.sequenceDelay = delay;
			actor.sequenceLoopCount = 0;
			actor.sequencePathLength = actor.pathLength;
		}
	}
}