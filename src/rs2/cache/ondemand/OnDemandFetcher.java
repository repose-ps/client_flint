package rs2.cache.ondemand;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.zip.CRC32;
import java.util.zip.GZIPInputStream;

import rs2.Client;
import rs2.cache.Archive;
import rs2.cache.ResourceLoader;
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
 * GZIP-compressed until {@link #poll()} returns them to the Client.
 * </p>
 */
public class OnDemandFetcher extends OnDemandProvider implements Runnable {

	/** Defines the model constant. */
	public static final int MODEL = 0;
	/** Defines the animation constant. */
	public static final int ANIMATION = 1;
	/** Defines the midi constant. */
	public static final int MIDI = 2;
	/** Defines the map constant. */
	public static final int MAP = 3;

	/** Defines the archive type count constant. */
	private static final int ARCHIVE_TYPE_COUNT = 4;
	/** Defines the max active requests constant. */
	private static final int MAX_ACTIVE_REQUESTS = 10;
	/** Defines the response header length constant. */
	private static final int RESPONSE_HEADER_LENGTH = 6;
	/** Defines the response chunk length constant. */
	private static final int RESPONSE_CHUNK_LENGTH = 500;
	/** Scratch chunk used while expanding completed GZIP payloads. */
	private static final int GZIP_READ_BUFFER_LENGTH = 8_192;
	/** Defines the resend after cycles constant. */
	private static final int RESEND_AFTER_CYCLES = 50;
	/** Defines the disconnect after idle cycles constant. */
	private static final int DISCONNECT_AFTER_IDLE_CYCLES = 750;
	/** Defines the keep alive after cycles constant. */
	private static final int KEEP_ALIVE_AFTER_CYCLES = 500;
	/** Defines the socket reopen delay millis constant. */
	private static final long SOCKET_REOPEN_DELAY_MILLIS = 4_000L;
	/** Defines the update server handshake constant. */
	private static final int UPDATE_SERVER_HANDSHAKE = 15;
	/** Defines the location prefetch type constant. */
	private static final int LOCATION_PREFETCH_TYPE = 93;

	/**
	 * Reads data.
	 */
	private void readData() {
		try {
			int available = inputStream.available();
			if (currentChunkLength == 0 && available >= RESPONSE_HEADER_LENGTH) {
				waiting = true;
				readFully(inputStream, ioBuffer, 0, RESPONSE_HEADER_LENGTH);

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
						if (currentRequest.buffer == null) {
							throw new IOException("missing start of file");
						}
						if (currentRequest.buffer.length != fileLength) {
							throw new IOException("inconsistent file length");
						}
					}
				}

				currentChunkOffset = chunk * RESPONSE_CHUNK_LENGTH;
				if (fileLength == 0) {
					currentChunkLength = 0;
				} else {
					if (currentChunkOffset >= fileLength) {
						throw new IOException("invalid response chunk " + chunk + " for length " + fileLength);
					}
					currentChunkLength = Math.min(RESPONSE_CHUNK_LENGTH, fileLength - currentChunkOffset);
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
				readFully(inputStream, destination, destinationOffset, currentChunkLength);

				if (currentRequest != null && currentChunkLength + currentChunkOffset >= destination.length) {
					if (resourceLoader.hasCache()) {
						resourceLoader.getCacheIndex(currentRequest.type + 1).write(currentRequest.id, destination);
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
			closeUpdateConnection();
		}
	}

	/**
	 * Fills a requested region or reports peer EOF rather than allowing a
	 * decrementing/never-completing legacy read loop.
	 * @param input the input data
	 * @param destination the destination
	 * @param offset the starting offset
	 * @param length the number of elements or bytes
	 * @throws IOException if an I/O operation fails
	 */
	private static void readFully(InputStream input, byte[] destination, int offset, int length) throws IOException {
		int read = 0;
		while (read < length) {
			int count = input.read(destination, offset + read, length - read);
			if (count < 0) {
				throw new EOFException("End of update-server stream after " + read + " of " + length + " bytes");
			}
			if (count == 0) {
				continue;
			}
			read += count;
		}
	}

	/**
	 * Returns model index.
	 *
	 * @param modelId the model id
	 * @return the model index
	 */
	public int getModelIndex(int modelId) {
		return modelIndices[modelId] & 0xff;
	}

	/**
	 * Queues a model for on-demand loading.
	 *
	 * @param modelId the model id
	 */
	@Override
	public void requestModel(int modelId) {
		request(MODEL, modelId);
	}

	/**
	 * Processes extra requests.
	 */
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

	/**
	 * Sets extra priority.
	 *
	 * @param type     the type
	 * @param id       the id
	 * @param priority the priority
	 */
	public void setExtraPriority(int type, int id, byte priority) {
		if (!resourceLoader.hasCache())
			return;
		if (versions[type][id] == 0)
			return;
		byte[] bytes = resourceLoader.getCacheIndex(type + 1).read(id);
		if (crcMatches(bytes, versions[type][id], crcs[type][id]))
			return;
		fileStatus[type][id] = priority;
		if (priority > highestPriority)
			highestPriority = priority;
		totalFiles++;
	}

	/**
	 * Returns whether midi preload.
	 *
	 * @param id the id
	 * @return whether the requested condition is satisfied
	 */
	public boolean isMidiPreload(int id) {
		return midiPreloadFlags[id] == 1;
	}

	/**
	 * Queues an on-demand resource request.
	 *
	 * @param type the type
	 * @param id   the id
	 */
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

	/**
	 * Removes and returns the next completed on-demand request.
	 *
	 * @return the next completed request, or {@code null} when none is available
	 */
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

		try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(request.buffer));
				ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			byte[] chunk = new byte[GZIP_READ_BUFFER_LENGTH];
			for (;;) {
				int read = gzip.read(chunk);
				if (read < 0) {
					break;
				}
				if (read > 0) {
					output.write(chunk, 0, read);
				}
			}
			request.buffer = output.toByteArray();
			return request;
		} catch (IOException exception) {
			throw new RuntimeException("error unzipping", exception);
		}
	}

	/**
	 * Runs this component's main processing loop.
	 */
	public void run() {
		Thread current = Thread.currentThread();
		workerThread = current;
		try {
			while (running) {
				onDemandCycle++;
				int sleepMillis = 20;
				if (highestPriority == 0 && resourceLoader.hasCache())
					sleepMillis = 50;
				try {
					Thread.sleep(sleepMillis);
				} catch (InterruptedException ignored) {
					/* Stop requests interrupt this sleep so the loop can observe running=false. */
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
						closeUpdateConnection();
					}
				} else {
					idleCycles = 0;
					statusString = "";
				}
				if (clientInstance.loggedIn && socket != null && outputStream != null
						&& (highestPriority > 0 || !resourceLoader.hasCache())) {
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
		} catch (RuntimeException exception) {
			Signlink.reportError("od_ex " + exception.getMessage());
		} finally {
			closeUpdateConnection();
			if (workerThread == current) {
				workerThread = null;
			}
		}
	}

	/**
	 * Handles failed requests.
	 */
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

	/**
	 * Queues map resources for background preloading.
	 *
	 * @param includeAllMaps whether include all maps
	 */
	public void preloadMaps(boolean includeAllMaps) {
		for (int index = 0; index < regionIds.length; index++) {
			if (includeAllMaps || mapPreloadFlags[index] != 0) {
				setExtraPriority(MAP, landscapeFileIds[index], (byte) 2);
				setExtraPriority(MAP, terrainFileIds[index], (byte) 2);
			}
		}
	}

	/**
	 * Returns outstanding request count.
	 *
	 * @return the outstanding request count
	 */
	public int getOutstandingRequestCount() {
		synchronized (outstandingRequests) {
			return outstandingRequests.size();
		}
	}

	/**
	 * Returns whether landscape file.
	 *
	 * @param fileId the file id
	 * @return whether the requested condition is satisfied
	 */
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
	 *
	 * @param archive        the archive
	 * @param clientInstance the client instance
	 * @param resourceLoader the resource loader
	 */
	public void start(Archive archive, Client clientInstance, ResourceLoader resourceLoader) {
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
		this.resourceLoader = resourceLoader;
		running = true;
		Thread thread = new Thread(this, "rs2-on-demand");
		thread.setDaemon(true);
		workerThread = thread;
		thread.start();
		thread.setPriority(2);
	}

	/**
	 * Clears extra requests.
	 */
	public void clearExtraRequests() {
		synchronized (extraRequestQueue) {
			extraRequestQueue.clear();
		}
	}

	/**
	 * Queues a low-priority extra resource request.
	 *
	 * @param type the type
	 * @param id   the id
	 */
	public void queueExtraRequest(int type, int id) {
		if (!resourceLoader.hasCache())
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

	/**
	 * Processes pending requests against the local cache.
	 */
	private void checkCache() {
		OnDemandRequest request;
		synchronized (cacheRequestQueue) {
			request = (OnDemandRequest) cacheRequestQueue.removeFirst();
		}
		while (request != null) {
			waiting = true;
			byte[] bytes = null;
			if (resourceLoader.hasCache()) {
				bytes = resourceLoader.getCacheIndex(request.type + 1).read(request.id);
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

	/**
	 * Stops the on-demand fetcher and closes its update connection.
	 */
	public void stop() {
		running = false;
		closeUpdateConnection();
		Thread thread = workerThread;
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
		if (workerThread == thread) {
			workerThread = null;
		}
		closeUpdateConnection();
	}

	/**
	 * Closes update connection.
	 */
	private void closeUpdateConnection() {
		Socket currentSocket = socket;
		socket = null;
		InputStream currentInput = inputStream;
		inputStream = null;
		OutputStream currentOutput = outputStream;
		outputStream = null;
		currentChunkLength = 0;
		if (currentSocket != null) {
			try {
				currentSocket.close();
			} catch (IOException ignored) {
			}
		}
		if (currentInput != null) {
			try {
				currentInput.close();
			} catch (IOException ignored) {
			}
		}
		if (currentOutput != null) {
			try {
				currentOutput.close();
			} catch (IOException ignored) {
			}
		}
	}

	/**
	 * Returns file count.
	 *
	 * @param type the type
	 * @return the file count
	 */
	public int getFileCount(int type) {
		return versions[type].length;
	}

	/**
	 * Returns whether cached data matches its expected version and CRC.
	 *
	 * @param data            the data
	 * @param expectedVersion the expected version
	 * @param expectedCrc     the expected crc
	 * @return whether the operation succeeds
	 */
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

	/**
	 * Sends an on-demand resource request to the update server.
	 *
	 * @param request the request
	 */
	private void sendRequest(OnDemandRequest request) {
		try {
			if (socket == null) {
				long now = System.currentTimeMillis();
				if (now - lastSocketOpenTime < SOCKET_REOPEN_DELAY_MILLIS) {
					return;
				}
				lastSocketOpenTime = now;
				socket = clientInstance.openSocket(43594 + Client.portOffset);
				inputStream = socket.getInputStream();
				outputStream = socket.getOutputStream();
				outputStream.write(UPDATE_SERVER_HANDSHAKE);
				for (int index = 0; index < 8; index++) {
					if (inputStream.read() < 0) {
						throw new EOFException("End of update-server handshake");
					}
				}
				idleCycles = 0;
			}

			ioBuffer[0] = (byte) request.type;
			ioBuffer[1] = (byte) (request.id >> 8);
			ioBuffer[2] = (byte) request.id;
			if (request.incomplete) {
				ioBuffer[3] = 2;
			} else if (!clientInstance.loggedIn) {
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

		closeUpdateConnection();
		requestFailures++;
	}

	/**
	 * Returns animation count.
	 *
	 * @return the animation count
	 */
	public int getAnimationCount() {
		return animationIndex.length;
	}

	/**
	 * Returns map file id.
	 *
	 * @param regionX  the region x
	 * @param regionY  the region y
	 * @param fileType the file type
	 * @return the map file id
	 */
	public int getMapFileId(int regionX, int regionY, int fileType) {
		int regionId = (regionX << 8) + regionY;
		for (int regionIndex = 0; regionIndex < regionIds.length; regionIndex++)
			if (regionIds[regionIndex] == regionId)
				if (fileType == 0)
					return terrainFileIds[regionIndex];
				else
					return landscapeFileIds[regionIndex];

		return -1;
	}

	/**
	 * Creates a new OnDemandFetcher instance.
	 */
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
		ioBuffer = new byte[RESPONSE_CHUNK_LENGTH];
		outstandingRequests = new DualNodeDeque();
		networkRequests = new NodeDeque();
		versions = new int[ARCHIVE_TYPE_COUNT][];
	}

	/** Stores the current files loaded. */
	private int filesLoaded;

	/** Stores model indices values. */
	private byte[] modelIndices;

	/** Stores map preload flags values. */
	private int[] mapPreloadFlags;

	/** Stores file status values. */
	private byte[][] fileStatus;
	/** Tracks whether waiting. */
	private boolean waiting;
	/** Tracks whether running. */
	private volatile boolean running;
	/** Worker thread owned by this fetcher. */
	private volatile Thread workerThread;

	/** Stores the current cache request queue. */
	private NodeDeque cacheRequestQueue;

	/** Stores the current highest priority. */
	private volatile int highestPriority;

	/** Stores the current mandatory request count. */
	private int mandatoryRequestCount;

	/** Stores the current extra request count. */
	private int extraRequestCount;

	/** Stores crcs values. */
	private int[][] crcs;

	/** Stores region IDs values. */
	private int[] regionIds;

	/** Stores the current status string. */
	public volatile String statusString;

	/** Stores the current on demand cycle. */
	public volatile int onDemandCycle;

	/** Stores the current output stream. */
	private volatile OutputStream outputStream;

	/** Stores the current total files. */
	public volatile int totalFiles;

	/** Stores the current missing request queue. */
	private NodeDeque missingRequestQueue;

	/** Stores the current idle cycles. */
	private int idleCycles;

	/** Stores the current crc32. */
	private CRC32 crc32;

	/** Stores the current socket. */
	private volatile Socket socket;

	/** Stores the current completed queue. */
	private NodeDeque completedQueue;

	/** Stores the current extra request queue. */
	private NodeDeque extraRequestQueue;

	/** Stores terrain file IDs values. */
	private int[] terrainFileIds;

	/** Stores the current chunk offset. */
	private int currentChunkOffset;

	/** Stores the current chunk length. */
	private int currentChunkLength;

	/** Stores I/O buffer values. */
	private byte[] ioBuffer;

	/** Stores landscape file IDs values. */
	private int[] landscapeFileIds;

	/** Stores MIDI preload flags values. */
	private int[] midiPreloadFlags;

	/** Stores the current outstanding requests. */
	private DualNodeDeque outstandingRequests;

	/** Stores the current input stream. */
	private volatile InputStream inputStream;

	/** Stores the current request. */
	private OnDemandRequest currentRequest;

	/** Stores the current client instance. */
	private Client clientInstance;

	/** Stores the current resource loader. */
	private ResourceLoader resourceLoader;

	/** Stores the current network requests. */
	private NodeDeque networkRequests;

	/** Stores the current keep alive cycles. */
	private int keepAliveCycles;

	/** Stores animation index values. */
	private int[] animationIndex;

	/** Stores versions values. */
	private int[][] versions;

	/** Stores the current last socket open time. */
	private long lastSocketOpenTime;

	/** Stores the current request failures. */
	public volatile int requestFailures;

}
