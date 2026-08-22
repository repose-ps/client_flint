package rs2.game;

import java.util.Arrays;

import rs2.scene.util.CollisionMap;

/**
 * Revision-377 local tile pathfinder.
 *
 * <p>The search intentionally preserves the client's fixed 104x104 BFS
 * workspace, 4000-entry circular queue, collision masks, direction codes,
 * approximate-target heuristic and turn-point compression.</p>
 */
public final class Pathfinder {

    private static final int MAP_SIZE = 104;
    private static final int QUEUE_CAPACITY = 4000;
    private static final int UNREACHABLE_DISTANCE = 0x5f5e0ff;
    private static final int START_DIRECTION = 99;
    private static final int ALTERNATIVE_SEARCH_RADIUS = 10;
    private static final int ALTERNATIVE_MAX_DISTANCE = 100;

    private final int[][] directions = new int[MAP_SIZE][MAP_SIZE];
    private final int[][] distances = new int[MAP_SIZE][MAP_SIZE];
    private final int[] queueX = new int[QUEUE_CAPACITY];
    private final int[] queueY = new int[QUEUE_CAPACITY];

    /**
     * Finds a route using the exact revision-377 local collision rules.
     *
     * @param collisionMap      collision map for the current plane
     * @param startX            mover start tile X
     * @param startY            mover start tile Y
     * @param destinationX      target tile/rectangle X
     * @param destinationY      target tile/rectangle Y
     * @param targetWidth       rectangular target width; zero for non-rectangle targets
     * @param targetHeight      rectangular target height; zero for non-rectangle targets
     * @param interactionType   location shape encoded as shape + 1, or zero for ordinary tiles/rectangles
     * @param orientation       location orientation used by wall/decoration reach checks
     * @param accessMask        rectangular target side restrictions
     * @param allowAlternative  whether to choose the nearest short reachable tile when the target cannot be reached
     * @return compressed route, or {@code null} when no supported route exists
     */
    public Route findRoute(CollisionMap collisionMap, int startX, int startY, int destinationX, int destinationY,
            int targetWidth, int targetHeight, int interactionType, int orientation, int accessMask,
            boolean allowAlternative) {
        resetSearch();

        int currentX = startX;
        int currentY = startY;
        directions[startX][startY] = START_DIRECTION;
        distances[startX][startY] = 0;

        int writeIndex = 0;
        int readIndex = 0;
        queueX[writeIndex] = startX;
        queueY[writeIndex] = startY;
        writeIndex = (writeIndex + 1) % queueX.length;

        boolean reached = false;
        int[][] flags = collisionMap.flags;

        while (readIndex != writeIndex) {
            currentX = queueX[readIndex];
            currentY = queueY[readIndex];
            readIndex = (readIndex + 1) % queueX.length;

            if (currentX == destinationX && currentY == destinationY) {
                reached = true;
                break;
            }

            if (interactionType != 0) {
                if ((interactionType < 5 || interactionType == 10)
                        && collisionMap.reachedWall(currentX, currentY, destinationX, destinationY,
                                interactionType - 1, orientation)) {
                    reached = true;
                    break;
                }
                if (interactionType < 10
                        && collisionMap.reachedWallDecoration(currentX, currentY, destinationX, destinationY,
                                interactionType - 1, orientation)) {
                    reached = true;
                    break;
                }
            }

            if (targetWidth != 0 && targetHeight != 0
                    && collisionMap.reachedObject(currentX, currentY, destinationX, destinationY, targetWidth,
                            targetHeight, accessMask)) {
                reached = true;
                break;
            }

            int nextDistance = distances[currentX][currentY] + 1;

            if (currentX > 0 && directions[currentX - 1][currentY] == 0
                    && (flags[currentX - 1][currentY] & 0x1280108) == 0) {
                queueX[writeIndex] = currentX - 1;
                queueY[writeIndex] = currentY;
                writeIndex = (writeIndex + 1) % queueX.length;
                directions[currentX - 1][currentY] = 2;
                distances[currentX - 1][currentY] = nextDistance;
            }
            if (currentX < MAP_SIZE - 1 && directions[currentX + 1][currentY] == 0
                    && (flags[currentX + 1][currentY] & 0x1280180) == 0) {
                queueX[writeIndex] = currentX + 1;
                queueY[writeIndex] = currentY;
                writeIndex = (writeIndex + 1) % queueX.length;
                directions[currentX + 1][currentY] = 8;
                distances[currentX + 1][currentY] = nextDistance;
            }
            if (currentY > 0 && directions[currentX][currentY - 1] == 0
                    && (flags[currentX][currentY - 1] & 0x1280102) == 0) {
                queueX[writeIndex] = currentX;
                queueY[writeIndex] = currentY - 1;
                writeIndex = (writeIndex + 1) % queueX.length;
                directions[currentX][currentY - 1] = 1;
                distances[currentX][currentY - 1] = nextDistance;
            }
            if (currentY < MAP_SIZE - 1 && directions[currentX][currentY + 1] == 0
                    && (flags[currentX][currentY + 1] & 0x1280120) == 0) {
                queueX[writeIndex] = currentX;
                queueY[writeIndex] = currentY + 1;
                writeIndex = (writeIndex + 1) % queueX.length;
                directions[currentX][currentY + 1] = 4;
                distances[currentX][currentY + 1] = nextDistance;
            }
            if (currentX > 0 && currentY > 0 && directions[currentX - 1][currentY - 1] == 0
                    && (flags[currentX - 1][currentY - 1] & 0x128010e) == 0
                    && (flags[currentX - 1][currentY] & 0x1280108) == 0
                    && (flags[currentX][currentY - 1] & 0x1280102) == 0) {
                queueX[writeIndex] = currentX - 1;
                queueY[writeIndex] = currentY - 1;
                writeIndex = (writeIndex + 1) % queueX.length;
                directions[currentX - 1][currentY - 1] = 3;
                distances[currentX - 1][currentY - 1] = nextDistance;
            }
            if (currentX < MAP_SIZE - 1 && currentY > 0 && directions[currentX + 1][currentY - 1] == 0
                    && (flags[currentX + 1][currentY - 1] & 0x1280183) == 0
                    && (flags[currentX + 1][currentY] & 0x1280180) == 0
                    && (flags[currentX][currentY - 1] & 0x1280102) == 0) {
                queueX[writeIndex] = currentX + 1;
                queueY[writeIndex] = currentY - 1;
                writeIndex = (writeIndex + 1) % queueX.length;
                directions[currentX + 1][currentY - 1] = 9;
                distances[currentX + 1][currentY - 1] = nextDistance;
            }
            if (currentX > 0 && currentY < MAP_SIZE - 1 && directions[currentX - 1][currentY + 1] == 0
                    && (flags[currentX - 1][currentY + 1] & 0x1280138) == 0
                    && (flags[currentX - 1][currentY] & 0x1280108) == 0
                    && (flags[currentX][currentY + 1] & 0x1280120) == 0) {
                queueX[writeIndex] = currentX - 1;
                queueY[writeIndex] = currentY + 1;
                writeIndex = (writeIndex + 1) % queueX.length;
                directions[currentX - 1][currentY + 1] = 6;
                distances[currentX - 1][currentY + 1] = nextDistance;
            }
            if (currentX < MAP_SIZE - 1 && currentY < MAP_SIZE - 1
                    && directions[currentX + 1][currentY + 1] == 0
                    && (flags[currentX + 1][currentY + 1] & 0x12801e0) == 0
                    && (flags[currentX + 1][currentY] & 0x1280180) == 0
                    && (flags[currentX][currentY + 1] & 0x1280120) == 0) {
                queueX[writeIndex] = currentX + 1;
                queueY[writeIndex] = currentY + 1;
                writeIndex = (writeIndex + 1) % queueX.length;
                directions[currentX + 1][currentY + 1] = 12;
                distances[currentX + 1][currentY + 1] = nextDistance;
            }
        }

        boolean alternative = false;
        if (!reached) {
            if (!allowAlternative) {
                return null;
            }

            int bestSquaredDistance = 1000;
            int bestPathDistance = ALTERNATIVE_MAX_DISTANCE;
            for (int x = destinationX - ALTERNATIVE_SEARCH_RADIUS; x <= destinationX + ALTERNATIVE_SEARCH_RADIUS; x++) {
                for (int y = destinationY - ALTERNATIVE_SEARCH_RADIUS; y <= destinationY + ALTERNATIVE_SEARCH_RADIUS; y++) {
                    if (x < 0 || y < 0 || x >= MAP_SIZE || y >= MAP_SIZE || distances[x][y] >= ALTERNATIVE_MAX_DISTANCE) {
                        continue;
                    }

                    int deltaX = 0;
                    if (x < destinationX) {
                        deltaX = destinationX - x;
                    } else if (x > destinationX + targetWidth - 1) {
                        deltaX = x - (destinationX + targetWidth - 1);
                    }

                    int deltaY = 0;
                    if (y < destinationY) {
                        deltaY = destinationY - y;
                    } else if (y > destinationY + targetHeight - 1) {
                        deltaY = y - (destinationY + targetHeight - 1);
                    }

                    int squaredDistance = deltaX * deltaX + deltaY * deltaY;
                    if (squaredDistance < bestSquaredDistance
                            || squaredDistance == bestSquaredDistance && distances[x][y] < bestPathDistance) {
                        bestSquaredDistance = squaredDistance;
                        bestPathDistance = distances[x][y];
                        currentX = x;
                        currentY = y;
                    }
                }
            }

            if (bestSquaredDistance == 1000) {
                return null;
            }
            if (currentX == startX && currentY == startY) {
                return null;
            }
            alternative = true;
        }

        int waypointCount = 0;
        queueX[waypointCount] = currentX;
        queueY[waypointCount++] = currentY;

        int previousDirection;
        for (int direction = previousDirection = directions[currentX][currentY]; currentX != startX || currentY != startY;
                direction = directions[currentX][currentY]) {
            if (direction != previousDirection) {
                previousDirection = direction;
                queueX[waypointCount] = currentX;
                queueY[waypointCount++] = currentY;
            }
            if ((direction & 2) != 0) {
                currentX++;
            } else if ((direction & 8) != 0) {
                currentX--;
            }
            if ((direction & 1) != 0) {
                currentY++;
            } else if ((direction & 4) != 0) {
                currentY--;
            }
        }

        return new Route(Arrays.copyOf(queueX, waypointCount), Arrays.copyOf(queueY, waypointCount), waypointCount,
                alternative);
    }

    private void resetSearch() {
        for (int x = 0; x < MAP_SIZE; x++) {
            for (int y = 0; y < MAP_SIZE; y++) {
                directions[x][y] = 0;
                distances[x][y] = UNREACHABLE_DISTANCE;
            }
        }
    }

    /** Compressed turn-point route in the original endpoint-to-start order. */
    public static final class Route {
        private final int[] x;
        private final int[] y;
        private final int waypointCount;
        private final boolean alternative;

        private Route(int[] x, int[] y, int waypointCount, boolean alternative) {
            this.x = x;
            this.y = y;
            this.waypointCount = waypointCount;
            this.alternative = alternative;
        }

        public int getWaypointCount() {
            return waypointCount;
        }

        public int getWaypointX(int index) {
            return x[index];
        }

        public int getWaypointY(int index) {
            return y[index];
        }

        public int getDestinationX() {
            return x[0];
        }

        public int getDestinationY() {
            return y[0];
        }

        public boolean isAlternative() {
            return alternative;
        }
    }
}