package rs2.action;

import rs2.chat.ChatController;
import rs2.chat.ChatMessageType;
import rs2.chat.SocialManager;
import rs2.game.ActorSynchronizer;
import rs2.game.entity.Actor;
import rs2.game.entity.Player;
import rs2.game.render.GameRenderer;
import rs2.net.MovementPacketEncoder;
import rs2.text.Base37;
import rs2.text.TextFormatter;
import rs2.ui.InterfaceController;
import rs2.ui.Widget;
import rs2.ui.menu.MenuEntry;
import rs2.ui.menu.MenuState;

/** Applies revision-377 social, message, report, trade, and challenge menu actions. */
public final class SocialActionHandler implements ClientActionDispatcher.ActionHandler {
    /** Social-list owner. */
    private final SocialManager socialManager;
    /** Actor state used for name-based trade/challenge targeting. */
    private final ActorSynchronizer actors;
    /** Revision-377 action packet encoder. */
    private final ActionPacketEncoder packets;
    /** Interface/report-abuse state owner. */
    private final InterfaceController interfaces;
    /** Renderer invalidation owner. */
    private final GameRenderer gameRenderer;
    /** Chat/prompt owner. */
    private final ChatController chatController;
    /** Movement capability supplied by the application coordinator. */
    private final ClientActionDispatcher.Movement movement;
    /** Chat-message sink. */
    private final SocialManager.MessageSink messages;
    /** Existing social-list mutation entry points. */
    private final ClientActionDispatcher.SocialListActions listActions;
    /** Shared interface-close operation. */
    private final Runnable closeInterfaces;

    /**
     * Creates the social action handler.
     *
     * @param socialManager social-list owner
     * @param actors actor state
     * @param packets revision-377 action packet encoder
     * @param interfaces interface/report-abuse owner
     * @param gameRenderer renderer invalidation owner
     * @param chatController chat/prompt owner
     * @param movement interaction movement capability
     * @param messages chat-message sink
     * @param listActions social-list mutation capability
     * @param closeInterfaces shared interface-close operation
     */
    public SocialActionHandler(SocialManager socialManager, ActorSynchronizer actors,
            ActionPacketEncoder packets, InterfaceController interfaces, GameRenderer gameRenderer,
            ChatController chatController, ClientActionDispatcher.Movement movement, SocialManager.MessageSink messages,
            ClientActionDispatcher.SocialListActions listActions, Runnable closeInterfaces) {
        this.socialManager = socialManager;
        this.actors = actors;
        this.packets = packets;
        this.interfaces = interfaces;
        this.gameRenderer = gameRenderer;
        this.chatController = chatController;
        this.movement = movement;
        this.messages = messages;
        this.listActions = listActions;
        this.closeInterfaces = closeInterfaces;
    }

    /** {@inheritDoc} */
    @Override
    public boolean dispatch(int actionId, MenuEntry entry) {
        int argument0 = entry.argument0();
        int argument1 = entry.argument1();
        int argument2 = entry.argument2();
        if (actionId == MenuState.ADD_FRIEND || actionId == MenuState.ADD_IGNORE
                || actionId == MenuState.REMOVE_FRIEND || actionId == MenuState.REMOVE_IGNORE) {
            String actionText = entry.text();
            int markerIndex = actionText.indexOf("@whi@");
            if (markerIndex != -1) {
                long encodedName = Base37.encode(actionText.substring(markerIndex + 5).trim());
                if (actionId == MenuState.ADD_FRIEND) {
                    listActions.addFriend(encodedName);
                }
                if (actionId == MenuState.ADD_IGNORE) {
                    listActions.addIgnore(encodedName);
                }
                if (actionId == MenuState.REMOVE_FRIEND) {
                    listActions.removeFriend(encodedName);
                }
                if (actionId == MenuState.REMOVE_IGNORE) {
                    listActions.removeIgnore(encodedName);
                }
            }
        }
        if (actionId == MenuState.ACCEPT_TRADE || actionId == MenuState.ACCEPT_CHALLENGE) {
            String actionText2 = entry.text();
            int markerIndex2 = actionText2.indexOf("@whi@");
            if (markerIndex2 != -1) {
                actionText2 = actionText2.substring(markerIndex2 + 5).trim();
                String encodedName2 = TextFormatter.formatDisplayName(Base37.decode(Base37.encode(actionText2)));
                boolean playerFound = false;
                for (int activePlayerIndex = 0; activePlayerIndex < actors.playerCount; activePlayerIndex++) {
                    Player player = actors.players[actors.playerIndices[activePlayerIndex]];
                    if (player == null || player.name == null || !player.name.equalsIgnoreCase(encodedName2)) {
                        continue;
                    }
                    movement.walkTo(false, ((Actor) player).pathX[0], ((Actor) player).pathY[0], 1, 1,
                            MovementPacketEncoder.INTERACTION, 0, 0, 0);
                    if (actionId == MenuState.ACCEPT_TRADE) {
                        packets.acceptTrade(actors.playerIndices[activePlayerIndex]);
                    }
                    if (actionId == MenuState.ACCEPT_CHALLENGE) {
                        packets.acceptChallenge(actors.playerIndices[activePlayerIndex]);
                    }
                    playerFound = true;
                    break;
                }

                if (!playerFound) {
                    messages.addChatMessage("", "Unable to find " + encodedName2, ChatMessageType.GAME);
                }
            }
        }
        if (actionId == MenuState.REPORT_ABUSE) {
            String actionText3 = entry.text();
            int markerIndex3 = actionText3.indexOf("@whi@");
            if (markerIndex3 != -1) {
                if (interfaces.state().openInterfaceId == -1) {
                    closeInterfaces.run();
                    interfaces.setReportAbuseName(actionText3.substring(markerIndex3 + 5).trim());
                    interfaces.setReportAbuseMutePlayer(false);
                    interfaces.state().reportAbuseInterfaceId = interfaces.state().openInterfaceId = Widget.reportAbuseInterfaceId;
                } else {
                    messages.addChatMessage("", "Please close the interface you have open before using 'report abuse'",
                            ChatMessageType.GAME);
                }
            }
        }
        if (actionId == MenuState.MESSAGE_FRIEND) {
            String actionText4 = entry.text();
            int markerIndex4 = actionText4.indexOf("@whi@");
            if (markerIndex4 != -1) {
                long encodedName3 = Base37.encode(actionText4.substring(markerIndex4 + 5).trim());
                int friendIndex = socialManager.findFriendIndex(encodedName3);

                if (friendIndex != -1 && socialManager.friendWorlds[friendIndex] > 0) {
                    gameRenderer.requestChatboxRedraw();
                    chatController.openPrivateMessagePrompt(socialManager.friendEncodedNames[friendIndex],
                            socialManager.friendNames[friendIndex]);
                }
            }
        }
        return false;
    }
}
