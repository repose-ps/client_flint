package rs2.net;

import java.math.BigInteger;
import java.nio.charset.Charset;

import rs2.collection.DualNode;

/**
 * A cursor-based byte buffer for the revision 377 cache and game protocols.
 *
 * <p>
 * Primitive methods use network byte order (big-endian) unless their name ends
 * in {@code LE}, {@code ME}, or {@code IME}. The Add, Neg, and Sub suffixes
 * identify RuneScape's byte transformations and are part of the wire format,
 * not arithmetic conveniences.
 * </p>
 *
 * <p>
 * This class deliberately exposes its cursor and backing array while the
 * untouched client is being refactored: many legacy call sites seek or copy
 * bytes directly. Once those call sites have named operations, these fields can
 * be made private.
 * </p>
 */
public class Buffer extends DualNode {

	/**
	 * Masks containing the lowest {@code n} bits.
	 *
	 * BIT_MASKS[5], for example, is binary 11111.
	 */
	public static final int BIT_MASKS[] = { 0, 1, 3, 7, 15, 31, 63, 127, 255, 511, 1023, 2047, 4095, 8191, 16383, 32767,
			65535, 0x1ffff, 0x3ffff, 0x7ffff, 0xfffff, 0x1fffff, 0x3fffff, 0x7fffff, 0xffffff, 0x1ffffff, 0x3ffffff,
			0x7ffffff, 0xfffffff, 0x1fffffff, 0x3fffffff, 0x7fffffff, -1 };

	/**
	 * Indicates the end of a String.
	 */
	private static final byte STRING_TERMINATOR = 10;

	/**
	 * The original client stored strings as one byte per character.
	 *
	 * ISO-8859-1 provides a predictable one-to-one mapping between byte values and
	 * the first 256 Unicode characters, avoiding the original dependence on the
	 * operating system's default charset.
	 */
	private static final Charset LEGACY_CHARSET = Charset.forName("ISO-8859-1");

	/**
	 * The backing storage.
	 *
	 * Retained as public during incremental refactoring because the untouched
	 * client accesses it directly in a number of places.
	 */
	public byte[] payload;

	/**
	 * Index of the next byte to read or write.
	 *
	 * This is temporarily public because legacy code performs direct seeking.
	 */
	public int position;

	/**
	 * Index of the next bit read while bit access is active.
	 */
	public int bitPosition;

	/**
	 * Cipher used to encode outgoing packet opcodes.
	 */
	public IsaacCipher opcodeCipher;

	/**
	 * Creates an empty buffer with the requested capacity.
	 * 
	 * @param capacity the capacity
	 */
	public Buffer(int capacity) {
		this(new byte[capacity]);
	}

	/**
	 * Wraps an existing byte array and starts the cursor at zero.
	 *
	 * The array is not copied.
	 * 
	 * @param byteData the byte data
	 */
	public Buffer(byte byteData[]) {
		payload = byteData;
		position = 0;
	}

	/**
	 * Writes an outgoing packet opcode obfuscated by ISAAC.
	 * 
	 * @param opcode the opcode
	 */
	public void writeOpcode(int opcode) {
		payload[position++] = (byte) (opcode + opcodeCipher.nextInt());
	}

	/**
	 * Writes an 8-bit value.
	 * 
	 * @param value the value
	 */
	public void writeByte(int value) {
		payload[position++] = (byte) value;
	}

	/**
	 * Writes a big-endian 16-bit value.
	 * 
	 * @param value the value
	 */
	public void writeShort(int value) {
		payload[position++] = (byte) (value >> 8);
		payload[position++] = (byte) value;
	}

	/**
	 * Writes a little-endian 16-bit value.
	 * 
	 * @param value the value
	 */
	public void writeShortLE(int value) {
		payload[position++] = (byte) value;
		payload[position++] = (byte) (value >> 8);
	}

	/**
	 * Writes a big-endian 24-bit value.
	 * 
	 * @param value the value
	 */
	public void writeMedium(int value) {
		payload[position++] = (byte) (value >> 16);
		payload[position++] = (byte) (value >> 8);
		payload[position++] = (byte) value;
	}

	/**
	 * Writes a big-endian 32-bit value.
	 * 
	 * @param inputValue the input value
	 */
	public void writeInt(int inputValue) {
		payload[position++] = (byte) (inputValue >> 24);
		payload[position++] = (byte) (inputValue >> 16);
		payload[position++] = (byte) (inputValue >> 8);
		payload[position++] = (byte) inputValue;
	}

	/**
	 * Writes a little-endian 32-bit value.
	 * 
	 * @param inputValue the input value
	 */
	public void writeIntLE(int inputValue) {
		payload[position++] = (byte) inputValue;
		payload[position++] = (byte) (inputValue >> 8);
		payload[position++] = (byte) (inputValue >> 16);
		payload[position++] = (byte) (inputValue >> 24);
	}

	/**
	 * Writes a big-endian 64-bit value.
	 * 
	 * @param value the value
	 */
	public void writeLong(long value) {
		payload[position++] = (byte) (int) (value >> 56);
		payload[position++] = (byte) (int) (value >> 48);
		payload[position++] = (byte) (int) (value >> 40);
		payload[position++] = (byte) (int) (value >> 32);
		payload[position++] = (byte) (int) (value >> 24);
		payload[position++] = (byte) (int) (value >> 16);
		payload[position++] = (byte) (int) (value >> 8);
		payload[position++] = (byte) (int) value;
	}

	/**
	 * Writes the client's legacy one-byte-per-character string format.
	 *
	 * Strings are terminated by line-feed ({@code 0x0A}), not a zero byte.
	 * 
	 * @param value the value
	 */
	public void writeString(String value) {
		for (int index = 0; index < value.length(); index++) {
			payload[position++] = (byte) value.charAt(index);
		}

		payload[position++] = STRING_TERMINATOR;
	}

	/**
	 * Copies bytes into this buffer and advances the cursor.
	 * 
	 * @param source the source
	 * @param offset the offset
	 * @param length the length
	 */
	public void writeBytes(byte source[], int offset, int length) {
		System.arraycopy(source, offset, payload, position, length);
		position += length;
	}

	/**
	 * Back-patches the one-byte length preceding a variable-length payload.
	 *
	 * <p>
	 * The usual sequence is:
	 * </p>
	 *
	 * <ol>
	 * <li>Write a placeholder byte.</li>
	 * <li>Write the variable-length payload.</li>
	 * <li>Call this method with the number of payload bytes written.</li>
	 * </ol>
	 * 
	 * @param length the length
	 */
	public void writeLength(int length) {
		payload[position - length - 1] = (byte) length;
	}

	/**
	 * Reads an unsigned 8-bit value.
	 */
	public int readUnsignedByte() {
		return payload[position++] & 0xff;
	}

	/**
	 * Reads a signed 8-bit value.
	 */
	public byte readSignedByte() {
		return payload[position++];
	}

	/**
	 * Reads an unsigned big-endian 16-bit value.
	 */
	public int readUnsignedShort() {
		return (readUnsignedByte() << 8) | readUnsignedByte();
	}

	/**
	 * Reads a signed big-endian 16-bit value.
	 */
	public int readSignedShort() {
		int value = readUnsignedShort();
		return value > 32767 ? value - 65536 : value;
	}

	/**
	 * Reads an unsigned big-endian 24-bit value.
	 */
	public int readMedium() {
		return (readUnsignedByte() << 16) | (readUnsignedByte() << 8) | readUnsignedByte();
	}

	/**
	 * Reads a big-endian 32-bit value.
	 */
	public int readInt() {
		return (readUnsignedByte() << 24) | (readUnsignedByte() << 16) | (readUnsignedByte() << 8) | readUnsignedByte();
	}

	/**
	 * Reads a big-endian 64-bit value.
	 */
	public long readLong() {
		long high = readInt() & 0xffffffffL;
		long low = readInt() & 0xffffffffL;
		return (high << 32) | low;
	}

	/**
	 * Reads a line-feed-terminated legacy string.
	 */
	public String readString() {
		int start = position;

		while (payload[position++] != STRING_TERMINATOR) {
			// Locate the terminator. Position must finish after it.
		}

		return new String(payload, start, position - start - 1, LEGACY_CHARSET);
	}

	/**
	 * Reads the bytes of a line-feed-terminated string without decoding them.
	 */
	public byte[] readStringBytes() {
		int start = position;

		while (payload[position++] != STRING_TERMINATOR) {
			// Locate the terminator. Position must finish after it.
		}

		int length = position - start - 1;
		byte[] value = new byte[length];

		System.arraycopy(payload, start, value, 0, length);
		return value;
	}

	/**
	 * Copies bytes out of this buffer and advances the cursor.
	 * 
	 * @param destination       the destination
	 * @param destinationOffset the destination offset
	 * @param length            the length
	 */
	public void readBytes(byte[] destination, int destinationOffset, int length) {
		System.arraycopy(payload, position, destination, destinationOffset, length);

		position += length;
	}

	/**
	 * Switches from byte reads to most-significant-bit-first bit reads.
	 */
	public void startBitAccess() {
		bitPosition = position * 8;
	}

	/**
	 * Reads up to 32 bits, most-significant bit first.
	 *
	 * <p>
	 * Call {@link #startBitAccess()} before the first bit read and
	 * {@link #finishBitAccess()} before returning to byte reads.
	 * </p>
	 * 
	 * @param count the count
	 */
	public int readBits(int count) {
		int byteIndex = bitPosition >>> 3;
		int bitsRemainingInByte = 8 - (bitPosition & 7);
		int value = 0;

		bitPosition += count;

		while (count > bitsRemainingInByte) {
			value |= (payload[byteIndex++] & BIT_MASKS[bitsRemainingInByte]) << (count - bitsRemainingInByte);

			count -= bitsRemainingInByte;
			bitsRemainingInByte = 8;
		}

		if (count == bitsRemainingInByte) {
			value |= payload[byteIndex] & BIT_MASKS[bitsRemainingInByte];
		} else {
			value |= (payload[byteIndex] >>> (bitsRemainingInByte - count)) & BIT_MASKS[count];
		}

		return value;
	}

	/**
	 * Leaves bit access and rounds the bit cursor up to a byte boundary.
	 */
	public void finishBitAccess() {
		position = (bitPosition + 7) / 8;
	}

	/**
	 * Reads a one- or two-byte signed "smart" value.
	 *
	 * <p>
	 * Values beginning below 128 occupy one byte and are biased by 64. Other values
	 * occupy two bytes and are biased by 49,152.
	 * </p>
	 */
	public int readSignedSmart() {
		int peek = payload[position] & 0xff;

		return peek < 128 ? readUnsignedByte() - 64 : readUnsignedShort() - 49152;
	}

	/**
	 * Reads a one- or two-byte unsigned "smart" value.
	 */
	public int readUnsignedSmart() {
		int peek = payload[position] & 0xff;

		return peek < 128 ? readUnsignedByte() : readUnsignedShort() - 32768;
	}

	/**
	 * Replaces the currently written bytes with an RSA-encrypted block.
	 *
	 * <p>
	 * The result is prefixed with a one-byte length. The signed {@link BigInteger}
	 * constructor is intentional because that is the exact behavior used by the
	 * revision 377 login block.
	 * </p>
	 *
	 * @param exponent the exponent
	 * @param modulus  the modulus
	 */
	public void encryptRsa(BigInteger exponent, BigInteger modulus) {
		int length = position;
		position = 0;

		byte[] plaintext = new byte[length];
		readBytes(plaintext, 0, length);

		byte[] ciphertext = new BigInteger(plaintext).modPow(exponent, modulus).toByteArray();

		position = 0;
		writeByte(ciphertext.length);
		writeBytes(ciphertext, 0, ciphertext.length);
	}

	/*
	 * RuneScape byte transformations.
	 *
	 * These transformations modify only the transmitted low byte:
	 *
	 * Add: encoded = value + 128 Neg: encoded = -value Sub: encoded = 128 - value
	 */

	/**
	 * Writes a byte with the add transformation.
	 * 
	 * @param value the value
	 */
	public void writeByteAdd(int value) {
		payload[position++] = (byte) (value + 128);
	}

	/**
	 * Writes a byte with the neg transformation.
	 * 
	 * @param value the value
	 */
	public void writeByteNeg(int value) {
		payload[position++] = (byte) (-value);
	}

	/**
	 * Writes a byte with the sub transformation.
	 * 
	 * @param value the value
	 */
	public void writeByteSub(int value) {
		payload[position++] = (byte) (128 - value);
	}

	/**
	 * Reads an unsigned byte with the add transformation.
	 */
	public int readUnsignedByteAdd() {
		return payload[position++] - 128 & 0xff;
	}

	/**
	 * Reads an unsigned byte with the neg transformation.
	 */
	public int readUnsignedByteNeg() {
		return -payload[position++] & 0xff;
	}

	/**
	 * Reads an unsigned byte with the sub transformation.
	 */
	public int readUnsignedByteSub() {
		return 128 - payload[position++] & 0xff;
	}

	/**
	 * Reads a byte with the add transformation.
	 */
	public byte readByteAdd() {
		return (byte) (payload[position++] - 128);
	}

	/**
	 * Reads a byte with the neg transformation.
	 */
	public byte readByteNeg() {
		return (byte) (-payload[position++]);
	}

	/**
	 * Reads a byte with the sub transformation.
	 */
	public byte readByteSub() {
		return (byte) (128 - payload[position++]);
	}

	/**
	 * Writes a big-endian short whose low byte has the Add transformation.
	 * 
	 * @param inputValue the input value
	 */
	public void writeShortAdd(int inputValue) {
		payload[position++] = (byte) (inputValue >> 8);
		payload[position++] = (byte) (inputValue + 128);
	}

	/**
	 * Writes a little-endian short whose low byte has the Add transformation.
	 * 
	 * @param inputValue the input value
	 */
	public void writeShortAddLE(int inputValue) {
		payload[position++] = (byte) (inputValue + 128);
		payload[position++] = (byte) (inputValue >> 8);
	}

	/**
	 * Reads a 16-bit short value in little endian order.
	 */
	public int readUnsignedShortLE() {
		int low = readUnsignedByte();
		int high = readUnsignedByte();

		return (high << 8) | low;
	}

	/**
	 * Reads a big-endian unsigned short whose low byte has the Add transformation.
	 */
	public int readUnsignedShortAdd() {
		int high = readUnsignedByte();
		int low = (payload[position++] - 128) & 0xff;

		return (high << 8) | low;
	}

	/**
	 * Reads a little-endian unsigned short whose low byte has the Add
	 * transformation.
	 */
	public int readUnsignedShortAddLE() {
		int low = (payload[position++] - 128) & 0xff;
		int high = readUnsignedByte();

		return (high << 8) | low;
	}

	/**
	 * Reads short le.
	 * 
	 * @return the resulting int
	 */
	public int readShortLE() {
		int value = readUnsignedShortLE();
		return value > 32767 ? value - 65536 : value;
	}

	/**
	 * Reads a signed big-endian short whose low byte has the Add transformation.
	 */
	public int readShortAdd() {
		int value = readUnsignedShortAdd();
		return value > 32767 ? value - 65536 : value;
	}

	/**
	 * Reads a 24-bit value stored in middle, high, low byte order.
	 */
	public int readMediumME() {
		int middle = readUnsignedByte();
		int high = readUnsignedByte();
		int low = readUnsignedByte();

		return (high << 16) | (middle << 8) | low;
	}

	/**
	 * Reads int le.
	 * 
	 * @return the resulting int
	 */
	public int readIntLE() {
		return readUnsignedByte() | (readUnsignedByte() << 8) | (readUnsignedByte() << 16) | (readUnsignedByte() << 24);
	}

	/**
	 * Reads a value stored in third, fourth, first, second significance order.
	 *
	 * For {@code 0x12345678}, the stored bytes are: {@code 56 78 12 34}.
	 */
	public int readIntME() {
		int third = readUnsignedByte();
		int fourth = readUnsignedByte();
		int first = readUnsignedByte();
		int second = readUnsignedByte();

		return (first << 24) | (second << 16) | (third << 8) | fourth;
	}

	/**
	 * Reads a value stored in second, first, fourth, third significance order.
	 *
	 * For {@code 0x12345678}, the stored bytes are: {@code 34 12 78 56}.
	 */
	public int readIntIME() {
		int second = readUnsignedByte();
		int first = readUnsignedByte();
		int fourth = readUnsignedByte();
		int third = readUnsignedByte();

		return (first << 24) | (second << 16) | (third << 8) | fourth;
	}

	/**
	 * Reads forward from this buffer while filling the destination backward.
	 * 
	 * @param destination       the destination
	 * @param destinationOffset the destination offset
	 * @param length            the length
	 */
	public void readBytesReverse(byte[] destination, int destinationOffset, int length) {
		for (int index = destinationOffset + length - 1; index >= destinationOffset; index--) {
			destination[index] = payload[position++];
		}
	}

	/**
	 * Reads bytes after subtracting 128 from every encoded byte.
	 * 
	 * @param destination       the destination
	 * @param destinationOffset the destination offset
	 * @param length            the length
	 */
	public void readBytesAdd(byte[] destination, int destinationOffset, int length) {
		int end = destinationOffset + length;

		for (int index = destinationOffset; index < end; index++) {
			destination[index] = (byte) (payload[position++] - 128);
		}
	}

}
