package rs2.collection;

/**
 * Base element used by the client's intrusive linked data structures.
 *
 * <p>
 * An intrusive collection stores its linkage directly inside each element,
 * instead of allocating a separate wrapper object for every list entry. This
 * allows subclasses of {@code Node} to be inserted into the client's deques and
 * hash tables without additional allocations.
 * </p>
 *
 * <h2>Link invariants</h2>
 * <ul>
 * <li>A linked node has non-null {@link #next} and {@link #previous}
 * links.</li>
 * <li>An unlinked node has both links set to {@code null}.</li>
 * <li>For a linked node, {@code previous.next == this}.</li>
 * <li>For a linked node, {@code next.previous == this}.</li>
 * </ul>
 *
 * <p>
 * This class is intentionally not thread-safe. The original client owns and
 * modifies these structures from its game thread.
 * </p>
 *
 * @see <a href="https://docs.kernel.org/core-api/list.html"> Linux kernel
 *      intrusive linked lists</a>
 * @see <a href=
 *      "https://github.com/openjdk/jdk/blob/master/src/java.base/share/classes/java/util/LinkedList.java">
 *      OpenJDK LinkedList node unlinking</a>
 */
public class Node {

	/**
	 * Identifier used when this node is stored in a {@link NodeHashTable}.
	 *
	 * <p>
	 * The node itself does not interpret the key. Depending on the owning cache, it
	 * may represent an archive identifier, model identifier, object identifier, or
	 * another client-specific lookup value.
	 * </p>
	 */
	public long key;

	/**
	 * Next node in the primary intrusive list.
	 *
	 * <p>
	 * This is {@code null} while the node is not linked.
	 * </p>
	 */
	public Node next;

	/**
	 * Previous node in the primary intrusive list.
	 *
	 * <p>
	 * This is {@code null} while the node is not linked. The original client uses
	 * this field to determine whether the node is currently attached.
	 * </p>
	 */
	public Node previous;

	/**
	 * Removes this node from its current primary list.
	 *
	 * <p>
	 * The operation is constant time because the node stores references to both
	 * neighboring nodes. Calling this method on an already-unlinked node has no
	 * effect.
	 * </p>
	 *
	 * <p>
	 * Both links are cleared after removal. Besides marking the node as detached,
	 * this prevents the removed node from retaining the rest of the list
	 * unnecessarily.
	 * </p>
	 */
	public void unlink() {
		if (previous == null) {
			return;
		} else {
			previous.next = next;
			next.previous = previous;
			next = null;
			previous = null;
			return;
		}
	}

}
