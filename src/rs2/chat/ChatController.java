package rs2.chat;

/**
 * Owns mutable chat modes, input, prompts, dialogue-input state, scrolling, and
 * fixed chat history for the logged-in client UI.
 *
 * <p>
 * Rendering and network packet construction remain outside this class during
 * the first client-decomposition phase; this owner centralizes the state those
 * operations act upon.
 * </p>
 */
public final class ChatController {

	/** Initial minimum chat-history content height. */
	public static final int MIN_CONTENT_HEIGHT = 78;

	/** Fixed newest-first chat history. */
	private final ChatHistory history = new ChatHistory();
	/** Public-chat visibility mode. */
	private int publicMode;
	/** Private-chat visibility mode. */
	private int privateMode;
	/** Trade/challenge visibility mode. */
	private int tradeMode;
	/** Split-private-chat setting. */
	private int splitPrivateChat;
	/** Normal chat-entry text. */
	private String input = "";
	/** Chat-history scroll offset from the bottom. */
	private int scrollOffset;
	/** Rendered chat-history content height. */
	private int contentHeight = MIN_CONTENT_HEIGHT;
	/** Whether the generic name/message prompt owns keyboard focus. */
	private boolean promptRaised;
	/** Heading displayed above the generic prompt. */
	private String promptMessage = "";
	/** Text currently entered in the generic prompt. */
	private String promptInput = "";
	/** Action discriminator for the generic prompt. */
	private int promptAction;
	/** Base-37 target used by the private-message prompt. */
	private long privateMessageTarget;
	/** Numeric/name/item-search input-dialog mode. */
	private int inputDialogState;
	/** Text entered in the active input dialog. */
	private String inputDialogText = "";
	/** Click-to-continue dialogue text, or {@code null} when absent. */
	private String clickToContinueMessage;

	/** Creates a chat controller with the revision-377 default state. */
	public ChatController() {
	}

	/**
	 * Returns the fixed newest-first chat history.
	 *
	 * @return chat history
	 */
	public ChatHistory history() {
		return history;
	}

	/**
	 * Returns the public-chat visibility mode.
	 *
	 * @return public-chat mode
	 */
	public int publicMode() {
		return publicMode;
	}

	/**
	 * Changes the public-chat visibility mode.
	 *
	 * @param mode new public-chat mode
	 */
	public void setPublicMode(int mode) {
		publicMode = mode;
	}

	/**
	 * Returns the private-chat visibility mode.
	 *
	 * @return private-chat mode
	 */
	public int privateMode() {
		return privateMode;
	}

	/**
	 * Changes the private-chat visibility mode.
	 *
	 * @param mode new private-chat mode
	 */
	public void setPrivateMode(int mode) {
		privateMode = mode;
	}

	/**
	 * Returns the trade/challenge visibility mode.
	 *
	 * @return trade/challenge mode
	 */
	public int tradeMode() {
		return tradeMode;
	}

	/**
	 * Changes the trade/challenge visibility mode.
	 *
	 * @param mode new trade/challenge mode
	 */
	public void setTradeMode(int mode) {
		tradeMode = mode;
	}

	/**
	 * Returns the split-private-chat setting.
	 *
	 * @return split-private-chat value
	 */
	public int splitPrivateChat() {
		return splitPrivateChat;
	}

	/**
	 * Changes the split-private-chat setting.
	 *
	 * @param value new setting value
	 */
	public void setSplitPrivateChat(int value) {
		splitPrivateChat = value;
	}

	/**
	 * Returns the current normal chat input.
	 *
	 * @return chat-entry text
	 */
	public String input() {
		return input;
	}

	/**
	 * Replaces the current normal chat input.
	 *
	 * @param value new chat-entry text
	 */
	public void setInput(String value) {
		input = value;
	}

	/**
	 * Returns the chat-history scroll offset.
	 *
	 * @return scroll offset
	 */
	public int scrollOffset() {
		return scrollOffset;
	}

	/**
	 * Changes the chat-history scroll offset.
	 *
	 * @param value new scroll offset
	 */
	public void setScrollOffset(int value) {
		scrollOffset = value;
	}

	/**
	 * Returns the rendered chat-history content height.
	 *
	 * @return content height
	 */
	public int contentHeight() {
		return contentHeight;
	}

	/**
	 * Changes the rendered chat-history content height.
	 *
	 * @param value new content height
	 */
	public void setContentHeight(int value) {
		contentHeight = value;
	}

	/**
	 * Opens a generic name/message prompt and clears other text-entry modes.
	 *
	 * @param action  prompt action identifier
	 * @param message prompt heading
	 */
	public void openPrompt(int action, String message) {
		inputDialogState = 0;
		promptRaised = true;
		promptInput = "";
		promptAction = action;
		promptMessage = message;
	}

	/**
	 * Opens the private-message prompt for one encoded friend name.
	 *
	 * @param target      encoded target name
	 * @param displayName friend display name
	 */
	public void openPrivateMessagePrompt(long target, String displayName) {
		openPrompt(3, "Enter message to send to " + displayName);
		privateMessageTarget = target;
	}

	/** Closes the generic prompt without altering its last text/action values. */
	public void closePrompt() {
		promptRaised = false;
	}

	/**
	 * Reports whether the generic prompt owns keyboard focus.
	 *
	 * @return whether the prompt is open
	 */
	public boolean isPromptRaised() {
		return promptRaised;
	}

	/**
	 * Returns the generic prompt heading.
	 *
	 * @return prompt heading
	 */
	public String promptMessage() {
		return promptMessage;
	}

	/**
	 * Returns text currently entered in the prompt.
	 *
	 * @return prompt input
	 */
	public String promptInput() {
		return promptInput;
	}

	/**
	 * Replaces text currently entered in the prompt.
	 *
	 * @param value new prompt input
	 */
	public void setPromptInput(String value) {
		promptInput = value;
	}

	/**
	 * Returns the generic prompt action discriminator.
	 *
	 * @return prompt action
	 */
	public int promptAction() {
		return promptAction;
	}

	/**
	 * Returns the encoded private-message target.
	 *
	 * @return encoded target
	 */
	public long privateMessageTarget() {
		return privateMessageTarget;
	}

	/**
	 * Returns the active numeric/name/item-search input-dialog mode.
	 *
	 * @return mode
	 */
	public int inputDialogState() {
		return inputDialogState;
	}

	/**
	 * Changes the active input-dialog mode.
	 *
	 * @param state new mode
	 */
	public void setInputDialogState(int state) {
		inputDialogState = state;
	}

	/**
	 * Opens an input-dialog mode with empty text and closes the generic prompt.
	 *
	 * @param state input-dialog mode
	 */
	public void openInputDialog(int state) {
		promptRaised = false;
		inputDialogState = state;
		inputDialogText = "";
	}

	/**
	 * Returns text entered in the active input dialog.
	 *
	 * @return dialog text
	 */
	public String inputDialogText() {
		return inputDialogText;
	}

	/**
	 * Replaces text entered in the active input dialog.
	 *
	 * @param value new dialog text
	 */
	public void setInputDialogText(String value) {
		inputDialogText = value;
	}

	/**
	 * Returns click-to-continue dialogue text.
	 *
	 * @return message, or {@code null}
	 */
	public String clickToContinueMessage() {
		return clickToContinueMessage;
	}

	/**
	 * Changes click-to-continue dialogue text.
	 *
	 * @param message message, or {@code null}
	 */
	public void setClickToContinueMessage(String message) {
		clickToContinueMessage = message;
	}

	/**
	 * Resets chat/input state cleared after successful login while retaining
	 * configured chat modes.
	 */
	public void resetForLogin() {
		history.clearMessages();
		inputDialogState = 0;
		promptRaised = false;
		clickToContinueMessage = null;
	}
}
