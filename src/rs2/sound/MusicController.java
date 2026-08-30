package rs2.sound;

import rs2.cache.ondemand.OnDemandFetcher;
import rs2.cache.ondemand.OnDemandRequest;
import rs2.sign.Signlink;

/**
 * Owns revision-377 MIDI selection, on-demand request, fade, volume, and resume
 * state.
 */
public final class MusicController {
	/** Provides requester state and behavior. */
	@FunctionalInterface
	public interface Requester {
		/**
		 * Requests the operation.
		 *
		 * @param type the type
		 * @param id the identifier
		 */
		void request(int type, int id);
	}

	/** Provides MIDI backend state and behavior. */
	interface MidiBackend {
		/**
		 * Sets volume.
		 *
		 * @param volume the volume
		 * @param adjustPlayingTrack the adjust playing track
		 */
		void setVolume(int volume, boolean adjustPlayingTrack);

		/**
		 * Stops the operation.
		 */
		void stop();

		/**
		 * Saves the operation.
		 *
		 * @param data the data to process
		 * @param length the number of elements or bytes
		 * @param fade the fade
		 */
		void save(byte[] data, int length, boolean fade);
	}

	/** MIDI backend that delegates legacy playback requests to {@link Signlink}. */
	private static final class SignlinkMidiBackend implements MidiBackend {

		/** Creates a new signlink MIDI backend with its default client state. */
		private SignlinkMidiBackend() {
		}
		public void setVolume(int volume, boolean adjustPlayingTrack) {
			Signlink.setMidiVolume(volume, adjustPlayingTrack);
		}

		public void stop() {
			Signlink.stopMidi();
		}

		public void save(byte[] data, int length, boolean fade) {
			Signlink.saveMidi(data, length, fade);
		}
	}

	/** Stores the current backend. */
	private final MidiBackend backend;
	/** Whether enabled is enabled or active. */
	private boolean enabled = true;
	/** Whether fade requested track is enabled or active. */
	private boolean fadeRequestedTrack = true;
	/** Stores the current requested track ID. */
	private int requestedTrackId;
	/** Stores the current selected track ID. */
	private int selectedTrackId = -1;
	/** Stores the current resume delay. */
	private int resumeDelay;

	/**
	 * Creates a new music controller.
	 */
	public MusicController() {
		this(new SignlinkMidiBackend());
	}

	/**
	 * Creates a new music controller.
	 *
	 * @param backend the backend
	 */
	MusicController(MidiBackend backend) {
		this.backend = backend;
	}

	/**
	 * Requests startup track.
	 *
	 * @param requester the requester
	 * @param lowMemory whether low-memory mode is active
	 */
	public void requestStartupTrack(Requester requester, boolean lowMemory) {
		if (!lowMemory) {
			requestedTrackId = 0;
			fadeRequestedTrack = true;
			requester.request(OnDemandFetcher.MIDI, requestedTrackId);
		}
	}

	/**
	 * Selects track.
	 *
	 * @param trackId the track ID
	 * @param lowMemory whether low-memory mode is active
	 * @param requester the requester
	 */
	public void selectTrack(int trackId, boolean lowMemory, Requester requester) {
		if (trackId == 65535)
			trackId = -1;
		if (trackId != selectedTrackId && enabled && !lowMemory && resumeDelay == 0) {
			requestedTrackId = trackId;
			fadeRequestedTrack = true;
			requester.request(OnDemandFetcher.MIDI, requestedTrackId);
		}
		selectedTrackId = trackId;
	}

	/**
	 * Requests playback of a temporary music track.
	 *
	 * @param trackId the track ID
	 * @param delay the delay
	 * @param lowMemory whether low-memory mode is active
	 * @param requester the requester
	 */
	public void playTemporaryTrack(int trackId, int delay, boolean lowMemory, Requester requester) {
		if (enabled && !lowMemory) {
			requestedTrackId = trackId;
			fadeRequestedTrack = false;
			requester.request(OnDemandFetcher.MIDI, requestedTrackId);
			resumeDelay = delay;
		}
	}

	/**
	 * Handles a completed on-demand music request.
	 *
	 * @param request the request
	 * @return whether accept on demand request
	 */
	public boolean acceptOnDemandRequest(OnDemandRequest request) {
		if (request.type == OnDemandFetcher.MIDI && request.id == requestedTrackId && request.buffer != null) {
			if (enabled)
				backend.save(request.buffer, request.buffer.length, fadeRequestedTrack);
			return true;
		}
		return false;
	}

	/**
	 * Updates resume delay.
	 *
	 * @param lowMemory whether low-memory mode is active
	 * @param requester the requester
	 */
	public void updateResumeDelay(boolean lowMemory, Requester requester) {
		if (resumeDelay > 0) {
			resumeDelay -= 20;
			if (resumeDelay < 0)
				resumeDelay = 0;
			if (resumeDelay == 0 && enabled && !lowMemory) {
				requestedTrackId = selectedTrackId;
				fadeRequestedTrack = true;
				requester.request(OnDemandFetcher.MIDI, requestedTrackId);
			}
		}
	}

	/**
	 * Applies setting.
	 *
	 * @param setting the setting
	 * @param lowMemory whether low-memory mode is active
	 * @param requester the requester
	 */
	public void applySetting(int setting, boolean lowMemory, Requester requester) {
		boolean wasEnabled = enabled;
		if (setting == 0) {
			backend.setVolume(0, enabled);
			enabled = true;
		}
		if (setting == 1) {
			backend.setVolume(-400, enabled);
			enabled = true;
		}
		if (setting == 2) {
			backend.setVolume(-800, enabled);
			enabled = true;
		}
		if (setting == 3) {
			backend.setVolume(-1200, enabled);
			enabled = true;
		}
		if (setting == 4)
			enabled = false;
		if (enabled != wasEnabled && !lowMemory) {
			if (enabled) {
				requestedTrackId = selectedTrackId;
				fadeRequestedTrack = true;
				requester.request(OnDemandFetcher.MIDI, requestedTrackId);
			} else {
				backend.stop();
			}
			resumeDelay = 0;
		}
	}

	/**
	 * Stops the operation.
	 */
	public void stop() {
		backend.stop();
	}

	/**
	 * Resets on logout.
	 */
	public void resetOnLogout() {
		backend.stop();
		selectedTrackId = -1;
		requestedTrackId = -1;
		resumeDelay = 0;
	}

	/**
	 * Returns whether enabled.
	 *
	 * @return whether enabled
	 */
	boolean isEnabled() {
		return enabled;
	}

	/**
	 * Returns whether fade requested track is active.
	 *
	 * @return whether fade requested track
	 */
	boolean fadeRequestedTrack() {
		return fadeRequestedTrack;
	}

	/**
	 * Requests ed track ID.
	 *
	 * @return the currently requested track identifier
	 */
	int requestedTrackId() {
		return requestedTrackId;
	}

	/**
	 * Selects ed track ID.
	 *
	 * @return the currently selected background-track identifier
	 */
	int selectedTrackId() {
		return selectedTrackId;
	}

	/**
	 * Returns the resume delay.
	 *
	 * @return the resume delay
	 */
	int resumeDelay() {
		return resumeDelay;
	}
}
