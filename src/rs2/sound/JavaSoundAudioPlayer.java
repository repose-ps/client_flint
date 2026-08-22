package rs2.sound;

import java.io.File;
import java.io.IOException;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiChannel;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Sequence;
import javax.sound.midi.Sequencer;
import javax.sound.midi.Synthesizer;
import javax.sound.midi.Transmitter;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;

/**
 * Standalone Java Sound replacement for the browser-side audio consumer used by
 * the revision-377 client.
 *
 * <p>
 * The historical client/signlink pair only published saved WAV/MIDI paths plus
 * volume and fade signals; browser-side media controls consumed those signals.
 * The standalone client no longer has that browser layer, so this class maps the
 * same requests onto {@code javax.sound.sampled} and {@code javax.sound.midi}.
 * Audio-device failures are deliberately non-fatal to the game client.
 * </p>
 */
public final class JavaSoundAudioPlayer implements AutoCloseable {

	interface WaveOutput extends AutoCloseable {
		void play(File file, int volume) throws Exception;

		void setVolume(int volume);
	}

	interface MidiOutput extends AutoCloseable {
		void play(File file, int volume, boolean fade) throws Exception;

		void setVolume(int volume);

		void stop();
	}

	private final WaveOutput waveOutput;
	private final MidiOutput midiOutput;

	/** Creates a player backed by the platform Java Sound implementation. */
	public JavaSoundAudioPlayer() {
		this(new ClipWaveOutput(), new SequencerMidiOutput());
	}

	JavaSoundAudioPlayer(WaveOutput waveOutput, MidiOutput midiOutput) {
		this.waveOutput = waveOutput;
		this.midiOutput = midiOutput;
	}

	/** Plays the supplied revision-377 WAV file at the legacy attenuation value. */
	public void playWave(File file, int volume) {
		try {
			waveOutput.play(file, volume);
		} catch (Exception exception) {
			report("WAV", exception);
		}
	}

	/** Applies the legacy hundredths-of-a-decibel attenuation to WAV playback. */
	public void setWaveVolume(int volume) {
		waveOutput.setVolume(volume);
	}

	/** Plays a MIDI file, optionally fading the currently playing track first. */
	public void playMidi(File file, int volume, boolean fade) {
		try {
			midiOutput.play(file, volume, fade);
		} catch (Exception exception) {
			report("MIDI", exception);
		}
	}

	/** Applies the legacy MIDI attenuation to the currently playing track. */
	public void setMidiVolume(int volume) {
		midiOutput.setVolume(volume);
	}

	/** Stops the current MIDI track. */
	public void stopMidi() {
		midiOutput.stop();
	}

	/** Releases any currently open Java Sound devices. */
	@Override
	public void close() {
		try {
			waveOutput.close();
		} catch (Exception exception) {
			report("WAV close", exception);
		}
		try {
			midiOutput.close();
		} catch (Exception exception) {
			report("MIDI close", exception);
		}
	}

	static float toDecibels(int legacyVolume) {
		return legacyVolume / 100.0F;
	}

	static int toMidiControllerVolume(int legacyVolume) {
		double amplitude = Math.pow(10.0D, legacyVolume / 2000.0D);
		int value = (int) Math.round(127.0D * amplitude);
		if (value < 0) {
			return 0;
		}
		if (value > 127) {
			return 127;
		}
		return value;
	}

	private static void report(String kind, Exception exception) {
		System.err.println(kind + " playback unavailable: " + exception.getMessage());
	}

	private static final class ClipWaveOutput implements WaveOutput {
		private Clip clip;
		private int volume;

		@Override
		public synchronized void play(File file, int requestedVolume)
				throws UnsupportedAudioFileException, IOException, LineUnavailableException {
			closeClip();
			volume = requestedVolume;
			try (AudioInputStream input = AudioSystem.getAudioInputStream(file)) {
				Clip next = AudioSystem.getClip();
				next.open(input);
				clip = next;
				applyGain(next, volume);
				next.addLineListener(event -> {
					if (event.getType() == LineEvent.Type.STOP) {
						synchronized (ClipWaveOutput.this) {
							if (clip == next && next.getFramePosition() >= next.getFrameLength()) {
								closeClip();
							}
						}
					}
				});
				next.start();
			}
		}

		@Override
		public synchronized void setVolume(int requestedVolume) {
			volume = requestedVolume;
			if (clip != null) {
				applyGain(clip, volume);
			}
		}

		@Override
		public synchronized void close() {
			closeClip();
		}

		private void closeClip() {
			if (clip != null) {
				Clip current = clip;
				clip = null;
				current.stop();
				current.close();
			}
		}

		private static void applyGain(Clip target, int volume) {
			if (!target.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
				return;
			}
			FloatControl gain = (FloatControl) target.getControl(FloatControl.Type.MASTER_GAIN);
			float decibels = toDecibels(volume);
			if (decibels < gain.getMinimum()) {
				decibels = gain.getMinimum();
			} else if (decibels > gain.getMaximum()) {
				decibels = gain.getMaximum();
			}
			gain.setValue(decibels);
		}
	}

	private static final class SequencerMidiOutput implements MidiOutput {
		private static final int FADE_STEP_HUNDREDTHS_DB = 100;
		private static final int FADE_FLOOR_HUNDREDTHS_DB = -3600;
		private static final long FADE_STEP_MILLIS = 200L;

		private Sequencer sequencer;
		private Synthesizer synthesizer;
		private Transmitter transmitter;
		private int volume;
		private int generation;

		@Override
		public void play(File file, int requestedVolume, boolean fade)
				throws IOException, InvalidMidiDataException, MidiUnavailableException {
			Sequence sequence = MidiSystem.getSequence(file);
			synchronized (this) {
				volume = requestedVolume;
				int requestGeneration = ++generation;
				if (fade && sequencer != null && sequencer.isRunning()) {
					Thread fadeThread = new Thread(() -> fadeThenReplace(sequence, requestedVolume, requestGeneration),
							"rs2-midi-fade");
					fadeThread.setDaemon(true);
					fadeThread.start();
					return;
				}
				replaceSequence(sequence, requestedVolume);
			}
		}

		@Override
		public synchronized void setVolume(int requestedVolume) {
			volume = requestedVolume;
			applyMidiVolume(requestedVolume);
		}

		@Override
		public synchronized void stop() {
			generation++;
			closeDevices();
		}

		@Override
		public synchronized void close() {
			generation++;
			closeDevices();
		}

		private void fadeThenReplace(Sequence sequence, int requestedVolume, int requestGeneration) {
			int fadeVolume;
			synchronized (this) {
				fadeVolume = volume;
			}
			while (fadeVolume > FADE_FLOOR_HUNDREDTHS_DB) {
				fadeVolume -= FADE_STEP_HUNDREDTHS_DB;
				synchronized (this) {
					if (requestGeneration != generation) {
						return;
					}
					applyMidiVolume(fadeVolume);
				}
				try {
					Thread.sleep(FADE_STEP_MILLIS);
				} catch (InterruptedException exception) {
					Thread.currentThread().interrupt();
					return;
				}
			}
			synchronized (this) {
				if (requestGeneration != generation) {
					return;
				}
				try {
					replaceSequence(sequence, requestedVolume);
				} catch (MidiUnavailableException | InvalidMidiDataException exception) {
					report("MIDI", exception);
				}
			}
		}

		private void replaceSequence(Sequence sequence, int requestedVolume)
				throws MidiUnavailableException, InvalidMidiDataException {
			closeDevices();
			Sequencer nextSequencer = null;
			Synthesizer nextSynthesizer = null;
			Transmitter nextTransmitter = null;
			try {
				nextSequencer = MidiSystem.getSequencer(false);
				nextSynthesizer = MidiSystem.getSynthesizer();
				if (nextSequencer == null || nextSynthesizer == null) {
					throw new MidiUnavailableException("No Java Sound MIDI sequencer/synthesizer available");
				}
				nextSequencer.open();
				nextSynthesizer.open();
				nextTransmitter = nextSequencer.getTransmitter();
				nextTransmitter.setReceiver(nextSynthesizer.getReceiver());
				nextSequencer.setSequence(sequence);
				sequencer = nextSequencer;
				synthesizer = nextSynthesizer;
				transmitter = nextTransmitter;
			} catch (MidiUnavailableException exception) {
				closeTemporary(nextTransmitter, nextSequencer, nextSynthesizer);
				Sequencer fallback = MidiSystem.getSequencer();
				if (fallback == null) {
					throw exception;
				}
				fallback.open();
				fallback.setSequence(sequence);
				sequencer = fallback;
			} catch (InvalidMidiDataException exception) {
				closeTemporary(nextTransmitter, nextSequencer, nextSynthesizer);
				throw exception;
			}
			volume = requestedVolume;
			applyMidiVolume(requestedVolume);
			sequencer.start();
		}

		private static void closeTemporary(Transmitter transmitter, Sequencer sequencer, Synthesizer synthesizer) {
			if (transmitter != null) {
				transmitter.close();
			}
			if (sequencer != null) {
				sequencer.close();
			}
			if (synthesizer != null) {
				synthesizer.close();
			}
		}

		private void applyMidiVolume(int requestedVolume) {
			if (synthesizer == null || !synthesizer.isOpen()) {
				return;
			}
			int controllerVolume = toMidiControllerVolume(requestedVolume);
			for (MidiChannel channel : synthesizer.getChannels()) {
				if (channel != null) {
					channel.controlChange(7, controllerVolume);
				}
			}
		}

		private void closeDevices() {
			if (sequencer != null) {
				sequencer.stop();
				sequencer.close();
				sequencer = null;
			}
			if (transmitter != null) {
				transmitter.close();
				transmitter = null;
			}
			if (synthesizer != null) {
				synthesizer.close();
				synthesizer = null;
			}
		}
	}
}