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

/**
 * Revision-377 asynchronous platform helper.
 *
 * <p>The historical client called this class {@code signlink}. In this
 * standalone build it retains the cache, socket, DNS, worker-thread and
 * sound/MIDI file-request behavior, but no longer contains applet-relative
 * URL operations.</p>
 */
public final class Signlink implements Runnable {

    public static final int CLIENT_VERSION = 377;
    private static final int CACHE_INDEX_COUNT = 5;
    private static final long MAX_CACHE_DATA_LENGTH = 0x3200000L;
    private static final int MAX_SAVE_LENGTH = 0x1e8480;
    private static final int AUDIO_FILE_SLOTS = 5;
    private static final long POLL_INTERVAL_MILLIS = 50L;

    public static int uid;
    public static int storeId = 32;
    public static RandomAccessFile cacheData;
    public static RandomAccessFile[] cacheIndexes = new RandomAccessFile[CACHE_INDEX_COUNT];

    private static boolean active;
    private static int workerGeneration;

    private static InetAddress socketAddress;
    private static int socketRequestPort;
    private static Socket requestedSocket;

    private static int threadRequestPriority = 1;
    private static Runnable threadRequest;

    private static String dnsRequest;
    public static String dns;

    private static int saveLength;
    private static String saveRequest;
    private static byte[] saveBuffer;

    public static boolean midiPlayPending;
    private static int midiPosition;
    public static String midi;
    public static int midiVolume;
    public static int midiFade;

    private static boolean wavePlayPending;
    private static int wavePosition;
    public static String wave;
    public static int waveVolume;

    public static boolean reportErrors = true;

    private Signlink() {
    }

    /**
     * Starts a fresh signlink worker and waits until it has initialized.
     */
    public static void start(InetAddress address) {
        workerGeneration = (int) (Math.random() * 99999999D);
        if (active) {
            try {
                Thread.sleep(500L);
            } catch (Exception ignored) {
            }
            active = false;
        }

        socketRequestPort = 0;
        threadRequest = null;
        dnsRequest = null;
        saveRequest = null;
        socketAddress = address;

        Thread thread = new Thread(new Signlink());
        thread.setDaemon(true);
        thread.start();

        while (!active) {
            try {
                Thread.sleep(POLL_INTERVAL_MILLIS);
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void run() {
        active = true;
        String cacheDirectory = findCacheDirectory();
        uid = getUid(cacheDirectory);

        try {
            File dataFile = new File(cacheDirectory + "main_file_cache.dat");
            if (dataFile.exists() && dataFile.length() > MAX_CACHE_DATA_LENGTH) {
                dataFile.delete();
            }
            cacheData = new RandomAccessFile(dataFile, "rw");
            for (int index = 0; index < CACHE_INDEX_COUNT; index++) {
                cacheIndexes[index] = new RandomAccessFile(
                        cacheDirectory + "main_file_cache.idx" + index, "rw");
            }
        } catch (Exception exception) {
            exception.printStackTrace();
        }

        for (int generation = workerGeneration; workerGeneration == generation;) {
            if (socketRequestPort != 0) {
                try {
                    requestedSocket = new Socket(socketAddress, socketRequestPort);
                } catch (Exception ignored) {
                    requestedSocket = null;
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
                if (saveBuffer != null) {
                    try (FileOutputStream output = new FileOutputStream(cacheDirectory + saveRequest)) {
                        output.write(saveBuffer, 0, saveLength);
                    } catch (Exception ignored) {
                    }
                }

                if (wavePlayPending) {
                    wave = cacheDirectory + saveRequest;
                    wavePlayPending = false;
                }
                if (midiPlayPending) {
                    midi = cacheDirectory + saveRequest;
                    midiPlayPending = false;
                }
                saveRequest = null;
            }

            try {
                Thread.sleep(POLL_INTERVAL_MILLIS);
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Returns the cache directory selected by the supplied standalone client.
     *
     * <p>This deliberately preserves the user's current fixed relative cache
     * path rather than restoring the old platform-directory search.</p>
     */
    public static String findCacheDirectory() {
        return "./rscache/";
    }

    /**
     * Reads or creates the historical four-byte installation UID and returns
     * the stored value plus one.
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

        try (DataInputStream input = new DataInputStream(
                new FileInputStream(cacheDirectory + "uid.dat"))) {
            return input.readInt() + 1;
        } catch (Exception ignored) {
            return 0;
        }
    }

    /** Opens a socket on the signlink worker thread and blocks for its result. */
    public static synchronized Socket openSocket(int port) throws IOException {
        for (socketRequestPort = port; socketRequestPort != 0;) {
            try {
                Thread.sleep(POLL_INTERVAL_MILLIS);
            } catch (Exception ignored) {
            }
        }

        if (requestedSocket == null) {
            throw new IOException("could not open socket");
        }
        return requestedSocket;
    }

    /** Queues a reverse/host-name lookup and immediately exposes the query text. */
    public static synchronized void lookupDns(String address) {
        dns = address;
        dnsRequest = address;
    }

    /** Queues creation of a daemon worker thread at the requested priority. */
    public static synchronized void startThread(Runnable runnable, int priority) {
        threadRequestPriority = priority;
        threadRequest = runnable;
    }

    /** Queues a WAV file save using the original five-slot filename ring. */
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

    /** Queues the most recently selected WAV file for replay without rewriting it. */
    public static synchronized boolean replayWave() {
        if (saveRequest != null) {
            return false;
        }

        saveBuffer = null;
        wavePlayPending = true;
        saveRequest = "sound" + wavePosition + ".wav";
        return true;
    }

    /** Queues a MIDI file save using the original five-slot filename ring. */
    public static synchronized void saveMidi(byte[] data, int length) {
        if (length > MAX_SAVE_LENGTH || saveRequest != null) {
            return;
        }

        midiPosition = (midiPosition + 1) % AUDIO_FILE_SLOTS;
        saveLength = length;
        saveBuffer = data;
        midiPlayPending = true;
        saveRequest = "jingle" + midiPosition + ".mid";
    }

    /**
     * Reports a client error locally.
     *
     * <p>The applet-relative HTTP report path was already non-functional after
     * the Canvas conversion, because there is no code base from which to open
     * the relative CGI URL. Standalone error reporting therefore retains its
     * observable console behavior only.</p>
     */
    public static void reportError(String message) {
        if (!reportErrors || !active) {
            return;
        }
        System.out.println("Error: " + message);
    }
}