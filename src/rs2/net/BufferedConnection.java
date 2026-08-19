// Decompiled by Jad v1.5.8f. Copyright 2001 Pavel Kouznetsov.
// Jad home page: http://www.kpdus.com/jad.html
// Decompiler options: packimports(3) 
package rs2.net;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

/**
 * A blocking game-server connection with an asynchronous buffered writer.
 *
 * <p>
 * Reads occur on the calling thread. Writes are copied into a bounded circular
 * buffer and drained by one private daemon thread, preventing a slow socket
 * write from stalling the client game loop.
 * </p>
 *
 * <p>
 * This class owns the supplied socket. Closing the connection also closes both
 * streams and terminates the writer thread.
 * </p>
 */
public class BufferedConnection implements Runnable, Closeable {

	private static final int READ_TIMEOUT_MILLIS = 30_000;
	private static final int WRITE_BUFFER_CAPACITY = 5_000;

	/**
	 * Leaves 100 bytes unused, preserving the safety margin from the original
	 * client.
	 */
	private static final int MAX_PENDING_BYTES = 4_900;
	private static final int WRITER_THREAD_PRIORITY = 3;

	public InputStream input;
	public OutputStream output;
	public Socket socket;
	private boolean closed;
	public byte[] writeBuffer = new byte[WRITE_BUFFER_CAPACITY];

	/**
	 * Index of the next queued byte consumed by the writer thread.
	 */
	public int readPosition;

	/**
	 * Index at which the producer will append the next outgoing byte.
	 */
	public int writePosition;
	public boolean writeThreadStarted;

	private Thread writerThread;
	private IOException writerFailure;

	/**
	 * Creates a connection around an already-connected socket.
	 *
	 * <p>
	 * TCP_NODELAY prevents small game packets from being delayed by Nagle's
	 * algorithm. The read timeout preserves the original 30-second timeout.
	 * </p>
	 */
	public BufferedConnection(Socket socket) throws IOException {
		this.socket = socket;
		socket.setSoTimeout(READ_TIMEOUT_MILLIS);
		socket.setTcpNoDelay(true);

		input = socket.getInputStream();
		output = socket.getOutputStream();
	}

	/**
	 * Closes the socket and wakes the writer so it can terminate.
	 *
	 * <p>
	 * Like the original client, closing is immediate: bytes still waiting in the
	 * circular buffer are discarded rather than drained.
	 * </p>
	 */
	@Override
	public void close() throws IOException {
		synchronized (this) {
			if (closed) {
				return;
			}

			closed = true;
			notifyAll();
		}

		/*
		 * Closing a Socket also closes its associated input and output streams. It also
		 * unblocks a writer currently stuck in socket I/O.
		 */
		socket.close();
	}

	/**
	 * Reads one unsigned byte, blocking until data is available.
	 *
	 * @return a value from 0 to 255, or -1 if the peer reached end-of-stream
	 */
	public int read() throws IOException {
		ensureOpen();
		return input.read();
	}

	/**
	 * Returns the number of bytes that can currently be read without blocking.
	 */
	public int available() throws IOException {
		ensureOpen();
		return input.available();
	}

	/**
	 * Blocks until exactly {@code length} bytes have been read.
	 *
	 * <p>
	 * A single InputStream read is not guaranteed to fill the requested region,
	 * even when the connection remains open. Reads must therefore continue until
	 * every requested byte has arrived.
	 * </p>
	 *
	 * @throws EOFException if the peer closes the stream before all requested bytes
	 *                      arrive
	 */
	public void readFully(byte[] destination, int destinationOffset, int length) throws IOException {
		checkRange(destination, destinationOffset, length);
		ensureOpen();

		int remaining = length;
		int offset = destinationOffset;

		while (remaining > 0) {
			int count = input.read(destination, offset, remaining);

			if (count < 0) {
				throw new EOFException(
						"End of stream after reading " + (length - remaining) + " of " + length + " bytes");
			}

			/*
			 * SocketInputStream normally blocks rather than returning zero, but handling
			 * zero makes the method correct for any InputStream implementation.
			 */
			if (count == 0) {
				continue;
			}

			offset += count;
			remaining -= count;
		}
	}

	/**
	 * Queues bytes for asynchronous transmission.
	 *
	 * <p>
	 * The entire write is accepted or rejected atomically. This improves on the
	 * original implementation, which could partially enqueue a packet before
	 * reporting that its circular buffer had overflowed.
	 * </p>
	 *
	 * @throws IOException if the connection is closed, the writer previously
	 *                     failed, or the bounded output queue lacks space
	 */
	public synchronized void write(byte[] source, int sourceOffset, int length) throws IOException {
		checkRange(source, sourceOffset, length);
		ensureOpen();
		checkWriterFailure();

		int pending = pendingBytes();
		int writable = MAX_PENDING_BYTES - pending;

		if (length > writable) {
			throw new IOException("Output buffer overflow: requested " + length + " bytes with only " + writable
					+ " bytes available");
		}

		/*
		 * The first copy fills from the current write position to either the end of the
		 * source region or the end of the circular buffer.
		 */
		int firstLength = Math.min(length, WRITE_BUFFER_CAPACITY - writePosition);

		System.arraycopy(source, sourceOffset, writeBuffer, writePosition, firstLength);

		/*
		 * If the write crossed the end of the circular buffer, copy the remaining bytes
		 * at index zero.
		 */
		int secondLength = length - firstLength;

		if (secondLength > 0) {
			System.arraycopy(source, sourceOffset + firstLength, writeBuffer, 0, secondLength);
		}

		writePosition = (writePosition + length) % WRITE_BUFFER_CAPACITY;

		startWriterThread();
		notifyAll();
	}

	/**
	 * Drains queued writes.
	 *
	 * <p>
	 * Only the private writer thread calls this method. The monitor is released
	 * before performing socket I/O so the game thread can continue enqueueing
	 * packets while an earlier region is being transmitted.
	 * </p>
	 */
	@Override
	public void run() {
		while (true) {
			int offset;
			int length;

			synchronized (this) {
				while (!closed && readPosition == writePosition) {
					try {
						wait();
					} catch (InterruptedException exception) {
						Thread.currentThread().interrupt();

						recordWriterFailure(new IOException("Writer thread interrupted", exception));

						return;
					}
				}

				if (closed) {
					return;
				}

				offset = readPosition;

				/*
				 * Write only one contiguous region. If queued bytes wrap around, the following
				 * loop iteration writes the region at the beginning of the array.
				 */
				if (writePosition >= readPosition) {
					length = writePosition - readPosition;
				} else {
					length = WRITE_BUFFER_CAPACITY - readPosition;
				}
			}

			try {
				output.write(writeBuffer, offset, length);
			} catch (IOException exception) {
				recordWriterFailure(exception);
				return;
			}

			boolean queueEmpty;

			synchronized (this) {
				readPosition = (readPosition + length) % WRITE_BUFFER_CAPACITY;

				queueEmpty = readPosition == writePosition;
			}

			/*
			 * Flush only after all currently queued data has been written. Socket streams
			 * generally do not buffer independently, but this preserves the behavior of the
			 * original connection.
			 */
			if (queueEmpty) {
				try {
					output.flush();
				} catch (IOException exception) {
					recordWriterFailure(exception);
					return;
				}
			}
		}
	}

	public synchronized boolean isClosed() {
		return closed;
	}

	/**
	 * Prints a diagnostic snapshot retained for the client's debug command.
	 */
	public synchronized void printDebugInformation() {
		System.out.println("closed: " + closed);
		System.out.println("readPosition: " + readPosition);
		System.out.println("writePosition: " + writePosition);
		System.out.println("writerStarted: " + (writerThread != null));
		System.out.println("writerFailure: " + writerFailure);

		try {
			System.out.println("available: " + (closed ? 0 : input.available()));
		} catch (IOException exception) {
			System.out.println("available: unavailable");
		}
	}

	/**
	 * Starts the asynchronous writer the first time data is queued.
	 *
	 * <p>
	 * The original implementation delegated thread creation to {@code Applet_Sub1}.
	 * The connection can own its writer directly, removing an unnecessary
	 * dependency from the networking package.
	 * </p>
	 */
	private synchronized void startWriterThread() {
		if (writerThread != null) {
			return;
		}

		writerThread = new Thread(this, "rs2-network-writer");

		/*
		 * A daemon thread cannot keep the JVM alive after the client has otherwise
		 * exited.
		 */
		writerThread.setDaemon(true);
		writerThread.setPriority(WRITER_THREAD_PRIORITY);
		writerThread.start();
	}

	/**
	 * Records the first asynchronous writer error.
	 *
	 * The game thread receives this failure on its next call to write().
	 */
	private synchronized void recordWriterFailure(IOException failure) {
		if (writerFailure == null) {
			writerFailure = failure;
		}

		notifyAll();
	}

	private void ensureOpen() throws IOException {
		if (closed) {
			throw new IOException("Connection is closed");
		}
	}

	private void checkWriterFailure() throws IOException {
		if (writerFailure != null) {
			throw new IOException("Asynchronous socket writer failed", writerFailure);
		}
	}

	/**
	 * Calculates the number of bytes waiting in the circular buffer.
	 */
	private int pendingBytes() {
		return (writePosition - readPosition + WRITE_BUFFER_CAPACITY) % WRITE_BUFFER_CAPACITY;
	}

	/**
	 * Performs the same bounds checks expected from standard Java array APIs.
	 */
	private static void checkRange(byte[] bytes, int offset, int length) {
		if (bytes == null) {
			throw new NullPointerException("bytes");
		}

		if (offset < 0 || length < 0 || offset > bytes.length - length) {
			throw new IndexOutOfBoundsException(
					"offset=" + offset + ", length=" + length + ", arrayLength=" + bytes.length);
		}
	}

}
