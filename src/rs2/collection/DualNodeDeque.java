// Decompiled by Jad v1.5.8f. Copyright 2001 Pavel Kouznetsov.
// Jad home page: http://www.kpdus.com/jad.html
// Decompiler options: packimports(3) 
package rs2.collection;

/**
 * A sentinel-backed deque using the secondary links of {@link DualNode}.
 *
 * <p>
 * This structure is used as an ordering queue while each node's primary links
 * remain available for membership in another structure. In {@link LruCache},
 * the first node is the least recently used entry and the last node is the most
 * recently used entry.
 * </p>
 *
 * <p>
 * The deque uses a circular sentinel node instead of nullable head and tail
 * references:
 * </p>
 *
 * <ul>
 * <li>An empty deque has {@code sentinel.nextDual == sentinel}.</li>
 * <li>It also has {@code sentinel.previousDual == sentinel}.</li>
 * <li>The first entry follows the sentinel.</li>
 * <li>The last entry precedes the sentinel.</li>
 * </ul>
 *
 * <p>
 * This class is not thread-safe. Its {@link #first()} and {@link #next()}
 * methods also share a mutable traversal cursor, so only one traversal may be
 * active at a time.
 * </p>
 *
 * @see <a href=
 *      "https://github.com/openjdk/jdk/blob/master/src/java.base/share/classes/java/util/LinkedHashMap.java">
 *      OpenJDK LinkedHashMap access ordering</a>
 * @see <a href=
 *      "https://github.com/ben-manes/caffeine/tree/master/caffeine/src/main/java/com/github/benmanes/caffeine/cache">
 *      Caffeine cache eviction structures</a>
 */
public class DualNodeDeque {
	/**
	 * Circular boundary node.
	 *
	 * <p>
	 * The sentinel is structural only and is never returned as a deque element.
	 * </p>
	 */
	public final DualNode sentinel = new DualNode();

	/**
	 * Cursor used by the legacy {@link #first()}/{@link #next()} traversal API.
	 */
	public DualNode cursor;

	/**
	 * Creates an empty deque.
	 */
	public DualNodeDeque() {
		sentinel.nextDual = sentinel;
		sentinel.previousDual = sentinel;
	}

	/**
	 * Adds a node to the end of the deque.
	 *
	 * <p>
	 * If the node already belongs to a secondary list, it is removed from that list
	 * first. Consequently, adding an existing member moves it to the end instead of
	 * inserting it twice.
	 * </p>
	 *
	 * <p>
	 * For an LRU cache, the end represents the most recently used position.
	 * </p>
	 *
	 * @param node node to add or move
	 */
	public void addLast(DualNode node) {
		if (node.previousDual != null)
			node.unlinkDual();
		node.previousDual = sentinel.previousDual;
		node.nextDual = sentinel;
		node.previousDual.nextDual = node;
		node.nextDual.previousDual = node;
	}

	/**
	 * Removes and returns the first node.
	 *
	 * <p>
	 * For an LRU cache, this removes the least recently used entry.
	 * </p>
	 *
	 * @return the removed node, or {@code null} when the deque is empty
	 */
	public DualNode removeFirst() {
		DualNode node = sentinel.nextDual;
		if (node == sentinel) {
			return null;
		} else {
			node.unlinkDual();
			return node;
		}
	}

	/**
	 * Begins a forward traversal.
	 *
	 * @return the first node, or {@code null} when the deque is empty
	 */
	public DualNode first() {
		DualNode node = sentinel.nextDual;
		if (node == sentinel) {
			cursor = null;
			return null;
		} else {
			cursor = node.nextDual;
			return node;
		}
	}

	/**
	 * Continues the traversal started by {@link #first()}.
	 *
	 * @return the next node, or {@code null} after reaching the sentinel
	 */
	public DualNode next() {
		DualNode node = cursor;
		if (node == sentinel) {
			cursor = null;
			return null;
		}
		cursor = node.nextDual;
		return node;
	}

	/**
	 * Counts the nodes currently in the deque.
	 *
	 * <p>
	 * This operation runs in linear time because the original structure does not
	 * maintain a separate size field.
	 * </p>
	 *
	 * @return number of nodes in the deque
	 */
	public int size() {
		int size = 0;
		for (DualNode node = sentinel.nextDual; node != sentinel; node = node.nextDual)
			size++;

		return size;
	}

}
