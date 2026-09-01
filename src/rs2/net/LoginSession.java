package rs2.net;

import java.io.IOException;
import java.math.BigInteger;
import java.net.Socket;

import rs2.sign.Signlink;
import rs2.text.Base37;

/**
 * Performs the revision-377 login handshake independently of the login-screen
 * presentation and post-login client reset.
 *
 * <p>
 * The session owns retry counters, the server session key, and the temporary
 * login packet buffer. Successful response codes are reported through
 * {@link Listener}; login-screen messages and redraws are abstracted through
 * {@link StatusSink} so protocol code does not depend on UI classes.
 * </p>
 */
public final class LoginSession {

	/** Initial revision-377 login handshake opcode. */
	private static final int HANDSHAKE_OPCODE = 14;
	/** Normal login request opcode. */
	private static final int LOGIN_OPCODE = 16;
	/** Reconnect login request opcode. */
	private static final int RECONNECT_OPCODE = 18;
	/** RSA credential-block opcode. */
	private static final int CREDENTIAL_BLOCK_OPCODE = 10;
	/** Revision encoded in the login block. */
	private static final int CLIENT_REVISION = 377;
	/** Legacy client marker preceding the revision. */
	private static final int CLIENT_MARKER = 255;
	/** Number of archive CRC values included by revision 377. */
	private static final int ARCHIVE_CRC_COUNT = 9;
	/** Number of ignored bytes sent before the first login response. */
	private static final int HANDSHAKE_PADDING_LENGTH = 8;
	/** Maximum retries after an EOF response following a valid handshake. */
	private static final int MAX_NO_RESPONSE_RETRIES = 2;
	/** Delay before retrying response code 1 or an initial EOF. */
	private static final long RETRY_DELAY_MILLIS = 2_000L;
	/** Profile-transfer countdown delay. */
	private static final long TRANSFER_COUNTDOWN_MILLIS = 1_200L;
	/** Extra seconds added to the server transfer countdown. */
	private static final int TRANSFER_COUNTDOWN_OFFSET = 3;
	/** Temporary login packet capacity. */
	private static final int LOGIN_BUFFER_CAPACITY = 5_000;

	/** Opens the game socket used by the login protocol. */
	@FunctionalInterface
	public interface SocketOpener {
		/**
		 * Opens the requested port.
		 *
		 * @param port server port
		 * @return connected socket
		 * @throws IOException if the connection cannot be opened
		 */
		Socket open(int port) throws IOException;
	}

	/** Supplies packed archive CRCs for the login request. */
	@FunctionalInterface
	public interface ArchiveCrcProvider {
		/**
		 * Returns one archive CRC.
		 *
		 * @param index archive index
		 * @return packed archive CRC
		 */
		int getArchiveCrc(int index);
	}

	/**
	 * Receives login-screen status changes without coupling protocol code to UI.
	 */
	public interface StatusSink {
		/**
		 * Changes the two login status lines.
		 *
		 * @param line1 first status line
		 * @param line2 second status line
		 */
		void setMessage(String line1, String line2);

		/** Redraws the current login status immediately. */
		void redraw();
	}

	/** Receives successful login response variants. */
	public interface Listener {
		/**
		 * Applies a complete fresh-login reset.
		 *
		 * @param playerRights   server supplied player-rights level
		 * @param accountFlagged whether the account is flagged
		 */
		void onFullLogin(int playerRights, boolean accountFlagged);

		/** Applies the partial state reset used by response code 15. */
		void onReconnectAccepted();
	}

	/** Live transport used for handshake bytes and post-login ISAAC state. */
	private final NetworkSession network;
	/** Opens the configured game-server socket. */
	private final SocketOpener socketOpener;
	/** Supplies the nine archive CRCs embedded in the login request. */
	private final ArchiveCrcProvider archiveCrcProvider;
	/**
	 * Receives human-readable login status without coupling to login UI classes.
	 */
	private final StatusSink statusSink;
	/** Receives successful full and reconnect login responses. */
	private final Listener listener;
	/** RSA public exponent used for the credential block. */
	private final BigInteger rsaExponent;
	/** RSA modulus used for the credential block. */
	private final BigInteger rsaModulus;
	/** TCP port used for revision-377 game/login connections. */
	private final int gamePort;
	/** Temporary buffer containing the outer revision-377 login request. */
	private final Buffer loginBuffer = new Buffer(LOGIN_BUFFER_CAPACITY);

	/**
	 * Number of bounded EOF retries attempted after a successful initial handshake.
	 */
	private int loginFailures;
	/** Most recently supplied 64-bit server session key. */
	private long serverSessionKey;

	/**
	 * Creates a revision-377 login protocol owner.
	 *
	 * @param network            live network session
	 * @param socketOpener       game-socket opener
	 * @param archiveCrcProvider archive CRC source
	 * @param statusSink         login status sink
	 * @param listener           successful-login listener
	 * @param rsaExponent        RSA public exponent
	 * @param rsaModulus         RSA modulus
	 * @param gamePort           game server port
	 */
	public LoginSession(NetworkSession network, SocketOpener socketOpener, ArchiveCrcProvider archiveCrcProvider,
			StatusSink statusSink, Listener listener, BigInteger rsaExponent, BigInteger rsaModulus, int gamePort) {
		this.network = network;
		this.socketOpener = socketOpener;
		this.archiveCrcProvider = archiveCrcProvider;
		this.statusSink = statusSink;
		this.listener = listener;
		this.rsaExponent = rsaExponent;
		this.rsaModulus = rsaModulus;
		this.gamePort = gamePort;
	}

	/**
	 * Resets the bounded no-response retry counter before a user-initiated attempt.
	 */
	public void resetFailures() {
		loginFailures = 0;
	}

	/**
	 * Returns the most recently supplied server session key.
	 *
	 * @return server session key, or zero before a handshake supplies one
	 */
	public long getServerSessionKey() {
		return serverSessionKey;
	}

	/**
	 * Performs one logical login attempt, including the legacy retry responses.
	 *
	 * @param username     login username
	 * @param password     login password
	 * @param reconnecting whether this is an in-game reconnect
	 * @param lowMemory    whether the client is using low-memory mode
	 */
	public void login(String username, String password, boolean reconnecting, boolean lowMemory) {
		try {
			if (!reconnecting) {
				statusSink.setMessage("", "Connecting to server...");
				statusSink.redraw();
			}
			network.connect(socketOpener.open(gamePort));
			long encodedUsername = Base37.encode(username);
			int usernameHashPart = (int) (encodedUsername >> 16 & 31L);
			network.outgoing.position = 0;
			network.outgoing.writeByte(HANDSHAKE_OPCODE);
			network.outgoing.writeByte(usernameHashPart);
			network.write(network.outgoing.payload, 0, 2);
			for (int index = 0; index < HANDSHAKE_PADDING_LENGTH; index++) {
				network.read();
			}

			int responseCode = network.read();
			int initialResponseCode = responseCode;
			if (responseCode == 0) {
				responseCode = sendCredentialBlock(username, password, reconnecting, lowMemory);
			}
			if (responseCode == 1) {
				sleep(RETRY_DELAY_MILLIS);
				login(username, password, reconnecting, lowMemory);
				return;
			}
			if (responseCode == 2) {
				int playerRights = network.read();
				boolean accountFlagged = network.read() == 1;
				listener.onFullLogin(playerRights, accountFlagged);
				return;
			}
			if (responseCode == 15) {
				listener.onReconnectAccepted();
				return;
			}
			if (responseCode == 21) {
				runTransferCountdown(username, password, reconnecting, lowMemory);
				return;
			}
			if (responseCode == -1) {
				handleNoResponse(initialResponseCode, username, password, reconnecting, lowMemory);
				return;
			}
			if (setKnownFailureMessage(responseCode)) {
				return;
			}
			System.out.println("response:" + responseCode);
			statusSink.setMessage("Unexpected server response", "Please try using a different world.");
		} catch (IOException ignored) {
			statusSink.setMessage("", "Error connecting to server.");
		}
	}

	/**
	 * Builds, RSA-encrypts, and sends the credential/login block after response
	 * zero.
	 *
	 * @param username     login username
	 * @param password     login password
	 * @param reconnecting whether to use the reconnect request opcode
	 * @param lowMemory    whether low-memory mode is active
	 * @return the server response following the complete login request
	 * @throws IOException if the handshake transport fails
	 */
	private int sendCredentialBlock(String username, String password, boolean reconnecting, boolean lowMemory)
			throws IOException {
		network.readFully(network.incoming.payload, 0, 8);
		network.incoming.position = 0;
		serverSessionKey = network.incoming.readLong();
		int[] isaacSeed = new int[4];
		isaacSeed[0] = (int) (Math.random() * 99_999_999D);
		isaacSeed[1] = (int) (Math.random() * 99_999_999D);
		isaacSeed[2] = (int) (serverSessionKey >> 32);
		isaacSeed[3] = (int) serverSessionKey;

		network.outgoing.position = 0;
		network.outgoing.writeByte(CREDENTIAL_BLOCK_OPCODE);
		network.outgoing.writeInt(isaacSeed[0]);
		network.outgoing.writeInt(isaacSeed[1]);
		network.outgoing.writeInt(isaacSeed[2]);
		network.outgoing.writeInt(isaacSeed[3]);
		network.outgoing.writeInt(Signlink.uid);
		network.outgoing.writeString(username);
		network.outgoing.writeString(password);
		network.outgoing.encryptRsa(rsaExponent, rsaModulus);

		loginBuffer.position = 0;
		loginBuffer.writeByte(reconnecting ? RECONNECT_OPCODE : LOGIN_OPCODE);
		loginBuffer.writeByte(network.outgoing.position + 36 + 1 + 1 + 2);
		loginBuffer.writeByte(CLIENT_MARKER);
		loginBuffer.writeShort(CLIENT_REVISION);
		loginBuffer.writeByte(lowMemory ? 1 : 0);
		for (int crcIndex = 0; crcIndex < ARCHIVE_CRC_COUNT; crcIndex++) {
			loginBuffer.writeInt(archiveCrcProvider.getArchiveCrc(crcIndex));
		}
		loginBuffer.writeBytes(network.outgoing.payload, 0, network.outgoing.position);
		network.initializeOpcodeCiphers(isaacSeed);
		network.write(loginBuffer.payload, 0, loginBuffer.position);
		return network.read();
	}

	/**
	 * Displays response-code 21's profile-transfer countdown and retries login.
	 *
	 * @param username     login username
	 * @param password     login password
	 * @param reconnecting whether this is a reconnect attempt
	 * @param lowMemory    whether low-memory mode is active
	 * @throws IOException if the countdown byte cannot be read
	 */
	private void runTransferCountdown(String username, String password, boolean reconnecting, boolean lowMemory)
			throws IOException {
		int transferSeconds = network.read() + TRANSFER_COUNTDOWN_OFFSET;
		for (; transferSeconds >= 0; transferSeconds--) {
			statusSink.setMessage("You have only just left another world",
					"Your profile will be transferred in: " + transferSeconds);
			statusSink.redraw();
			sleep(TRANSFER_COUNTDOWN_MILLIS);
		}
		login(username, password, reconnecting, lowMemory);
	}

	/**
	 * Applies the original bounded retry behavior for an EOF/no-response result.
	 *
	 * @param initialResponseCode response received before any credential block
	 * @param username            login username
	 * @param password            login password
	 * @param reconnecting        whether this is a reconnect attempt
	 * @param lowMemory           whether low-memory mode is active
	 */
	private void handleNoResponse(int initialResponseCode, String username, String password, boolean reconnecting,
			boolean lowMemory) {
		if (initialResponseCode == 0) {
			if (loginFailures < MAX_NO_RESPONSE_RETRIES) {
				sleep(RETRY_DELAY_MILLIS);
				loginFailures++;
				login(username, password, reconnecting, lowMemory);
			} else {
				statusSink.setMessage("No response from loginserver", "Please wait 1 minute and try again.");
			}
		} else {
			statusSink.setMessage("No response from server", "Please try using a different world.");
		}
	}

	/**
	 * Maps known revision-377 login failure codes to their classic messages.
	 *
	 * @param responseCode server login response code
	 * @return {@code true} when the response code has a known message
	 */
	private boolean setKnownFailureMessage(int responseCode) {
		return switch (responseCode) {
		case 3 -> message("", "Invalid username or password.");
		case 4 -> message("Your account has been disabled.", "Please check your message-centre for details.");
		case 5 -> message("Your account is already logged in.", "Try again in 60 secs...");
		case 6 -> message("RuneScape has been updated!", "Please reload this page.");
		case 7 -> message("This world is full.", "Please use a different world.");
		case 8 -> message("Unable to connect.", "Login server offline.");
		case 9 -> message("Login limit exceeded.", "Too many connections from your address.");
		case 10 -> message("Unable to connect.", "Bad session id.");
		case 12 -> message("You need a members account to login to this world.",
				"Please subscribe, or use a different world.");
		case 13 -> message("Could not complete login.", "Please try using a different world.");
		case 14 -> message("The server is being updated.", "Please wait 1 minute and try again.");
		case 16 -> message("Login attempts exceeded.", "Please wait 1 minute and try again.");
		case 17 ->
			message("You are standing in a members-only area.", "To play on this world move to a free area first");
		case 18 -> message("Account locked as we suspect it has been stolen.",
				"Press 'recover a locked account' on front page.");
		case 20 -> message("Invalid loginserver requested", "Please try using a different world.");
		case 22 -> message("Malformed login packet.", "Please try again.");
		case 23 -> message("No reply from loginserver.", "Please try again.");
		case 24 -> message("Error loading your profile.", "Please contact customer support.");
		case 25 -> message("Unexpected loginserver response.", "Please try using a different world.");
		case 26 -> message("This computers address has been blocked", "as it was used to break our rules");
		default -> false;
		};
	}

	/**
	 * Sends a two-line failure message to the presentation sink.
	 *
	 * @param line1 first status line
	 * @param line2 second status line
	 * @return always {@code true}, for switch-expression use
	 */
	private boolean message(String line1, String line2) {
		statusSink.setMessage(line1, line2);
		return true;
	}

	/**
	 * Sleeps for one legacy retry/countdown delay, ignoring interruption as the
	 * original client did.
	 *
	 * @param millis delay in milliseconds
	 */
	private static void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException ignored) {
		}
	}
}
