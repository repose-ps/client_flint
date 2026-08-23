package rs2.cache.ondemand;

/**
 * Supplies model archive requests to the model loader when model bytes are not
 * yet available locally.
 *
 * <p>
 * The revision-377 model loader only needs this single callback. The concrete
 * {@link OnDemandFetcher} maps model requests to archive type 0.
 * </p>
 */
public class OnDemandProvider {

	/**
	 * Requests a model archive entry.
	 *
	 * @param modelId model archive id
	 */
	public void requestModel(int modelId) {
		// Base provider intentionally does nothing, matching the original hook.
	}
}
