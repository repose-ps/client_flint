package rs2.sound;

import rs2.cache.ondemand.OnDemandFetcher;
import rs2.cache.ondemand.OnDemandRequest;
import rs2.sign.Signlink;

/**
 * Revision-377 MIDI selection, on-demand request, fade, volume and resume
 * state.
 *
 * <p>
 * Legacy ownership moved from client fields anInt1327 (selected track),
 * anInt1270 (requested track), aBoolean1271 (fade request), anInt1128 (resume
 * delay), and aBoolean1266 (music enabled).
 * </p>
 */
public final class MusicController {
	@FunctionalInterface
	public interface Requester {
		void request(int type, int id);
	}

	interface MidiBackend {
		void setVolume(int volume, boolean adjustPlayingTrack);

		void stop();

		void save(byte[] data, int length, boolean fade);
	}

	private static final class SignlinkMidiBackend implements MidiBackend {
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

	private final MidiBackend backend;
	private boolean enabled = true;
	private boolean fadeRequestedTrack = true;
	private int requestedTrackId;
	private int selectedTrackId = -1;
	private int resumeDelay;

	public MusicController() {
		this(new SignlinkMidiBackend());
	}

	MusicController(MidiBackend backend) {
		this.backend = backend;
	}

	public void requestStartupTrack(Requester requester, boolean lowMemory) {
		if (!lowMemory) {
			requestedTrackId = 0;
			fadeRequestedTrack = true;
			requester.request(OnDemandFetcher.MIDI, requestedTrackId);
		}
	}

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

	public void playTemporaryTrack(int trackId, int delay, boolean lowMemory, Requester requester) {
		if (enabled && !lowMemory) {
			requestedTrackId = trackId;
			fadeRequestedTrack = false;
			requester.request(OnDemandFetcher.MIDI, requestedTrackId);
			resumeDelay = delay;
		}
	}

	public boolean acceptOnDemandRequest(OnDemandRequest request) {
		if (request.type == OnDemandFetcher.MIDI && request.id == requestedTrackId && request.buffer != null) {
			if (enabled)
				backend.save(request.buffer, request.buffer.length, fadeRequestedTrack);
			return true;
		}
		return false;
	}

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

	public void stop() {
		backend.stop();
	}

	public void resetOnLogout() {
		backend.stop();
		selectedTrackId = -1;
		requestedTrackId = -1;
		resumeDelay = 0;
	}

	boolean isEnabled() {
		return enabled;
	}

	boolean fadeRequestedTrack() {
		return fadeRequestedTrack;
	}

	int requestedTrackId() {
		return requestedTrackId;
	}

	int selectedTrackId() {
		return selectedTrackId;
	}

	int resumeDelay() {
		return resumeDelay;
	}
}