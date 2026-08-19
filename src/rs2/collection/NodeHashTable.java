// Decompiled by Jad v1.5.8f. Copyright 2001 Pavel Kouznetsov.
// Jad home page: http://www.kpdus.com/jad.html
// Decompiler options: packimports(3) 
package rs2.collection;

/**
 * A fixed-size intrusive hash table keyed by {@code long} values.
 *
 * <p>
 * Each bucket is a circular linked list with its own sentinel node. Nodes are
 * selected using a power-of-two mask:
 * </p>
 *
 * <pre>{@code
 * bucketIndex = (int) (key & (bucketCount - 1));
 * }</pre>
 *
 * <p>
 * Because the table is intrusive, it stores no separate entry objects. A node's
 * {@link Node#key}, {@link Node#next}, and {@link Node#previous} fields form
 * the complete hash-table entry.
 * </p>
 *
 * <p>
 * The table does not resize. This matches the original client, where the table
 * is used for bounded caches with predictable allocation behavior.
 * </p>
 *
 * @see <a href=
 *      "https://github.com/openjdk/jdk/blob/master/src/java.base/share/classes/java/util/HashMap.java">
 *      OpenJDK HashMap bucket implementation</a>
 */
public class NodeHashTable {
	
	/**
	 * Number of hash buckets.
	 *
	 * <p>
	 * This is always a positive power of two.
	 * </p>
	 */
	public int bucketCount;

	/**
	 * Circular sentinel node for each hash bucket.
	 */
	public Node[] buckets;

	/**
	 * Creates a fixed-size hash table.
	 *
	 * @param bucketCount number of buckets; must be a positive power of two
	 */
	public NodeHashTable(int bucketCount) {
		this.bucketCount = bucketCount;
		buckets = new Node[bucketCount];
		for (int index = 0; index < bucketCount; index++) {
			Node sentinel = buckets[index] = new Node();
			sentinel.next = sentinel;
			sentinel.previous = sentinel;
		}
	}

	/**
	 * Finds the node associated with a key.
	 *
	 * <p>
	 * Only the selected bucket is traversed. The operation is constant time on
	 * average but linear in the number of collisions in that bucket.
	 * </p>
	 *
	 * @param key lookup key
	 * @return the matching node, or {@code null} when absent
	 */
	public Node get(long key) {
		Node bucket = buckets[(int) (key & (long) (bucketCount - 1))];
		for (Node node = bucket.next; node != bucket; node = node.next)
			if (node.key == key)
				return node;

		return null;
	}

	/**
	 * Associates a node with a key.
	 *
	 * <p>
	 * If the node already belongs to a primary intrusive list, it is unlinked
	 * before being inserted into the selected bucket.
	 * </p>
	 *
	 * <p>
	 * This method does not search for or replace another node using the same key.
	 * The client is responsible for maintaining unique keys in each cache.
	 * </p>
	 *
	 * @param key  lookup key
	 * @param node node to store
	 */
	public void put(long key, Node node) {
		if (node.previous != null)
			node.unlink();
		Node bucket = bucket(key);
		node.previous = bucket.previous;
		node.next = bucket;
		node.previous.next = node;
		node.next.previous = node;
		node.key = key;
	}

	/**
	 * Selects the bucket associated with a key.
	 */
	private Node bucket(long key) {
		int index = (int) (key & (bucketCount - 1));
		return buckets[index];
	}

}
