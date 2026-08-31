package rs2.tools;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import rs2.game.Pathfinder;
import rs2.net.Buffer;
import rs2.net.IsaacCipher;
import rs2.net.LoginSession;
import rs2.net.MovementPacketEncoder;
import rs2.net.NetworkSession;
import rs2.scene.util.CollisionMap;
import rs2.text.Base37;

/** Known-vector coverage for revision-377 protocol primitives and framing. */
public final class ProtocolSelfTest {

    /** Maximum time allowed for one loopback framing read. */
    private static final long SOCKET_TEST_TIMEOUT_NANOS = 2_000_000_000L;

    /** Prevents instantiation. */
    private ProtocolSelfTest() {
    }

    /**
     * Runs the protocol regression suite.
     *
     * @param args command-line arguments; none are accepted
     * @throws Exception if a loopback protocol fixture fails
     */
    public static void main(String[] args) throws Exception {
        if (args.length != 0) {
            throw new IllegalArgumentException("Usage: ProtocolSelfTest");
        }
        int checks = run();
        System.out.println("ProtocolSelfTest: PASS (" + checks + " checks)");
    }

    /**
     * Runs all protocol checks.
     *
     * @return completed assertion count
     * @throws Exception if a loopback protocol fixture fails
     */
    static int run() throws Exception {
        SelfTestSupport test = new SelfTestSupport();
        testBufferWrites(test);
        testBufferReads(test);
        testBufferTransforms(test);
        testBitAndSmartReads(test);
        testRsaIdentityExponent(test);
        testIsaacKnownVectors(test);
        testMovementPacketEncoding(test);
        testIncomingPacketFraming(test);
        testLoginFraming(test);
        return test.checks();
    }

    /** Verifies primitive and variable-length buffer writes.
     * @param test assertion sink
     */
    private static void testBufferWrites(SelfTestSupport test) {
        Buffer buffer = new Buffer(64);
        buffer.writeByte(0x12);
        buffer.writeShort(0x3456);
        buffer.writeShortLE(0x789a);
        buffer.writeMedium(0xbcdef0);
        buffer.writeInt(0x12345678);
        buffer.writeIntLE(0x90abcdef);
        buffer.writeLong(0x1122334455667788L);
        buffer.writeString("Hi");
        buffer.writeBytes(new byte[] { 9, 8, 7, 6 }, 1, 2);

        test.bytes(Arrays.copyOf(buffer.payload, buffer.position), new byte[] {
                0x12,
                0x34, 0x56,
                (byte) 0x9a, 0x78,
                (byte) 0xbc, (byte) 0xde, (byte) 0xf0,
                0x12, 0x34, 0x56, 0x78,
                (byte) 0xef, (byte) 0xcd, (byte) 0xab, (byte) 0x90,
                0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, (byte) 0x88,
                'H', 'i', 0x0a,
                8, 7
        }, "primitive write bytes");

        Buffer variable = new Buffer(8);
        variable.writeByte(0);
        int payloadStart = variable.position;
        variable.writeByte(0xaa);
        variable.writeByte(0xbb);
        variable.writeByte(0xcc);
        variable.writeLength(variable.position - payloadStart);
        test.bytes(Arrays.copyOf(variable.payload, variable.position),
                new byte[] { 3, (byte) 0xaa, (byte) 0xbb, (byte) 0xcc }, "variable-length backpatch");
    }

    /** Verifies primitive and bulk buffer reads.
     * @param test assertion sink
     */
    private static void testBufferReads(SelfTestSupport test) {
        Buffer buffer = new Buffer(new byte[] {
                (byte) 0xfe,
                0x12, 0x34,
                (byte) 0xff, (byte) 0xfe,
                0x01, 0x23, 0x45,
                (byte) 0x89, (byte) 0xab, (byte) 0xcd, (byte) 0xef,
                0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, (byte) 0x88,
                'o', 'k', 0x0a,
                4, 5, 6
        });
        test.equal(buffer.readUnsignedByte(), 254, "unsigned byte");
        test.equal(buffer.readUnsignedShort(), 0x1234, "unsigned short");
        test.equal(buffer.readSignedShort(), -2, "signed short");
        test.equal(buffer.readMedium(), 0x012345, "medium");
        test.equal(buffer.readInt(), 0x89abcdef, "int");
        test.equal(buffer.readLong(), 0x1122334455667788L, "long");
        test.equal(buffer.readString(), "ok", "legacy string");
        byte[] bytes = new byte[3];
        buffer.readBytes(bytes, 0, bytes.length);
        test.bytes(bytes, new byte[] { 4, 5, 6 }, "bulk byte read");

        Buffer stringBytes = new Buffer(new byte[] { 'r', 's', 0x0a });
        test.bytes(stringBytes.readStringBytes(), new byte[] { 'r', 's' }, "raw string bytes");
    }

    /** Verifies byte, short and mixed-endian protocol transformations.
     * @param test assertion sink
     */
    private static void testBufferTransforms(SelfTestSupport test) {
        Buffer writes = new Buffer(32);
        writes.writeByteAdd(0x12);
        writes.writeByteNeg(0x12);
        writes.writeByteSub(0x12);
        writes.writeShortAdd(0x1234);
        writes.writeShortAddLE(0x1234);
        test.bytes(Arrays.copyOf(writes.payload, writes.position), new byte[] {
                (byte) 0x92, (byte) 0xee, 0x6e,
                0x12, (byte) 0xb4,
                (byte) 0xb4, 0x12
        }, "transformed write bytes");

        Buffer reads = new Buffer(new byte[] {
                (byte) 0x92, (byte) 0xee, 0x6e,
                (byte) 0x92, (byte) 0xee, 0x6e,
                0x34, 0x12,
                0x12, (byte) 0xb4,
                (byte) 0xb4, 0x12,
                0x34, 0x12, 0x56,
                0x78, 0x56, 0x34, 0x12,
                0x56, 0x78, 0x12, 0x34,
                0x34, 0x12, 0x78, 0x56
        });
        test.equal(reads.readUnsignedByteAdd(), 0x12, "unsigned byte add");
        test.equal(reads.readUnsignedByteNeg(), 0x12, "unsigned byte neg");
        test.equal(reads.readUnsignedByteSub(), 0x12, "unsigned byte sub");
        test.equal(reads.readByteAdd(), 0x12, "signed byte add");
        test.equal(reads.readByteNeg(), 0x12, "signed byte neg");
        test.equal(reads.readByteSub(), 0x12, "signed byte sub");
        test.equal(reads.readUnsignedShortLE(), 0x1234, "unsigned short LE");
        test.equal(reads.readUnsignedShortAdd(), 0x1234, "unsigned short Add");
        test.equal(reads.readUnsignedShortAddLE(), 0x1234, "unsigned short LE Add");
        test.equal(reads.readMediumME(), 0x123456, "middle-endian medium");
        test.equal(reads.readIntLE(), 0x12345678, "int LE");
        test.equal(reads.readIntME(), 0x12345678, "int ME");
        test.equal(reads.readIntIME(), 0x12345678, "int IME");

        Buffer signed = new Buffer(new byte[] { (byte) 0xff, 0x7f, (byte) 0xff, (byte) 0xff });
        test.equal(signed.readShortLE(), 32767, "signed short LE positive");
        test.equal(signed.readShortAdd(), -129, "signed short Add negative");

        Buffer reverse = new Buffer(new byte[] { 1, 2, 3, (byte) 0x81, (byte) 0x82, (byte) 0x83 });
        byte[] destination = new byte[6];
        reverse.readBytesReverse(destination, 1, 3);
        reverse.readBytesAdd(destination, 3, 3);
        test.bytes(destination, new byte[] { 0, 3, 2, 1, 2, 3 }, "reverse/add bulk reads");
    }

    /** Verifies bit access and signed/unsigned smart decoding.
     * @param test assertion sink
     */
    private static void testBitAndSmartReads(SelfTestSupport test) {
        Buffer bits = new Buffer(new byte[] { (byte) 0xac, 0x72 });
        bits.startBitAccess();
        test.equal(bits.readBits(3), 5, "first bit field");
        test.equal(bits.readBits(5), 12, "second bit field");
        test.equal(bits.readBits(4), 7, "third bit field");
        test.equal(bits.readBits(4), 2, "fourth bit field");
        bits.finishBitAccess();
        test.equal(bits.position, 2, "bit access byte alignment");

        Buffer signedSmart = new Buffer(new byte[] { 0x40, 0x7f, (byte) 0xc0, 0x00 });
        test.equal(signedSmart.readSignedSmart(), 0, "one-byte signed smart zero");
        test.equal(signedSmart.readSignedSmart(), 63, "one-byte signed smart max");
        test.equal(signedSmart.readSignedSmart(), 0, "two-byte signed smart zero");

        Buffer unsignedSmart = new Buffer(new byte[] { 0x7f, (byte) 0x80, 0x00, (byte) 0x80, 0x7b });
        test.equal(unsignedSmart.readUnsignedSmart(), 127, "one-byte unsigned smart max");
        test.equal(unsignedSmart.readUnsignedSmart(), 0, "two-byte unsigned smart zero");
        test.equal(unsignedSmart.readUnsignedSmart(), 123, "two-byte unsigned smart value");
    }

    /** Verifies RSA block replacement using an identity exponent fixture.
     * @param test assertion sink
     */
    private static void testRsaIdentityExponent(SelfTestSupport test) {
        Buffer rsa = new Buffer(32);
        rsa.writeByte(10);
        rsa.writeInt(0x11223344);
        rsa.writeString("u");
        rsa.encryptRsa(BigInteger.ONE, BigInteger.ONE.shiftLeft(256).subtract(BigInteger.ONE));
        test.equal(rsa.payload[0] & 0xff, 7, "RSA block length prefix");
        test.bytes(Arrays.copyOfRange(rsa.payload, 1, rsa.position),
                new byte[] { 10, 0x11, 0x22, 0x33, 0x44, 'u', 0x0a }, "RSA identity-exponent payload");
    }

    /** Verifies fixed ISAAC output vectors.
     * @param test assertion sink
     */
    private static void testIsaacKnownVectors(SelfTestSupport test) {
        int[] zeroExpected = {
                0x182600f3, 0x300b4a8d, 0x301b6622, 0xb08acd21,
                0x296fd679, 0x995206e9, 0xb3ffa8b5, 0x0fc99c24,
                0x5f071faf, 0x52251def, 0x894f41c2, 0xcc4c9afb
        };
        int[] seededExpected = {
                0xdaf8863e, 0x74a5cb37, 0xafd4ed73, 0x877c7c44,
                0x8fc83d9b, 0x606024ad, 0xff7a07aa, 0x4fb9c0c7,
                0xd47d4e08, 0xea54103c, 0x0ac4e8c1, 0xfc624e56
        };
        assertIsaac(test, new int[] { 0, 0, 0, 0 }, zeroExpected, "zero seed");
        assertIsaac(test, new int[] { 1, 2, 3, 4 }, seededExpected, "1,2,3,4 seed");
    }

    /**
     * Checks one ISAAC vector.
     *
     * @param test assertion sink
     * @param seed ISAAC seed
     * @param expected expected output words
     * @param label diagnostic label
     */
    private static void assertIsaac(SelfTestSupport test, int[] seed, int[] expected, String label) {
        IsaacCipher cipher = new IsaacCipher(seed);
        for (int index = 0; index < expected.length; index++) {
            test.equal(cipher.nextInt(), expected[index], "ISAAC " + label + " word " + index);
        }
    }

    /** Verifies a known compressed walking-route packet.
     * @param test assertion sink
     */
    private static void testMovementPacketEncoding(SelfTestSupport test) {
        CollisionMap collisionMap = new CollisionMap(104, 104);
        for (int x = 1; x < 103; x++) {
            for (int y = 1; y < 103; y++) {
                collisionMap.flags[x][y] = 0;
            }
        }
        Pathfinder.Route route = new Pathfinder().findRoute(collisionMap, 50, 50, 55, 53,
                0, 0, 0, 0, 0, false);
        test.check(route != null, "movement route exists");
        test.equal(route.getWaypointCount(), 2, "movement compressed waypoint count");
        test.equal(route.getWaypointX(0), 55, "movement destination X");
        test.equal(route.getWaypointY(0), 53, "movement destination Y");
        test.equal(route.getWaypointX(1), 52, "movement turn X");
        test.equal(route.getWaypointY(1), 50, "movement turn Y");

        Buffer outgoing = new Buffer(64);
        outgoing.opcodeCipher = new IsaacCipher(new int[] { 1, 2, 3, 4 });
        MovementPacketEncoder.write(outgoing, route, MovementPacketEncoder.SCREEN, 3200, 3200, true);
        test.bytes(Arrays.copyOf(outgoing.payload, outgoing.position), new byte[] {
                0x5a, 0x07, 0x34, 0x0c, 0x01, 0x32, 0x0c, 0x03, 0x7d
        }, "screen movement packet bytes");
    }

    /**
     * Verifies fixed, variable-byte and variable-short packet framing over loopback TCP.
     *
     * @param test assertion sink
     * @throws Exception if the loopback socket fixture fails
     */
    private static void testIncomingPacketFraming(SelfTestSupport test) throws Exception {
        byte[] wire = {
                2, 1, 2, 3, 4,
                63, 3, 5, 6, 7,
                53, 0, 3, 8, 9, 10
        };
        AtomicReference<Throwable> serverFailure = new AtomicReference<>();
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            Thread writer = new Thread(() -> {
                try (Socket socket = server.accept()) {
                    socket.getOutputStream().write(wire);
                    socket.getOutputStream().flush();
                } catch (Throwable throwable) {
                    serverFailure.set(throwable);
                }
            }, "protocol-self-test-framing");
            writer.start();

            NetworkSession network = new NetworkSession();
            try {
                network.connect(new Socket(server.getInetAddress(), server.getLocalPort()));
                assertIncomingFrame(test, network, 2, new byte[] { 1, 2, 3, 4 });
                assertIncomingFrame(test, network, 63, new byte[] { 5, 6, 7 });
                assertIncomingFrame(test, network, 53, new byte[] { 8, 9, 10 });
                test.equal(network.lastOpcode, 53, "last framed opcode");
                test.equal(network.secondLastOpcode, 63, "second-last framed opcode");
                test.equal(network.thirdLastOpcode, 2, "third-last framed opcode");
            } finally {
                network.closeConnection();
            }
            writer.join();
        }
        rethrowServerFailure(serverFailure);
    }

    /**
     * Waits for and verifies one framed packet.
     *
     * @param test assertion sink
     * @param network network session under test
     * @param opcode expected opcode
     * @param payload expected payload
     * @throws Exception if framing fails
     */
    private static void assertIncomingFrame(SelfTestSupport test, NetworkSession network, int opcode, byte[] payload)
            throws Exception {
        long deadline = System.nanoTime() + SOCKET_TEST_TIMEOUT_NANOS;
        while (!network.readIncomingPacket()) {
            if (System.nanoTime() >= deadline) {
                throw new AssertionError("Timed out waiting for incoming opcode " + opcode);
            }
            Thread.onSpinWait();
        }
        test.equal(network.incomingOpcode, opcode, "framed opcode");
        test.equal(network.incomingLength, payload.length, "framed payload length");
        test.bytes(Arrays.copyOf(network.incoming.payload, payload.length), payload, "framed payload");
        test.equal(network.incoming.position, 0, "framed payload cursor reset");
        network.finishIncomingPacket();
        test.equal(network.incomingOpcode, -1, "framed opcode completion reset");
    }

    /**
     * Verifies handshake and outer credential/login framing against a loopback server.
     *
     * @param test assertion sink
     * @throws Exception if the loopback login fixture fails
     */
    private static void testLoginFraming(SelfTestSupport test) throws Exception {
        long serverSessionKey = 0x1122334455667788L;
        int[] archiveCrcs = new int[9];
        for (int index = 0; index < archiveCrcs.length; index++) {
            archiveCrcs[index] = 0x10203040 + index;
        }

        LoginCapture capture = new LoginCapture();
        LoginListener listener = new LoginListener();
        AtomicReference<Throwable> serverFailure = new AtomicReference<>();
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            Thread serverThread = new Thread(() -> runLoginServer(server, capture, serverFailure, serverSessionKey),
                    "protocol-self-test-login");
            serverThread.start();

            NetworkSession network = new NetworkSession();
            LoginSession login = new LoginSession(
                    network,
                    port -> new Socket(server.getInetAddress(), port),
                    index -> archiveCrcs[index],
                    new SilentStatusSink(),
                    listener,
                    BigInteger.ONE,
                    BigInteger.ONE.shiftLeft(1024).subtract(BigInteger.ONE),
                    server.getLocalPort());
            try {
                login.login("Rune Scape", "pass123", false, false);
            } finally {
                network.closeConnection();
            }
            serverThread.join();
        }
        rethrowServerFailure(serverFailure);

        test.equal(capture.handshake[0] & 0xff, 14, "login handshake opcode");
        int expectedHashPart = (int) (Base37.encode("Rune Scape") >> 16 & 31L);
        test.equal(capture.handshake[1] & 0xff, expectedHashPart, "login username hash partition");
        test.equal(capture.loginOpcode, 16, "full-login request opcode");
        test.equal(capture.body.length, capture.loginLength, "outer login length byte");
        test.equal(capture.body[0] & 0xff, 255, "client marker");
        test.equal(unsignedShort(capture.body, 1), 377, "client revision");
        test.equal(capture.body[3] & 0xff, 0, "low-memory flag");
        for (int index = 0; index < archiveCrcs.length; index++) {
            test.equal(readInt(capture.body, 4 + index * 4), archiveCrcs[index], "archive CRC " + index);
        }

        int rsaLength = capture.body[40] & 0xff;
        test.equal(rsaLength, capture.body.length - 41, "credential block length");
        byte[] credential = Arrays.copyOfRange(capture.body, 41, capture.body.length);
        test.equal(credential[0] & 0xff, 10, "credential block opcode");
        test.equal(readInt(credential, 9), (int) (serverSessionKey >>> 32), "server session key high word");
        test.equal(readInt(credential, 13), (int) serverSessionKey, "server session key low word");
        int usernameOffset = 21;
        test.equal(readTerminatedString(credential, usernameOffset), "Rune Scape", "credential username");
        int passwordOffset = usernameOffset + "Rune Scape".length() + 1;
        test.equal(readTerminatedString(credential, passwordOffset), "pass123", "credential password");
        test.equal(loginCaptureLength(capture), 2 + capture.body.length, "captured login frame size");
        test.check(listener.fullLogin, "full-login callback invoked");
        test.equal(listener.playerRights, 2, "player rights callback value");
        test.check(listener.accountFlagged, "account flagged callback value");
    }

    /**
     * Runs the server side of the loopback login fixture.
     *
     * @param server listening server socket
     * @param capture captured login bytes
     * @param failure asynchronous server failure sink
     * @param serverSessionKey session key returned to the client
     */
    private static void runLoginServer(ServerSocket server, LoginCapture capture, AtomicReference<Throwable> failure,
            long serverSessionKey) {
        try (Socket socket = server.accept();
                DataInputStream input = new DataInputStream(socket.getInputStream());
                DataOutputStream output = new DataOutputStream(socket.getOutputStream())) {
            input.readFully(capture.handshake);
            output.write(new byte[8]);
            output.writeByte(0);
            output.writeLong(serverSessionKey);
            output.flush();

            capture.loginOpcode = input.readUnsignedByte();
            capture.loginLength = input.readUnsignedByte();
            capture.body = new byte[capture.loginLength];
            input.readFully(capture.body);

            output.writeByte(2);
            output.writeByte(2);
            output.writeByte(1);
            output.flush();
        } catch (Throwable throwable) {
            failure.set(throwable);
        }
    }

    /**
     * Returns the total captured outer login frame size.
     *
     * @param capture captured login frame
     * @return frame size including opcode and length byte
     */
    private static int loginCaptureLength(LoginCapture capture) {
        return 2 + capture.body.length;
    }

    /**
     * Reads one unsigned big-endian short from a fixture byte array.
     *
     * @param bytes source bytes
     * @param offset source offset
     * @return unsigned short value
     */
    private static int unsignedShort(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) << 8 | bytes[offset + 1] & 0xff;
    }

    /**
     * Reads one big-endian int from a fixture byte array.
     *
     * @param bytes source bytes
     * @param offset source offset
     * @return decoded integer
     */
    private static int readInt(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) << 24
                | (bytes[offset + 1] & 0xff) << 16
                | (bytes[offset + 2] & 0xff) << 8
                | bytes[offset + 3] & 0xff;
    }

    /**
     * Reads one line-feed-terminated fixture string.
     *
     * @param bytes source bytes
     * @param offset source offset
     * @return decoded string
     */
    private static String readTerminatedString(byte[] bytes, int offset) {
        int end = offset;
        while (bytes[end] != 0x0a) {
            end++;
        }
        return new String(bytes, offset, end - offset, StandardCharsets.ISO_8859_1);
    }

    /**
     * Rethrows an asynchronous loopback-server failure on the test thread.
     *
     * @param failure failure reference
     * @throws Exception when the server failed with an exception
     */
    private static void rethrowServerFailure(AtomicReference<Throwable> failure) throws Exception {
        Throwable throwable = failure.get();
        if (throwable == null) {
            return;
        }
        if (throwable instanceof Exception exception) {
            throw exception;
        }
        throw new AssertionError("Loopback protocol server failed", throwable);
    }

    /** Captured bytes from the loopback login server. */
    private static final class LoginCapture {
        /** Creates an empty login capture. */
        private LoginCapture() {
        }

        /** Initial two-byte handshake. */
        private final byte[] handshake = new byte[2];
        /** Outer login opcode. */
        private int loginOpcode;
        /** Outer login payload length. */
        private int loginLength;
        /** Outer login payload. */
        private byte[] body;
    }

    /** Successful-login callback capture. */
    private static final class LoginListener implements LoginSession.Listener {
        /** Creates an empty callback capture. */
        private LoginListener() {
        }

        /** Whether the full-login callback ran. */
        private boolean fullLogin;
        /** Captured rights value. */
        private int playerRights;
        /** Captured account flag. */
        private boolean accountFlagged;

        @Override
        public void onFullLogin(int rights, boolean flagged) {
            fullLogin = true;
            playerRights = rights;
            accountFlagged = flagged;
        }

        @Override
        public void onReconnectAccepted() {
            throw new AssertionError("Unexpected reconnect callback");
        }
    }

    /** Login status sink that intentionally suppresses presentation. */
    private static final class SilentStatusSink implements LoginSession.StatusSink {
        /** Creates a silent status sink. */
        private SilentStatusSink() {
        }

        @Override
        public void setMessage(String line1, String line2) {
            // Status text is presentation state and is not part of this framing test.
        }

        @Override
        public void redraw() {
            // No UI is created by the executable protocol test.
        }
    }
}
