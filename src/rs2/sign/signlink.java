package rs2.sign;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.net.Socket;

import rs2.sound.JavaSoundAudioPlayer;

/**
 * Revision-377 asynchronous platform helper.
 *
 * <p>
 * The historical client called this class {@code signlink}. In this standalone
 * build it retains the cache, socket, DNS, worker-thread and sound/MIDI
 * file-request behavior, and routes completed audio requests to the standalone
 * Java Sound backend. It no longer contains applet-relative URL operations.
 * </p>
 */
public final class Signlink implements Runnable {

	/** Constant value for client version. */
	public static final int CLIENT_VERSION = 377;
	/** Constant value for cache index count. */
	private static final int CACHE_INDEX_COUNT = 5;
	/** Maximum cache data length. */
	private static final long MAX_CACHE_DATA_LENGTH = 0x3200000L;
	/** Maximum save length. */
	private static final int MAX_SAVE_LENGTH = 0x1e8480;
	/** Constant value for audio file slots. */
	private static final int AUDIO_FILE_SLOTS = 5;
	/** Constant value for poll interval millis. */
	private static final long POLL_INTERVAL_MILLIS = 50L;
	/**
	 * Lifecycle lock.
	 *
	 */
	private static final Object LIFECYCLE_LOCK = new Object();
	/** Java Sound backend used for wave and MIDI playback. */
	private static volatile JavaSoundAudioPlayer audioPlayer = new JavaSoundAudioPlayer();

	/** Stores the current UID. */
	public static int uid;
	/** Stores the current store ID. */
	public static int storeId = 32;
	/** Stores the current cache data. */
	public static volatile RandomAccessFile cacheData;
	/** Stores cache indexes values. */
	public static RandomAccessFile[] cacheIndexes = new RandomAccessFile[CACHE_INDEX_COUNT];

	/** Whether active is enabled or active. */
	private static volatile boolean active;
	/** Whether initialized is enabled or active. */
	private static volatile boolean initialized;
	/** Stores the current worker generation. */
	private static volatile int workerGeneration;
	/** Stores the current worker thread. */
	private static volatile Thread workerThread;

	/** Stores the current socket address. */
	private static volatile InetAddress socketAddress;
	/** Stores the current socket request port. */
	private static volatile int socketRequestPort;
	/** Stores the current requested socket. */
	private static volatile Socket requestedSocket;

	/** Stores the current thread request priority. */
	private static int threadRequestPriority = 1;
	/** Stores the current thread request. */
	private static volatile Runnable threadRequest;

	/** Stores the current dns request. */
	private static volatile String dnsRequest;
	/** Stores the current dns. */
	public static volatile String dns;

	/** Stores the current save length. */
	private static int saveLength;
	/** Stores the current save request. */
	private static volatile String saveRequest;
	/** Stores save buffer values. */
	private static byte[] saveBuffer;

	/** Whether MIDI play pending is enabled or active. */
	public static volatile boolean midiPlayPending;
	/** Stores the current MIDI position. */
	private static int midiPosition;
	/** Stores the current MIDI. */
	public static volatile String midi;
	/** Stores the current MIDI volume. */
	public static int midiVolume;
	/** Stores the current MIDI fade. */
	public static int midiFade;

	/** Whether wave play pending is enabled or active. */
	private static volatile boolean wavePlayPending;
	/** Stores the current wave position. */
	private static int wavePosition;
	/** Stores the current wave. */
	public static volatile String wave;
	/** Stores the current wave volume. */
	public static int waveVolume;

	/** Whether report errors is enabled or active. */
	public static boolean reportErrors = true;
	/** Stores the current cache directory. */
	private static volatile String cacheDirectory = "./rscache/";

	/** Stores the current generation. */
	private final int generation;

	/**
	 * Creates a new signlink.
	 *
	 * @param generation the generation
	 */
	private Signlink(int generation) {
		this.generation = generation;
	}

	/**
	 * Starts a fresh signlink worker and waits until it has initialized.
	 * 
	 * @param address the address
	 */
	public static void start(InetAddress address) {
		stopWorker(false);

		Thread thread;
		int generation = (int) (Math.random() * 99999999D);
		synchronized (LIFECYCLE_LOCK) {
			workerGeneration = generation;
			initialized = false;
			active = false;
			socketRequestPort = 0;
			requestedSocket = null;
			threadRequest = null;
			dnsRequest = null;
			saveRequest = null;
			socketAddress = address;
			if (audioPlayer == null) {
				audioPlayer = new JavaSoundAudioPlayer();
			}

			thread = new Thread(new Signlink(generation), "rs2-signlink");
			thread.setDaemon(true);
			workerThread = thread;
			thread.start();
		}

		while (!initialized && thread.isAlive()) {
			try {
				Thread.sleep(POLL_INTERVAL_MILLIS);
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				break;
			}
		}
	}

	@Override
	public void run() {
		String cacheDirectory = findCacheDirectory();
		uid = getUid(cacheDirectory);

		try {
			File dataFile = new File(cacheDirectory + "main_file_cache.dat");
			if (dataFile.exists() && dataFile.length() > MAX_CACHE_DATA_LENGTH) {
				dataFile.delete();
			}
			cacheData = new RandomAccessFile(dataFile, "rw");
			for (int index = 0; index < CACHE_INDEX_COUNT; index++) {
				cacheIndexes[index] = new RandomAccessFile(cacheDirectory + "main_file_cache.idx" + index, "rw");
			}
		} catch (Exception exception) {
			exception.printStackTrace();
		} finally {
			active = true;
			initialized = true;
		}

		try {
			while (workerGeneration == generation && !Thread.currentThread().isInterrupted()) {
				if (socketRequestPort != 0) {
					Socket socket = null;
					try {
						socket = new Socket(socketAddress, socketRequestPort);
					} catch (Exception ignored) {
					}
					if (workerGeneration == generation && !Thread.currentThread().isInterrupted()) {
						requestedSocket = socket;
					} else if (socket != null) {
						try {
							socket.close();
						} catch (IOException ignored) {
						}
					}
					socketRequestPort = 0;
				} else if (threadRequest != null) {
					Thread thread = new Thread(threadRequest);
					thread.setDaemon(true);
					thread.start();
					thread.setPriority(threadRequestPriority);
					threadRequest = null;
				} else if (dnsRequest != null) {
					try {
						dns = InetAddress.getByName(dnsRequest).getHostName();
					} catch (Exception ignored) {
						dns = "unknown";
					}
					dnsRequest = null;
				} else if (saveRequest != null) {
					File audioFile = new File(cacheDirectory + saveRequest);
					if (saveBuffer != null) {
						try (FileOutputStream output = new FileOutputStream(audioFile)) {
							output.write(saveBuffer, 0, saveLength);
						} catch (Exception ignored) {
						}
					}

					JavaSoundAudioPlayer player = audioPlayer;
					if (wavePlayPending) {
						wave = audioFile.getPath();
						wavePlayPending = false;
						if (player != null) {
							player.playWave(audioFile, waveVolume);
						}
					}
					if (midiPlayPending) {
						midi = audioFile.getPath();
						midiPlayPending = false;
						if (player != null) {
							player.playMidi(audioFile, midiVolume, midiFade != 0);
						}
					}
					saveRequest = null;
				}

				try {
					Thread.sleep(POLL_INTERVAL_MILLIS);
				} catch (InterruptedException exception) {
					Thread.currentThread().interrupt();
					break;
				}
			}
		} finally {
			active = false;
			synchronized (LIFECYCLE_LOCK) {
				if (workerThread == Thread.currentThread()) {
					workerThread = null;
				}
			}
		}
	}

	/**
	 * Returns the cache directory selected by the supplied standalone client.
	 *
	 * <p>
	 * This deliberately preserves the user's current fixed relative cache path
	 * rather than restoring the old platform-directory search.
	 * </p>
	 * 
	 * @return the cache directory result
	 */
	public static String findCacheDirectory() {
		return cacheDirectory;
	}

	/**
	 * Selects the disk-cache directory before {@link #start(InetAddress)} is
	 * called.
	 * 
	 * @param directory the directory
	 */
	public static void setCacheDirectory(String directory) {
		if (directory == null || directory.trim().isEmpty()) {
			throw new IllegalArgumentException("cache directory must not be empty");
		}
		String normalized = new File(directory).getPath();
		if (!normalized.endsWith(File.separator)) {
			normalized += File.separator;
		}
		cacheDirectory = normalized;
	}

	/**
	 * Reads or creates the historical four-byte installation UID and returns the
	 * stored value plus one.
	 * 
	 * @param cacheDirectory the cache directory
	 * @return the UID
	 */
	public static int getUid(String cacheDirectory) {
		try {
			File file = new File(cacheDirectory + "uid.dat");
			if (!file.exists() || file.length() < 4L) {
				try (DataOutputStream output = new DataOutputStream(new FileOutputStream(file))) {
					output.writeInt((int) (Math.random() * 99999999D));
				}
			}
		} catch (Exception ignored) {
		}

		try (DataInputStream input = new DataInputStream(new FileInputStream(cacheDirectory + "uid.dat"))) {
			return input.readInt() + 1;
		} catch (Exception ignored) {
			return 0;
		}
	}

	/**
	 * Opens a socket on the signlink worker thread and blocks for its result.
	 * 
	 * @param port the network port
	 * @return the connected socket
	 * @throws IOException if an I/O operation fails
	 */
	public static synchronized Socket openSocket(int port) throws IOException {
		requestedSocket = null;
		socketRequestPort = port;
		while (socketRequestPort != 0 && active) {
			try {
				Thread.sleep(POLL_INTERVAL_MILLIS);
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				throw new IOException("interrupted while opening socket", exception);
			}
		}

		Socket socket = requestedSocket;
		requestedSocket = null;
		if (socket == null) {
			throw new IOException("could not open socket");
		}
		return socket;
	}

	/**
	 * Queues a reverse/host-name lookup and immediately exposes the query text.
	 * 
	 * @param address the address
	 */
	public static synchronized void lookupDns(String address) {
		dns = address;
		dnsRequest = address;
	}

	/**
	 * Queues creation of a daemon worker thread at the requested priority.
	 * 
	 * @param runnable the runnable
	 * @param priority the request priority
	 */
	public static synchronized void startThread(Runnable runnable, int priority) {
		threadRequestPriority = priority;
		threadRequest = runnable;
	}

	/**
	 * Applies the legacy WAV attenuation to the standalone Java Sound player.
	 * 
	 * @param volume the volume
	 */
	public static synchronized void setWaveVolume(int volume) {
		waveVolume = volume;
		JavaSoundAudioPlayer player = audioPlayer;
		if (player != null) {
			player.setWaveVolume(volume);
		}
	}

	/**
	 * Applies the legacy MIDI attenuation, optionally updating the live track.
	 * 
	 * @param volume             the volume
	 * @param adjustPlayingTrack the adjust playing track
	 */
	public static synchronized void setMidiVolume(int volume, boolean adjustPlayingTrack) {
		midiVolume = volume;
		if (adjustPlayingTrack) {
			midi = "voladjust";
			JavaSoundAudioPlayer player = audioPlayer;
			if (player != null) {
				player.setMidiVolume(volume);
			}
		}
	}

	/**
	 * Stops standalone MIDI playback and preserves the historical control marker.
	 */
	public static synchronized void stopMidi() {
		midiPlayPending = false;
		midiFade = 0;
		midi = "stop";
		JavaSoundAudioPlayer player = audioPlayer;
		if (player != null) {
			player.stopMidi();
		}
	}

	/**
	 * Queues a WAV file save using the original five-slot filename ring.
	 * 
	 * @param data   the data to process
	 * @param length the number of elements or bytes
	 * @return whether save wave
	 */
	public static synchronized boolean saveWave(byte[] data, int length) {
		if (length > MAX_SAVE_LENGTH) {
			return false;
		}
		if (saveRequest != null) {
			return false;
		}

		wavePosition = (wavePosition + 1) % AUDIO_FILE_SLOTS;
		saveLength = length;
		saveBuffer = data;
		wavePlayPending = true;
		saveRequest = "sound" + wavePosition + ".wav";
		return true;
	}

	/**
	 * Queues the most recently selected WAV file for replay without rewriting it.
	 * 
	 * @return whether replay wave
	 */
	public static synchronized boolean replayWave() {
		if (saveRequest != null) {
			return false;
		}

		saveBuffer = null;
		wavePlayPending = true;
		saveRequest = "sound" + wavePosition + ".wav";
		return true;
	}

	/**
	 * Queues a MIDI file save using the original five-slot filename ring.
	 * 
	 * @param data   the data to process
	 * @param length the number of elements or bytes
	 * @param fade   the fade
	 */
	public static synchronized void saveMidi(byte[] data, int length, boolean fade) {
		if (length > MAX_SAVE_LENGTH || saveRequest != null) {
			return;
		}

		midiPosition = (midiPosition + 1) % AUDIO_FILE_SLOTS;
		saveLength = length;
		saveBuffer = data;
		midiFade = fade ? 1 : 0;
		midiPlayPending = true;
		saveRequest = "jingle" + midiPosition + ".mid";
	}

	/**
	 * Compatibility overload retaining the currently selected fade mode.
	 * 
	 * @param data   the data to process
	 * @param length the number of elements or bytes
	 */
	public static synchronized void saveMidi(byte[] data, int length) {
		saveMidi(data, length, midiFade != 0);
	}

	/** Stops the platform worker, closes cache files, and releases Java Sound. */
	public static void shutdown() {
		stopWorker(true);
	}

	/**
	 * Stops worker.
	 *
	 * @param closeAudio the close audio
	 */
	private static void stopWorker(boolean closeAudio) {
		Thread thread;
		Socket staleSocket;
		synchronized (LIFECYCLE_LOCK) {
			workerGeneration++;
			active = false;
			initialized = false;
			socketRequestPort = 0;
			threadRequest = null;
			dnsRequest = null;
			saveRequest = null;
			wavePlayPending = false;
			midiPlayPending = false;
			staleSocket = requestedSocket;
			requestedSocket = null;
			thread = workerThread;
		}

		if (staleSocket != null) {
			try {
				staleSocket.close();
			} catch (IOException ignored) {
			}
		}
		if (thread != null && thread != Thread.currentThread()) {
			thread.interrupt();
			boolean interrupted = false;
			for (;;) {
				try {
					thread.join();
					break;
				} catch (InterruptedException exception) {
					interrupted = true;
				}
			}
			if (interrupted) {
				Thread.currentThread().interrupt();
			}
		}

		Socket lateSocket = requestedSocket;
		requestedSocket = null;
		if (lateSocket != null) {
			try {
				lateSocket.close();
			} catch (IOException ignored) {
			}
		}
		closeCacheFiles();
		if (closeAudio) {
			JavaSoundAudioPlayer player;
			synchronized (LIFECYCLE_LOCK) {
				player = audioPlayer;
				audioPlayer = null;
			}
			if (player != null) {
				player.close();
			}
		}
	}

	/**
	 * Closes cache files.
	 */
	private static void closeCacheFiles() {
		RandomAccessFile data = cacheData;
		cacheData = null;
		if (data != null) {
			try {
				data.close();
			} catch (IOException ignored) {
			}
		}
		for (int index = 0; index < cacheIndexes.length; index++) {
			RandomAccessFile file = cacheIndexes[index];
			cacheIndexes[index] = null;
			if (file != null) {
				try {
					file.close();
				} catch (IOException ignored) {
				}
			}
		}
	}

	/**
	 * Reports a client error locally.
	 *
	 * <p>
	 * The applet-relative HTTP report path was already non-functional after the
	 * Canvas conversion, because there is no code base from which to open the
	 * relative CGI URL. Standalone error reporting therefore retains its observable
	 * console behavior only.
	 * </p>
	 * 
	 * @param message the message text
	 */
	public static void reportError(String message) {
		if (!reportErrors || !active) {
			return;
		}
		System.out.println("Error: " + message);
	}
}
