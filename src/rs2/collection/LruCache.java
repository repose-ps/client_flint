package rs2.collection;

/**
 * A fixed-capacity least-recently-used cache keyed by {@code long} values.
 *
 * <p>
 * The cache combines two intrusive structures:
 * </p>
 *
 * <ul>
 * <li>{@link NodeHashTable} provides key lookup through a node's primary
 * links.</li>
 * <li>{@link DualNodeDeque} records access order through the node's secondary
 * links.</li>
 * </ul>
 *
 * <p>
 * Because the two structures use independent link pairs, a cached object can
 * remain in its hash bucket while being promoted to the most-recently-used
 * position.
 * </p>
 *
 * <p>
 * The first node in the recency deque is the least recently used entry. The
 * last node is the most recently used entry.
 * </p>
 *
 * <p>
 * This implementation is intentionally not thread-safe. The original client
 * uses these caches from its game thread.
 * </p>
 *
 * @see <a href=
 *      "https://github.com/openjdk/jdk/blob/master/src/java.base/share/classes/java/util/LinkedHashMap.java">
 *      OpenJDK access-ordered LinkedHashMap</a>
 * @see <a href="https://github.com/ben-manes/caffeine"> Caffeine
 *      high-performance caching library</a>
 */
public class LruCache {

	/**
	 * Number of unsuccessful lookups.
	 */
	public int misses;

	/**
	 * Number of successful lookups.
	 */
	public int hits;

	/**
	 * Number of entries the cache can retain.
	 */
	public int capacity;

	/**
	 * Number of unused entry slots before eviction becomes necessary.
	 */
	public int remaining;

	/**
	 * Key-to-node lookup table.
	 *
	 * <p>
	 * The original client uses 1,024 buckets for every LRU cache, independently of
	 * its entry capacity.
	 * </p>
	 */
	public final NodeHashTable table = new NodeHashTable(1024);

	/**
	 * Entries ordered from least recently used to most recently used.
	 */
	public final DualNodeDeque recency = new DualNodeDeque();

	/**
	 * Creates an empty fixed-capacity cache.
	 *
	 * @param capacity maximum number of retained entries
	 */
	public LruCache(int capacity) {
		this.capacity = capacity;
		remaining = capacity;
	}

	/**
	 * Finds an entry and promotes it to the most-recently-used position.
	 *
	 * @param key lookup key
	 * @return the cached node, or {@code null} when absent
	 */
	public DualNode get(long key) {
		DualNode node = (DualNode) table.get(key);

		if (node == null) {
			misses++;
			return null;
		}

		recency.addLast(node);
		hits++;
		return node;
	}

	/**
	 * Stores an entry.
	 *
	 * <p>
	 * When the cache is full, the least recently used entry is removed before the
	 * new entry is inserted.
	 * </p>
	 *
	 * @param key  lookup key
	 * @param node entry to cache
	 */
	public void put(long key, DualNode node) {
		if (remaining == 0) {
			evictLeastRecentlyUsed();
		} else {
			remaining--;
		}
		table.put(key, node);
		recency.addLast(node);
	}

	/**
	 * Removes every entry from the cache.
	 *
	 * <p>
	 * Lookup statistics are intentionally retained, matching the original client's
	 * behavior.
	 * </p>
	 */
	public void clear() {
		do {
			DualNode node = recency.removeFirst();
			if (node != null) {
				node.unlink();
				node.unlinkDual();
			} else {
				remaining = capacity;
				return;
			}
		} while (true);
	}

	/**
	 * Removes the least recently used entry.
	 */
	private void evictLeastRecentlyUsed() {
		DualNode oldest = recency.removeFirst();
		if (oldest == null) {
			throw new IllegalStateException("Cache reports full capacity but contains no entries");
		}

		// removeFirst() has already removed the secondary recency links.
		// The primary links still attach the node to its hash bucket.
		oldest.unlink();
	}
}
