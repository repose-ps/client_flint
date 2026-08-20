package rs2.scene.util;

/**
 * Two-dimensional collision flags used for movement and projectile routing.
 *
 * <p>
 * Each tile stores directional wall flags, occupant flags, and higher-order
 * state such as unloaded or floor-decoration blocking. Wall updates are
 * mirrored onto the adjacent tile so route checks can be performed from either
 * side of the boundary.
 * </p>
 */
public class CollisionMap {

	/** World-to-local X translation applied by public coordinate methods. */
	public int insetX;

	/** World-to-local Y translation applied by public coordinate methods. */
	public int insetY;

	/** Number of tiles along the local X axis. */
	public int width;

	/** Number of tiles along the local Y axis. */
	public int height;

	/** Collision bitfield indexed as {@code flags[x][y]}. */
	public int[][] flags;

	public static final int BLOCK_NORTH_WEST = 0x1;
	public static final int BLOCK_NORTH = 0x2;
	public static final int BLOCK_NORTH_EAST = 0x4;
	public static final int BLOCK_EAST = 0x8;
	public static final int BLOCK_SOUTH_EAST = 0x10;
	public static final int BLOCK_SOUTH = 0x20;
	public static final int BLOCK_SOUTH_WEST = 0x40;
	public static final int BLOCK_WEST = 0x80;
	public static final int BLOCK_OBJECT = 0x100;

	public static final int BLOCK_PROJECTILE_NORTH_WEST = 0x200;
	public static final int BLOCK_PROJECTILE_NORTH = 0x400;
	public static final int BLOCK_PROJECTILE_NORTH_EAST = 0x800;
	public static final int BLOCK_PROJECTILE_EAST = 0x1000;
	public static final int BLOCK_PROJECTILE_SOUTH_EAST = 0x2000;
	public static final int BLOCK_PROJECTILE_SOUTH = 0x4000;
	public static final int BLOCK_PROJECTILE_SOUTH_WEST = 0x8000;
	public static final int BLOCK_PROJECTILE_WEST = 0x10000;
	public static final int BLOCK_PROJECTILE_OBJECT = 0x20000;

	public static final int BLOCK_FLOOR_DECORATION = 0x200000;
	public static final int UNLOADED = 0x1000000;

	private static final int BORDER_BLOCKED = 0x00ffffff;
	private static final int PROJECTILE_FLAG_SHIFT = 9;
	private static final int ACCESS_FROM_WEST_BLOCKED = 0x1280108;
	private static final int ACCESS_FROM_EAST_BLOCKED = 0x1280180;
	private static final int ACCESS_FROM_SOUTH_BLOCKED = 0x1280102;
	private static final int ACCESS_FROM_NORTH_BLOCKED = 0x1280120;

	/**
	 * Creates a collision map and initializes its border as impassable.
	 *
	 * <p>
	 * The parameter order remains {@code (height, width)} to preserve the original
	 * client's constructor contract.
	 * </p>
	 */
	public CollisionMap(int height, int width) {
		this.insetX = 0;
		this.insetY = 0;
		this.width = width;
		this.height = height;
		this.flags = new int[width][height];
		reset();
	}

	/** Resets border tiles to fully blocked and interior tiles to unloaded. */
	public void reset() {
		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				if (x == 0 || y == 0 || x == width - 1 || y == height - 1) {
					flags[x][y] = BORDER_BLOCKED;
				} else {
					flags[x][y] = UNLOADED;
				}
			}
		}
	}

	/**
	 * Adds collision for a wall and the matching boundary on its neighbour.
	 *
	 * @param x                 world or scene X coordinate
	 * @param y                 world or scene Y coordinate
	 * @param type              wall shape: {@code 0} straight, {@code 1} or
	 *                          {@code 3} diagonal, and {@code 2} corner
	 * @param orientation       wall orientation in the range {@code 0-3}
	 * @param blocksProjectiles whether to add the parallel projectile flags
	 */
	public void markWall(int x, int y, int type, int orientation, boolean blocksProjectiles) {
		x -= insetX;
		y -= insetY;
		updateWallFlags(x, y, type, orientation, 0, true);
		if (blocksProjectiles) {
			updateWallFlags(x, y, type, orientation, PROJECTILE_FLAG_SHIFT, true);
		}
	}

	/**
	 * Marks every tile occupied by a rectangular object.
	 *
	 * <p>
	 * Width and height are exchanged for odd orientations because the object's
	 * footprint has been rotated by a quarter turn.
	 * </p>
	 */
	public void markSolidOccupant(int x, int y, int sizeX, int sizeY, int orientation, boolean blocksProjectiles) {
		int occupiedFlag = BLOCK_OBJECT;
		if (blocksProjectiles) {
			occupiedFlag |= BLOCK_PROJECTILE_OBJECT;
		}

		x -= insetX;
		y -= insetY;
		if (orientation == 1 || orientation == 3) {
			int originalSizeX = sizeX;
			sizeX = sizeY;
			sizeY = originalSizeX;
		}

		updateRectangle(x, y, sizeX, sizeY, occupiedFlag, true);
	}

	/** Marks a tile as blocked by a floor decoration. */
	public void markBlocked(int x, int y) {
		x -= insetX;
		y -= insetY;
		addFlag(x, y, BLOCK_FLOOR_DECORATION);
	}

	/** Adds one or more collision bits to a local tile. */
	public void addFlag(int x, int y, int flag) {
		flags[x][y] |= flag;
	}

	/** Removes collision for a wall and its mirrored neighbouring boundary. */
	public void unmarkWall(int x, int y, int type, int orientation, boolean blocksProjectiles) {
		x -= insetX;
		y -= insetY;
		updateWallFlags(x, y, type, orientation, 0, false);
		if (blocksProjectiles) {
			updateWallFlags(x, y, type, orientation, PROJECTILE_FLAG_SHIFT, false);
		}
	}

	/** Removes the occupant flags from a rotated rectangular footprint. */
	public void unmarkSolidOccupant(int x, int y, int sizeX, int sizeY, int orientation, boolean blocksProjectiles) {
		int occupiedFlag = BLOCK_OBJECT;
		if (blocksProjectiles) {
			occupiedFlag |= BLOCK_PROJECTILE_OBJECT;
		}

		x -= insetX;
		y -= insetY;
		if (orientation == 1 || orientation == 3) {
			int originalSizeX = sizeX;
			sizeX = sizeY;
			sizeY = originalSizeX;
		}

		updateRectangle(x, y, sizeX, sizeY, occupiedFlag, false);
	}

	/**
	 * Removes collision bits from a local tile.
	 *
	 * <p>
	 * The 24-bit mask intentionally also clears {@link #UNLOADED}, matching the
	 * original map-loading behavior.
	 * </p>
	 */
	public void removeFlag(int x, int y, int flag) {
		flags[x][y] &= BORDER_BLOCKED - flag;
	}

	/** Removes the floor-decoration blocking flag from a tile. */
	public void unmarkBlocked(int x, int y) {
		x -= insetX;
		y -= insetY;
		flags[x][y] &= 0x00dfffff;
	}

	/**
	 * Tests whether a mover has reached an interactable wall position.
	 *
	 * @param currentX    mover X coordinate
	 * @param currentY    mover Y coordinate
	 * @param goalX       wall X coordinate
	 * @param goalY       wall Y coordinate
	 * @param wallType    wall shape, normally {@code 0}, {@code 2}, or {@code 9}
	 * @param orientation wall orientation in the range {@code 0-3}
	 */
	public boolean reachedWall(int currentX, int currentY, int goalX, int goalY, int wallType, int orientation) {
		if (currentX == goalX && currentY == goalY) {
			return true;
		}

		currentX -= insetX;
		currentY -= insetY;
		goalX -= insetX;
		goalY -= insetY;

		if (wallType == 0) {
			if (orientation == 0) {
				if (currentX == goalX - 1 && currentY == goalY) {
					return true;
				}
				if (currentX == goalX && currentY == goalY + 1
						&& (flags[currentX][currentY] & ACCESS_FROM_NORTH_BLOCKED) == 0) {
					return true;
				}
				if (currentX == goalX && currentY == goalY - 1
						&& (flags[currentX][currentY] & ACCESS_FROM_SOUTH_BLOCKED) == 0) {
					return true;
				}
			} else if (orientation == 1) {
				if (currentX == goalX && currentY == goalY + 1) {
					return true;
				}
				if (currentX == goalX - 1 && currentY == goalY
						&& (flags[currentX][currentY] & ACCESS_FROM_WEST_BLOCKED) == 0) {
					return true;
				}
				if (currentX == goalX + 1 && currentY == goalY
						&& (flags[currentX][currentY] & ACCESS_FROM_EAST_BLOCKED) == 0) {
					return true;
				}
			} else if (orientation == 2) {
				if (currentX == goalX + 1 && currentY == goalY) {
					return true;
				}
				if (currentX == goalX && currentY == goalY + 1
						&& (flags[currentX][currentY] & ACCESS_FROM_NORTH_BLOCKED) == 0) {
					return true;
				}
				if (currentX == goalX && currentY == goalY - 1
						&& (flags[currentX][currentY] & ACCESS_FROM_SOUTH_BLOCKED) == 0) {
					return true;
				}
			} else if (orientation == 3) {
				if (currentX == goalX && currentY == goalY - 1) {
					return true;
				}
				if (currentX == goalX - 1 && currentY == goalY
						&& (flags[currentX][currentY] & ACCESS_FROM_WEST_BLOCKED) == 0) {
					return true;
				}
				if (currentX == goalX + 1 && currentY == goalY
						&& (flags[currentX][currentY] & ACCESS_FROM_EAST_BLOCKED) == 0) {
					return true;
				}
			}
		}

		if (wallType == 2) {
			if (orientation == 0) {
				if (currentX == goalX - 1 && currentY == goalY) {
					return true;
				}
				if (currentX == goalX && currentY == goalY + 1) {
					return true;
				}
				if (currentX == goalX + 1 && currentY == goalY
						&& (flags[currentX][currentY] & ACCESS_FROM_EAST_BLOCKED) == 0) {
					return true;
				}
				if (currentX == goalX && currentY == goalY - 1
						&& (flags[currentX][currentY] & ACCESS_FROM_SOUTH_BLOCKED) == 0) {
					return true;
				}
			} else if (orientation == 1) {
				if (currentX == goalX - 1 && currentY == goalY
						&& (flags[currentX][currentY] & ACCESS_FROM_WEST_BLOCKED) == 0) {
					return true;
				}
				if (currentX == goalX && currentY == goalY + 1) {
					return true;
				}
				if (currentX == goalX + 1 && currentY == goalY) {
					return true;
				}
				if (currentX == goalX && currentY == goalY - 1
						&& (flags[currentX][currentY] & ACCESS_FROM_SOUTH_BLOCKED) == 0) {
					return true;
				}
			} else if (orientation == 2) {
				if (currentX == goalX - 1 && currentY == goalY
						&& (flags[currentX][currentY] & ACCESS_FROM_WEST_BLOCKED) == 0) {
					return true;
				}
				if (currentX == goalX && currentY == goalY + 1
						&& (flags[currentX][currentY] & ACCESS_FROM_NORTH_BLOCKED) == 0) {
					return true;
				}
				if (currentX == goalX + 1 && currentY == goalY) {
					return true;
				}
				if (currentX == goalX && currentY == goalY - 1) {
					return true;
				}
			} else if (orientation == 3) {
				if (currentX == goalX - 1 && currentY == goalY) {
					return true;
				}
				if (currentX == goalX && currentY == goalY + 1
						&& (flags[currentX][currentY] & ACCESS_FROM_NORTH_BLOCKED) == 0) {
					return true;
				}
				if (currentX == goalX + 1 && currentY == goalY
						&& (flags[currentX][currentY] & ACCESS_FROM_EAST_BLOCKED) == 0) {
					return true;
				}
				if (currentX == goalX && currentY == goalY - 1) {
					return true;
				}
			}
		}

		if (wallType == 9) {
			if (currentX == goalX && currentY == goalY + 1 && (flags[currentX][currentY] & BLOCK_SOUTH) == 0) {
				return true;
			}
			if (currentX == goalX && currentY == goalY - 1 && (flags[currentX][currentY] & BLOCK_NORTH) == 0) {
				return true;
			}
			if (currentX == goalX - 1 && currentY == goalY && (flags[currentX][currentY] & BLOCK_EAST) == 0) {
				return true;
			}
			return currentX == goalX + 1 && currentY == goalY && (flags[currentX][currentY] & BLOCK_WEST) == 0;
		}

		return false;
	}

	/** Tests whether a mover has reached a wall-decoration interaction tile. */
	public boolean reachedWallDecoration(int currentX, int currentY, int goalX, int goalY, int decorationType,
			int orientation) {
		if (currentX == goalX && currentY == goalY) {
			return true;
		}

		currentX -= insetX;
		currentY -= insetY;
		goalX -= insetX;
		goalY -= insetY;

		if (decorationType == 6 || decorationType == 7) {
			if (decorationType == 7) {
				orientation = orientation + 2 & 3;
			}
			if (orientation == 0) {
				if (currentX == goalX + 1 && currentY == goalY && (flags[currentX][currentY] & BLOCK_WEST) == 0) {
					return true;
				}
				if (currentX == goalX && currentY == goalY - 1 && (flags[currentX][currentY] & BLOCK_NORTH) == 0) {
					return true;
				}
			} else if (orientation == 1) {
				if (currentX == goalX - 1 && currentY == goalY && (flags[currentX][currentY] & BLOCK_EAST) == 0) {
					return true;
				}
				if (currentX == goalX && currentY == goalY - 1 && (flags[currentX][currentY] & BLOCK_NORTH) == 0) {
					return true;
				}
			} else if (orientation == 2) {
				if (currentX == goalX - 1 && currentY == goalY && (flags[currentX][currentY] & BLOCK_EAST) == 0) {
					return true;
				}
				if (currentX == goalX && currentY == goalY + 1 && (flags[currentX][currentY] & BLOCK_SOUTH) == 0) {
					return true;
				}
			} else if (orientation == 3) {
				if (currentX == goalX + 1 && currentY == goalY && (flags[currentX][currentY] & BLOCK_WEST) == 0) {
					return true;
				}
				if (currentX == goalX && currentY == goalY + 1 && (flags[currentX][currentY] & BLOCK_SOUTH) == 0) {
					return true;
				}
			}
		}

		if (decorationType == 8) {
			if (currentX == goalX && currentY == goalY + 1 && (flags[currentX][currentY] & BLOCK_SOUTH) == 0) {
				return true;
			}
			if (currentX == goalX && currentY == goalY - 1 && (flags[currentX][currentY] & BLOCK_NORTH) == 0) {
				return true;
			}
			if (currentX == goalX - 1 && currentY == goalY && (flags[currentX][currentY] & BLOCK_EAST) == 0) {
				return true;
			}
			return currentX == goalX + 1 && currentY == goalY && (flags[currentX][currentY] & BLOCK_WEST) == 0;
		}

		return false;
	}

	/**
	 * Tests whether a mover occupies or can approach a rectangular object.
	 *
	 * @param accessMask side restrictions: north {@code 1}, east {@code 2}, south
	 *                   {@code 4}, and west {@code 8}
	 */
	public boolean reachedObject(int currentX, int currentY, int goalX, int goalY, int sizeX, int sizeY,
			int accessMask) {
		int maxGoalX = goalX + sizeX - 1;
		int maxGoalY = goalY + sizeY - 1;

		if (currentX >= goalX && currentX <= maxGoalX && currentY >= goalY && currentY <= maxGoalY) {
			return true;
		}
		if (currentX == goalX - 1 && currentY >= goalY && currentY <= maxGoalY
				&& (flags[currentX - insetX][currentY - insetY] & BLOCK_EAST) == 0 && (accessMask & 8) == 0) {
			return true;
		}
		if (currentX == maxGoalX + 1 && currentY >= goalY && currentY <= maxGoalY
				&& (flags[currentX - insetX][currentY - insetY] & BLOCK_WEST) == 0 && (accessMask & 2) == 0) {
			return true;
		}
		if (currentY == goalY - 1 && currentX >= goalX && currentX <= maxGoalX
				&& (flags[currentX - insetX][currentY - insetY] & BLOCK_NORTH) == 0 && (accessMask & 4) == 0) {
			return true;
		}
		return currentY == maxGoalY + 1 && currentX >= goalX && currentX <= maxGoalX
				&& (flags[currentX - insetX][currentY - insetY] & BLOCK_SOUTH) == 0 && (accessMask & 1) == 0;
	}

	private void updateRectangle(int x, int y, int sizeX, int sizeY, int flag, boolean add) {
		for (int currentX = x; currentX < x + sizeX; currentX++) {
			if (currentX < 0 || currentX >= width) {
				continue;
			}
			for (int currentY = y; currentY < y + sizeY; currentY++) {
				if (currentY >= 0 && currentY < height) {
					updateFlag(currentX, currentY, flag, add);
				}
			}
		}
	}

	private void updateWallFlags(int x, int y, int type, int orientation, int shift, boolean add) {
		if (type == 0) {
			if (orientation == 0) {
				updateFlag(x, y, BLOCK_WEST << shift, add);
				updateFlag(x - 1, y, BLOCK_EAST << shift, add);
			} else if (orientation == 1) {
				updateFlag(x, y, BLOCK_NORTH << shift, add);
				updateFlag(x, y + 1, BLOCK_SOUTH << shift, add);
			} else if (orientation == 2) {
				updateFlag(x, y, BLOCK_EAST << shift, add);
				updateFlag(x + 1, y, BLOCK_WEST << shift, add);
			} else if (orientation == 3) {
				updateFlag(x, y, BLOCK_SOUTH << shift, add);
				updateFlag(x, y - 1, BLOCK_NORTH << shift, add);
			}
		} else if (type == 1 || type == 3) {
			if (orientation == 0) {
				updateFlag(x, y, BLOCK_NORTH_WEST << shift, add);
				updateFlag(x - 1, y + 1, BLOCK_SOUTH_EAST << shift, add);
			} else if (orientation == 1) {
				updateFlag(x, y, BLOCK_NORTH_EAST << shift, add);
				updateFlag(x + 1, y + 1, BLOCK_SOUTH_WEST << shift, add);
			} else if (orientation == 2) {
				updateFlag(x, y, BLOCK_SOUTH_EAST << shift, add);
				updateFlag(x + 1, y - 1, BLOCK_NORTH_WEST << shift, add);
			} else if (orientation == 3) {
				updateFlag(x, y, BLOCK_SOUTH_WEST << shift, add);
				updateFlag(x - 1, y - 1, BLOCK_NORTH_EAST << shift, add);
			}
		} else if (type == 2) {
			if (orientation == 0) {
				updateFlag(x, y, (BLOCK_WEST | BLOCK_NORTH) << shift, add);
				updateFlag(x - 1, y, BLOCK_EAST << shift, add);
				updateFlag(x, y + 1, BLOCK_SOUTH << shift, add);
			} else if (orientation == 1) {
				updateFlag(x, y, (BLOCK_NORTH | BLOCK_EAST) << shift, add);
				updateFlag(x, y + 1, BLOCK_SOUTH << shift, add);
				updateFlag(x + 1, y, BLOCK_WEST << shift, add);
			} else if (orientation == 2) {
				updateFlag(x, y, (BLOCK_EAST | BLOCK_SOUTH) << shift, add);
				updateFlag(x + 1, y, BLOCK_WEST << shift, add);
				updateFlag(x, y - 1, BLOCK_NORTH << shift, add);
			} else if (orientation == 3) {
				updateFlag(x, y, (BLOCK_SOUTH | BLOCK_WEST) << shift, add);
				updateFlag(x, y - 1, BLOCK_NORTH << shift, add);
				updateFlag(x - 1, y, BLOCK_EAST << shift, add);
			}
		}
	}

	private void updateFlag(int x, int y, int flag, boolean add) {
		if (add) {
			addFlag(x, y, flag);
		} else {
			removeFlag(x, y, flag);
		}
	}
}