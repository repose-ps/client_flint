package rs2.collection;

/**
 * A {@link Node} that can participate in two intrusive linked lists
 * simultaneously.
 *
 * <p>
 * The primary links inherited from {@link Node} are generally used by
 * {@link NodeHashTable}. The secondary links declared here are used by
 * {@link DualNodeDeque}, most notably for the access-order list maintained by
 * {@link LruCache}.
 * </p>
 *
 * <p>
 * Keeping two independent link pairs allows an object to remain inside a hash
 * bucket while being moved through an LRU recency queue. Promoting an entry in
 * the recency queue therefore does not disturb its hash-table membership.
 * </p>
 *
 * <h2>Secondary-link invariants</h2>
 * <ul>
 * <li>A linked node has non-null {@code nextDual} and {@code previousDual}
 * links.</li>
 * <li>An unlinked node has both secondary links set to {@code null}.</li>
 * <li>For a linked node, {@code previousDual.nextDual == this}.</li>
 * <li>For a linked node, {@code nextDual.previousDual == this}.</li>
 * </ul>
 *
 * @see <a href=
 *      "https://github.com/openjdk/jdk/blob/master/src/java.base/share/classes/java/util/LinkedHashMap.java">
 *      OpenJDK LinkedHashMap.Entry</a>
 * @see <a href="https://github.com/ben-manes/caffeine"> Caffeine in-memory
 *      cache</a>
 */
public class DualNode extends Node {

	/**
	 * Next node in the secondary intrusive list.
	 *
	 * <p>
	 * Package-private because only the collection implementation should modify list
	 * linkage directly.
	 * </p>
	 */
	public DualNode nextDual;

	/**
	 * Previous node in the secondary intrusive list.
	 *
	 * <p>
	 * A null value indicates that this node is not currently attached to a
	 * secondary list.
	 * </p>
	 */
	public DualNode previousDual;

	/**
	 * Removes this node from its current secondary list.
	 *
	 * <p>
	 * This operation does not affect the primary links inherited from {@link Node}.
	 * A node stored in a {@link NodeHashTable} therefore remains in that table
	 * after this method is called.
	 * </p>
	 *
	 * <p>
	 * The operation is constant time and has no effect when the node is already
	 * unlinked.
	 * </p>
	 */
	public void unlinkDual() {
		if (previousDual == null) {
			return;
		} else {
			previousDual.nextDual = nextDual;
			nextDual.previousDual = previousDual;
			nextDual = null;
			previousDual = null;
			return;
		}
	}

}
