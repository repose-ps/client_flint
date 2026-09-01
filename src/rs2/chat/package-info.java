/**
 * Contains chat text codecs, filtering, social-list management, and the mutable
 * chat/input state used by the revision-377 client.
 *
 * <p>
 * {@link rs2.chat.ChatController} owns chat modes, prompts, text-entry state,
 * scrolling, and history while packet construction and rendering remain with
 * their higher-level client subsystems.
 * </p>
 */
package rs2.chat;
