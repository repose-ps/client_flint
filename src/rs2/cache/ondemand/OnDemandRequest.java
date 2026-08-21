package rs2.cache.ondemand;

import rs2.collection.DualNode;

/**
 * Intrusive request node used by the revision-377 on-demand cache/network
 * loader.
 *
 * <p>
 * The node participates in a primary deque while its inherited secondary links
 * are simultaneously used by the fetcher's outstanding-request set.
 * </p>
 */
public class OnDemandRequest extends DualNode {

	/** Archive type: 0=model, 1=animation, 2=MIDI, 3=map. */
	public int type;

	/** File id within {@link #type}. */
	public int id;

	/** Number of service-loop cycles since this request was last sent. */
	public int loopCycle;

	/**
	 * Compressed cache/update-server bytes until {@code poll()} decompresses them.
	 */
	public byte[] buffer;

	/**
	 * Historical "incomplete" request state from this client lineage.
	 *
	 * <p>
	 * When true the request is a foreground request and is sent with request mode
	 * 2. Background extra-file requests use false. A completed background map
	 * request can be promoted back to true with synthetic type 93 so the client can
	 * scan its location stream for model dependencies.
	 * </p>
	 */
	public boolean incomplete = true;
}