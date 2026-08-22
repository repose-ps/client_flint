package rs2.cache.ondemand;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.zip.CRC32;
import java.util.zip.GZIPInputStream;

import rs2.client;
import rs2.cache.Archive;
import rs2.collection.DualNodeDeque;
import rs2.collection.NodeDeque;
import rs2.net.Buffer;
import rs2.sign.Signlink;

/**
 * Revision-377 on-demand cache and update-server loader.
 *
 * <p>
 * Files are addressed by a type/id pair. Types 0 through 3 are models,
 * animation frames, MIDI tracks, and map files respectively. Cached bytes are
 * validated using the version list's trailing two-byte version and CRC-32
 * before a network request is made.
 * </p>
 *
 * <p>
 * The update-server response protocol uses a six-byte header followed by chunks
 * of at most 500 bytes. Completed cache/update-server payloads remain
 * GZIP-compressed until {@link #poll()} returns them to the client.
 * </p>
 */
public class OnDemandFetcher extends OnDemandProvider implements Runnable {

	public static final int MODEL = 0;
	public static final int ANIMATION = 1;
	public static final int MIDI = 2;
	public static final int MAP = 3;

	private static final int ARCHIVE_TYPE_COUNT = 4;
	private static final int MAX_ACTIVE_REQUESTS = 10;
	private static final int RESPONSE_HEADER_LENGTH = 6;
	private static final int RESPONSE_CHUNK_LENGTH = 500;
	private static final int GZIP_BUFFER_LENGTH = 65_000;
	private static final int RESEND_AFTER_CYCLES = 50;
	private static final int DISCONNECT_AFTER_IDLE_CYCLES = 750;
	private static final int KEEP_ALIVE_AFTER_CYCLES = 500;
	private static final long SOCKET_REOPEN_DELAY_MILLIS = 4_000L;
	private static final int UPDATE_SERVER_HANDSHAKE = 15;
	private static final int LOCATION_PREFETCH_TYPE = 93;

	private void readData() {
		try {
			int available = inputStream.available();
			if (currentChunkLength == 0 && available >= RESPONSE_HEADER_LENGTH) {
				waiting = true;
				for (int read = 0; read < RESPONSE_HEADER_LENGTH; read += inputStream.read(ioBuffer, read,
						RESPONSE_HEADER_LENGTH - read)) {
					// Preserve the original blocking fill loop after available() admits a header.
				}

				int type = ioBuffer[0] & 0xff;
				int id = ((ioBuffer[1] & 0xff) << 8) + (ioBuffer[2] & 0xff);
				int fileLength = ((ioBuffer[3] & 0xff) << 8) + (ioBuffer[4] & 0xff);
				int chunk = ioBuffer[5] & 0xff;
				currentRequest = null;
				for (OnDemandRequest request = (OnDemandRequest) networkRequests
						.first(); request != null; request = (OnDemandRequest) networkRequests.next()) {
					if (request.type == type && request.id == id) {
						currentRequest = request;
					}
					/*
					 * Original quirk: once the matching request is encountered, every subsequently
					 * traversed request also has its resend counter reset.
					 */
					if (currentRequest != null) {
						request.loopCycle = 0;
					}
				}

				if (currentRequest != null) {
					idleCycles = 0;
					if (fileLength == 0) {
						Signlink.reportError("Rej: " + type + "," + id);
						currentRequest.buffer = null;
						if (currentRequest.incomplete) {
							synchronized (completedQueue) {
								completedQueue.addLast(currentRequest);
							}
						} else {
							currentRequest.unlink();
						}
						currentRequest = null;
					} else {
						if (currentRequest.buffer == null && chunk == 0) {
							currentRequest.buffer = new byte[fileLength];
						}
						if (currentRequest.buffer == null && chunk != 0) {
							throw new IOException("missing start of file");
						}
					}
				}

				currentChunkOffset = chunk * RESPONSE_CHUNK_LENGTH;
				currentChunkLength = RESPONSE_CHUNK_LENGTH;
				if (currentChunkLength > fileLength - chunk * RESPONSE_CHUNK_LENGTH) {
					currentChunkLength = fileLength - chunk * RESPONSE_CHUNK_LENGTH;
				}
			}

			if (currentChunkLength > 0 && available >= currentChunkLength) {
				waiting = true;
				byte[] destination = ioBuffer;
				int destinationOffset = 0;
				if (currentRequest != null) {
					destination = currentRequest.buffer;
					destinationOffset = currentChunkOffset;
				}
				for (int read = 0; read < currentChunkLength; read += inputStream.read(destination,
						destinationOffset + read, currentChunkLength - read)) {
					// Preserve the original fill-loop semantics.
				}

				if (currentChunkLength + currentChunkOffset >= destination.length && currentRequest != null) {
					if (clientInstance.aClass23Array1228[0] != null) {
						clientInstance.aClass23Array1228[currentRequest.type + 1].write(currentRequest.id, destination);
					}
					if (!currentRequest.incomplete && currentRequest.type == MAP) {
						currentRequest.incomplete = true;
						currentRequest.type = LOCATION_PREFETCH_TYPE;
					}
					if (currentRequest.incomplete) {
						synchronized (completedQueue) {
							completedQueue.addLast(currentRequest);
						}
					} else {
						currentRequest.unlink();
					}
				}
				currentChunkLength = 0;
			}
		} catch (IOException exception) {
			try {
				socket.close();
			} catch (Exception ignored) {
			}
			socket = null;
			inputStream = null;
			outputStream = null;
			currentChunkLength = 0;
		}
	}

	public int getModelIndex(int modelId) {
		return modelIndices[modelId] & 0xff;
	}

	@Override
	public void requestModel(int modelId) {
		request(MODEL, modelId);
	}

	private void processExtraRequests() {
		while (mandatoryRequestCount == 0 && extraRequestCount < MAX_ACTIVE_REQUESTS) {
			if (highestPriority == 0) {
				break;
			}

			OnDemandRequest request;
			synchronized (extraRequestQueue) {
				request = (OnDemandRequest) extraRequestQueue.removeFirst();
			}
			while (request != null) {
				if (fileStatus[request.type][request.id] != 0) {
					fileStatus[request.type][request.id] = 0;
					networkRequests.addLast(request);
					sendRequest(request);
					waiting = true;
					if (filesLoaded < totalFiles) {
						filesLoaded++;
					}
					statusString = "Loading extra files - " + (filesLoaded * 100) / totalFiles + "%";
					extraRequestCount++;
					if (extraRequestCount == MAX_ACTIVE_REQUESTS) {
						return;
					}
				}
				synchronized (extraRequestQueue) {
					request = (OnDemandRequest) extraRequestQueue.removeFirst();
				}
			}

			for (int type = 0; type < ARCHIVE_TYPE_COUNT; type++) {
				byte[] statuses = fileStatus[type];
				for (int id = 0; id < statuses.length; id++) {
					if (statuses[id] == highestPriority) {
						statuses[id] = 0;
						OnDemandRequest priorityRequest = new OnDemandRequest();
						priorityRequest.type = type;
						priorityRequest.id = id;
						priorityRequest.incomplete = false;
						networkRequests.addLast(priorityRequest);
						sendRequest(priorityRequest);
						waiting = true;
						if (filesLoaded < totalFiles) {
							filesLoaded++;
						}
						statusString = "Loading extra files - " + (filesLoaded * 100) / totalFiles + "%";
						extraRequestCount++;
						if (extraRequestCount == MAX_ACTIVE_REQUESTS) {
							return;
						}
					}
				}
			}
			highestPriority--;
		}
	}

	public void setExtraPriority(int type, int id, byte priority) {
		if (clientInstance.aClass23Array1228[0] == null)
			return;
		if (versions[type][id] == 0)
			return;
		byte[] bytes = clientInstance.aClass23Array1228[type + 1].read(id);
		if (crcMatches(bytes, versions[type][id], crcs[type][id]))
			return;
		fileStatus[type][id] = priority;
		if (priority > highestPriority)
			highestPriority = priority;
		totalFiles++;
	}

	public boolean isMidiPreload(int id) {
		return midiPreloadFlags[id] == 1;
	}

	public void request(int type, int id) {
		if (type < 0 || type > versions.length || id < 0 || id > versions[type].length)
			return;
		if (versions[type][id] == 0)
			return;
		synchronized (outstandingRequests) {
			for (OnDemandRequest request = (OnDemandRequest) outstandingRequests
					.first(); request != null; request = (OnDemandRequest) outstandingRequests.next()) {
				if (request.type == type && request.id == id) {
					return;
				}
			}

			OnDemandRequest request = new OnDemandRequest();
			request.type = type;
			request.id = id;
			request.incomplete = true;
			synchronized (cacheRequestQueue) {
				cacheRequestQueue.addLast(request);
			}
			outstandingRequests.addLast(request);
		}
	}

	public OnDemandRequest poll() {
		OnDemandRequest request;
		synchronized (completedQueue) {
			request = (OnDemandRequest) completedQueue.removeFirst();
		}
		if (request == null) {
			return null;
		}
		synchronized (outstandingRequests) {
			request.unlinkDual();
		}
		if (request.buffer == null) {
			return request;
		}

		int length = 0;
		try {
			GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(request.buffer));
			do {
				if (length == gzipBuffer.length) {
					throw new RuntimeException("buffer overflow!");
				}
				int read = gzip.read(gzipBuffer, length, gzipBuffer.length - length);
				if (read == -1) {
					break;
				}
				length += read;
			} while (true);
		} catch (IOException exception) {
			throw new RuntimeException("error unzipping");
		}

		request.buffer = new byte[length];
		System.arraycopy(gzipBuffer, 0, request.buffer, 0, length);
		return request;
	}

	public void run() {
		try {
			while (running) {
				onDemandCycle++;
				int sleepMillis = 20;
				if (highestPriority == 0 && clientInstance.aClass23Array1228[0] != null)
					sleepMillis = 50;
				try {
					Thread.sleep(sleepMillis);
				} catch (Exception _ex) {
				}
				waiting = true;
				for (int iteration = 0; iteration < 100; iteration++) {
					if (!waiting)
						break;
					waiting = false;
					checkCache();
					handleFailedRequests();
					if (mandatoryRequestCount == 0 && iteration >= 5)
						break;
					processExtraRequests();
					if (inputStream != null)
						readData();
				}

				boolean hasPendingRequests = false;
				for (OnDemandRequest request = (OnDemandRequest) networkRequests
						.first(); request != null; request = (OnDemandRequest) networkRequests.next()) {
					if (request.incomplete) {
						hasPendingRequests = true;
						request.loopCycle++;
						if (request.loopCycle > RESEND_AFTER_CYCLES) {
							request.loopCycle = 0;
							sendRequest(request);
						}
					}
				}

				if (!hasPendingRequests) {
					for (OnDemandRequest request = (OnDemandRequest) networkRequests
							.first(); request != null; request = (OnDemandRequest) networkRequests.next()) {
						hasPendingRequests = true;
						request.loopCycle++;
						if (request.loopCycle > RESEND_AFTER_CYCLES) {
							request.loopCycle = 0;
							sendRequest(request);
						}
					}

				}
				if (hasPendingRequests) {
					idleCycles++;
					if (idleCycles > DISCONNECT_AFTER_IDLE_CYCLES) {
						try {
							socket.close();
						} catch (Exception _ex) {
						}
						socket = null;
						inputStream = null;
						outputStream = null;
						currentChunkLength = 0;
					}
				} else {
					idleCycles = 0;
					statusString = "";
				}
				if (clientInstance.aBoolean1137 && socket != null && outputStream != null
						&& (highestPriority > 0 || clientInstance.aClass23Array1228[0] == null)) {
					keepAliveCycles++;
					if (keepAliveCycles > KEEP_ALIVE_AFTER_CYCLES) {
						keepAliveCycles = 0;
						ioBuffer[0] = 0;
						ioBuffer[1] = 0;
						ioBuffer[2] = 0;
						ioBuffer[3] = 10;
						try {
							outputStream.write(ioBuffer, 0, 4);
						} catch (IOException _ex) {
							idleCycles = 5000;
						}
					}
				}
			}
			return;
		} catch (Exception exception) {
			Signlink.reportError("od_ex " + exception.getMessage());
		}
	}

	private void handleFailedRequests() {
		mandatoryRequestCount = 0;
		extraRequestCount = 0;
		for (OnDemandRequest request = (OnDemandRequest) networkRequests
				.first(); request != null; request = (OnDemandRequest) networkRequests.next()) {
			if (request.incomplete) {
				mandatoryRequestCount++;
			} else {
				extraRequestCount++;
			}
		}

		while (mandatoryRequestCount < MAX_ACTIVE_REQUESTS) {
			OnDemandRequest request = (OnDemandRequest) missingRequestQueue.removeFirst();
			if (request == null) {
				break;
			}
			if (fileStatus[request.type][request.id] != 0) {
				filesLoaded++;
			}
			fileStatus[request.type][request.id] = 0;
			networkRequests.addLast(request);
			mandatoryRequestCount++;
			sendRequest(request);
			waiting = true;
		}
	}

	public void preloadMaps(boolean includeAllMaps) {
		for (int index = 0; index < regionIds.length; index++) {
			if (includeAllMaps || mapPreloadFlags[index] != 0) {
				setExtraPriority(MAP, landscapeFileIds[index], (byte) 2);
				setExtraPriority(MAP, terrainFileIds[index], (byte) 2);
			}
		}
	}

	public int getOutstandingRequestCount() {
		synchronized (outstandingRequests) {
			return outstandingRequests.size();
		}
	}

	public boolean isLandscapeFile(int fileId) {
		for (int index = 0; index < regionIds.length; index++) {
			if (landscapeFileIds[index] == fileId) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Loads the revision-377 version-list archive and starts the fetcher thread.
	 *
	 * <p>
	 * The archive contains four version tables and four CRC tables plus
	 * {@code model_index}, seven-byte {@code map_index} records,
	 * {@code anim_index}, and {@code midi_index}.
	 * </p>
	 */
	public void start(Archive archive, client clientInstance) {
		String[] versionNames = { "model_version", "anim_version", "midi_version", "map_version" };
		for (int type = 0; type < ARCHIVE_TYPE_COUNT; type++) {
			byte[] bytes = archive.read(versionNames[type]);
			Buffer buffer = new Buffer(bytes);
			versions[type] = new int[bytes.length / 2];
			fileStatus[type] = new byte[versions[type].length];
			for (int id = 0; id < versions[type].length; id++) {
				versions[type][id] = buffer.readUnsignedShort();
			}
		}

		String[] crcNames = { "model_crc", "anim_crc", "midi_crc", "map_crc" };
		for (int type = 0; type < ARCHIVE_TYPE_COUNT; type++) {
			byte[] bytes = archive.read(crcNames[type]);
			Buffer buffer = new Buffer(bytes);
			crcs[type] = new int[bytes.length / 4];
			for (int id = 0; id < crcs[type].length; id++) {
				crcs[type][id] = buffer.readInt();
			}
		}

		byte[] bytes = archive.read("model_index");
		modelIndices = new byte[versions[MODEL].length];
		for (int id = 0; id < modelIndices.length; id++) {
			modelIndices[id] = id < bytes.length ? bytes[id] : 0;
		}

		bytes = archive.read("map_index");
		Buffer buffer = new Buffer(bytes);
		int mapCount = bytes.length / 7;
		regionIds = new int[mapCount];
		terrainFileIds = new int[mapCount];
		landscapeFileIds = new int[mapCount];
		mapPreloadFlags = new int[mapCount];
		for (int index = 0; index < mapCount; index++) {
			regionIds[index] = buffer.readUnsignedShort();
			terrainFileIds[index] = buffer.readUnsignedShort();
			landscapeFileIds[index] = buffer.readUnsignedShort();
			mapPreloadFlags[index] = buffer.readUnsignedByte();
		}

		bytes = archive.read("anim_index");
		buffer = new Buffer(bytes);
		animationIndex = new int[bytes.length / 2];
		for (int index = 0; index < animationIndex.length; index++) {
			animationIndex[index] = buffer.readUnsignedShort();
		}

		bytes = archive.read("midi_index");
		buffer = new Buffer(bytes);
		midiPreloadFlags = new int[bytes.length];
		for (int id = 0; id < midiPreloadFlags.length; id++) {
			midiPreloadFlags[id] = buffer.readUnsignedByte();
		}

		this.clientInstance = clientInstance;
		running = true;
		this.clientInstance.startThread(this, 2);
	}

	public void clearExtraRequests() {
		synchronized (extraRequestQueue) {
			extraRequestQueue.clear();
		}
	}

	public void queueExtraRequest(int type, int id) {
		if (clientInstance.aClass23Array1228[0] == null)
			return;
		if (versions[type][id] == 0)
			return;
		if (fileStatus[type][id] == 0)
			return;
		if (highestPriority == 0)
			return;
		OnDemandRequest request = new OnDemandRequest();
		request.type = type;
		request.id = id;
		request.incomplete = false;
		synchronized (extraRequestQueue) {
			extraRequestQueue.addLast(request);
		}
	}

	private void checkCache() {
		OnDemandRequest request;
		synchronized (cacheRequestQueue) {
			request = (OnDemandRequest) cacheRequestQueue.removeFirst();
		}
		while (request != null) {
			waiting = true;
			byte[] bytes = null;
			if (clientInstance.aClass23Array1228[0] != null) {
				bytes = clientInstance.aClass23Array1228[request.type + 1].read(request.id);
			}
			if (!crcMatches(bytes, versions[request.type][request.id], crcs[request.type][request.id])) {
				bytes = null;
			}

			synchronized (cacheRequestQueue) {
				if (bytes == null) {
					missingRequestQueue.addLast(request);
				} else {
					request.buffer = bytes;
					synchronized (completedQueue) {
						completedQueue.addLast(request);
					}
				}
				request = (OnDemandRequest) cacheRequestQueue.removeFirst();
			}
		}
	}

	public void stop() {
		running = false;
	}

	public int getFileCount(int type) {
		return versions[type].length;
	}

	private boolean crcMatches(byte[] data, int expectedVersion, int expectedCrc) {
		if (data == null || data.length < 2)
			return false;
		int length = data.length - 2;
		int version = ((data[length] & 0xff) << 8) + (data[length + 1] & 0xff);
		crc32.reset();
		crc32.update(data, 0, length);
		int crc = (int) crc32.getValue();
		if (version != expectedVersion)
			return false;
		return crc == expectedCrc;
	}

	private void sendRequest(OnDemandRequest request) {
		try {
			if (socket == null) {
				long now = System.currentTimeMillis();
				if (now - lastSocketOpenTime < SOCKET_REOPEN_DELAY_MILLIS) {
					return;
				}
				lastSocketOpenTime = now;
				socket = clientInstance.method32(43594 + client.anInt924);
				inputStream = socket.getInputStream();
				outputStream = socket.getOutputStream();
				outputStream.write(UPDATE_SERVER_HANDSHAKE);
				for (int index = 0; index < 8; index++) {
					inputStream.read();
				}
				idleCycles = 0;
			}

			ioBuffer[0] = (byte) request.type;
			ioBuffer[1] = (byte) (request.id >> 8);
			ioBuffer[2] = (byte) request.id;
			if (request.incomplete) {
				ioBuffer[3] = 2;
			} else if (!clientInstance.aBoolean1137) {
				ioBuffer[3] = 1;
			} else {
				ioBuffer[3] = 0;
			}
			outputStream.write(ioBuffer, 0, 4);
			keepAliveCycles = 0;
			requestFailures = -10000;
			return;
		} catch (IOException ignored) {
		}

		try {
			socket.close();
		} catch (Exception ignored) {
		}
		socket = null;
		inputStream = null;
		outputStream = null;
		currentChunkLength = 0;
		requestFailures++;
	}

	public int getAnimationCount() {
		return animationIndex.length;
	}

	public int getMapFileId(int regionX, int regionY, int fileType) {
		int regionId = (regionX << 8) + regionY;
		for (int j1 = 0; j1 < regionIds.length; j1++)
			if (regionIds[j1] == regionId)
				if (fileType == 0)
					return terrainFileIds[j1];
				else
					return landscapeFileIds[j1];

		return -1;
	}

	public OnDemandFetcher() {
		fileStatus = new byte[ARCHIVE_TYPE_COUNT][];
		waiting = false;
		running = true;
		cacheRequestQueue = new NodeDeque();
		crcs = new int[ARCHIVE_TYPE_COUNT][];
		statusString = "";
		missingRequestQueue = new NodeDeque();
		crc32 = new CRC32();
		completedQueue = new NodeDeque();
		extraRequestQueue = new NodeDeque();
		gzipBuffer = new byte[GZIP_BUFFER_LENGTH];
		ioBuffer = new byte[RESPONSE_CHUNK_LENGTH];
		outstandingRequests = new DualNodeDeque();
		networkRequests = new NodeDeque();
		versions = new int[ARCHIVE_TYPE_COUNT][];
	}

	private int filesLoaded;
	private byte[] modelIndices;
	private int[] mapPreloadFlags;
	private byte[][] fileStatus;
	private boolean waiting;
	private boolean running;
	private NodeDeque cacheRequestQueue;
	private int highestPriority;
	private int mandatoryRequestCount;
	private int extraRequestCount;
	private int[][] crcs;
	private int[] regionIds;
	public String statusString;
	public int onDemandCycle;
	private OutputStream outputStream;
	public int totalFiles;
	private NodeDeque missingRequestQueue;
	private int idleCycles;
	private CRC32 crc32;
	private Socket socket;
	private NodeDeque completedQueue;
	private NodeDeque extraRequestQueue;
	private byte[] gzipBuffer;
	private int[] terrainFileIds;
	private int currentChunkOffset;
	private int currentChunkLength;
	private byte[] ioBuffer;
	private int[] landscapeFileIds;
	private int[] midiPreloadFlags;
	private DualNodeDeque outstandingRequests;
	private InputStream inputStream;
	private OnDemandRequest currentRequest;
	private client clientInstance;
	private NodeDeque networkRequests;
	private int keepAliveCycles;
	private int[] animationIndex;
	private int[][] versions;
	private long lastSocketOpenTime;
	public int requestFailures;

}