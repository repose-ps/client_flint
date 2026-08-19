// Decompiled by Jad v1.5.8f. Copyright 2001 Pavel Kouznetsov.
// Jad home page: http://www.kpdus.com/jad.html
// Decompiler options: packimports(3) 
package rs2.collection;

/**
 * A sentinel-backed double-ended queue for {@link Node} instances.
 *
 * <p>
 * This is an intrusive collection: each element stores its own
 * {@link Node#next} and {@link Node#previous} links. Adding an element
 * therefore requires no wrapper allocation.
 * </p>
 *
 * <p>
 * The deque uses a circular sentinel node:
 * </p>
 *
 * <ul>
 * <li>An empty deque has {@code sentinel.next == sentinel}.</li>
 * <li>It also has {@code sentinel.previous == sentinel}.</li>
 * <li>The first element follows the sentinel.</li>
 * <li>The last element precedes the sentinel.</li>
 * </ul>
 *
 * <p>
 * The {@link #first()}, {@link #last()}, {@link #next()}, and
 * {@link #previous()} methods share one traversal cursor. Consequently,
 * traversals are not reentrant and this class is not thread-safe.
 * </p>
 *
 * @see <a href="https://docs.kernel.org/core-api/list.html"> Linux kernel
 *      intrusive linked lists</a>
 * @see <a href=
 *      "https://github.com/openjdk/jdk/blob/master/src/java.base/share/classes/java/util/LinkedList.java">
 *      OpenJDK LinkedList</a>
 */
public class NodeDeque {

	/**
	 * Circular boundary node.
	 *
	 * <p>
	 * The sentinel is structural and is never returned as an element.
	 * </p>
	 */
	public final Node sentinel = new Node();

	/**
	 * Cursor used by the legacy traversal methods.
	 */
	public Node cursor;

	/**
	 * Creates an empty deque.
	 */
	public NodeDeque() {
		sentinel.next = sentinel;
		sentinel.previous = sentinel;
	}

	/**
	 * Adds a node to the end of the deque.
	 *
	 * <p>
	 * If the node already belongs to a primary intrusive list, it is unlinked from
	 * that list first.
	 * </p>
	 *
	 * @param node node to add or move
	 */
	public void addLast(Node node) {
		if (node.previous != null)
			node.unlink();
		node.previous = sentinel.previous;
		node.next = sentinel;
		node.previous.next = node;
		node.next.previous = node;
	}

	/**
	 * Adds a node to the beginning of the deque.
	 *
	 * <p>
	 * If the node already belongs to a primary intrusive list, it is unlinked from
	 * that list first.
	 * </p>
	 *
	 * @param node node to add or move
	 */
	public void addFirst(Node node) {
		if (node.previous != null)
			node.unlink();
		node.previous = sentinel;
		node.next = sentinel.next;
		node.previous.next = node;
		node.next.previous = node;
	}

	/**
	 * Removes and returns the first node.
	 *
	 * @return the removed node, or {@code null} when the deque is empty
	 */
	public Node removeFirst() {
		Node node = sentinel.next;
		if (node == sentinel) {
			return null;
		} else {
			node.unlink();
			return node;
		}
	}

	/**
	 * Begins a forward traversal.
	 *
	 * <p>
	 * Continue the traversal by repeatedly calling {@link #next()}.
	 * </p>
	 *
	 * @return the first node, or {@code null} when the deque is empty
	 */
	public Node first() {
		Node node = sentinel.next;
		if (node == sentinel) {
			cursor = null;
			return null;
		} else {
			cursor = node.next;
			return node;
		}
	}

	/**
	 * Begins a reverse traversal.
	 *
	 * <p>
	 * Continue the traversal by repeatedly calling {@link #previous()}.
	 * </p>
	 *
	 * @return the last node, or {@code null} when the deque is empty
	 */
	public Node last() {
		Node node = sentinel.previous;
		if (node == sentinel) {
			cursor = null;
			return null;
		} else {
			cursor = node.previous;
			return node;
		}
	}

	/**
	 * Continues the forward traversal started by {@link #first()}.
	 *
	 * @return the next node, or {@code null} at the end of the deque
	 */
	public Node next() {
		Node node = cursor;
		if (node == sentinel) {
			cursor = null;
			return null;
		}
		cursor = node.next;
		return node;
	}

	/**
	 * Continues the reverse traversal started by {@link #last()}.
	 *
	 * @return the previous node, or {@code null} at the beginning of the deque
	 */
	public Node previous() {
		Node node = cursor;
		if (node == sentinel) {
			cursor = null;
			return null;
		} else {
			cursor = node.previous;
			return node;
		}
	}

	/**
	 * Unlinks every node from the deque.
	 *
	 * <p>
	 * The removed nodes are left with null primary links and may subsequently be
	 * inserted into another collection.
	 * </p>
	 */
	public void clear() {
		if (sentinel.next == sentinel)
			return;
		do {
			Node node = sentinel.next;
			if (node == sentinel)
				return;
			node.unlink();
		} while (true);
	}

}
