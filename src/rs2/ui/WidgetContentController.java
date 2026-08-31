package rs2.ui;

import java.util.Calendar;
import java.util.Date;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import rs2.chat.SocialManager;
import rs2.game.entity.Player;
import rs2.text.Base37;
import rs2.text.TextFormatter;

/**
 * Owns application-defined dynamic content for cache-defined revision-377
 * widgets.
 *
 * <p>The controller translates social, appearance, report-abuse, and account
 * state into the mutable widget fields consumed by {@link WidgetRenderer}.
 * Rendering remains responsible only for drawing the resulting widget state;
 * the top-level client is not exposed through a generic mutation callback.</p>
 */
public final class WidgetContentController {

    /**
     * Immutable account-panel values required while refreshing account-status
     * widgets.
     *
     * @param currentDay server supplied current account day
     * @param lastLoginDay server supplied previous-login day
     * @param recoveryQuestionsDate recovery-question status day
     * @param unreadMessageCount current unread message count
     * @param lastPasswordChangeDate last password-change day
     * @param membershipDays remaining membership credit
     * @param membersWorld whether the current world enables members features
     * @param lastLoginHost resolved host text for the previous login address
     */
    public record AccountStatus(
            int currentDay,
            int lastLoginDay,
            int recoveryQuestionsDate,
            int unreadMessageCount,
            int lastPasswordChangeDate,
            int membershipDays,
            boolean membersWorld,
            String lastLoginHost) {
    }

    /** Friend and ignore list owner. */
    private final SocialManager socialManager;
    /** Character-design state and preview owner. */
    private final AppearanceEditor appearanceEditor;
    /** Report-abuse interface state owner. */
    private final InterfaceController interfaceController;
    /** Reads the current client cycle. */
    private final IntSupplier gameCycle;
    /** Reads the local player used by the appearance preview. */
    private final Supplier<Player> localPlayer;
    /** Reads the current world identifier used by friend-world rows. */
    private final IntSupplier currentWorldId;
    /** Reads the local player's moderator-rights level. */
    private final IntSupplier playerRights;
    /** Supplies the latest account-panel state as one cohesive snapshot. */
    private final Supplier<AccountStatus> accountStatus;

    /**
     * Creates the dynamic widget-content owner from narrow application state
     * sources.
     *
     * @param socialManager friend and ignore state owner
     * @param appearanceEditor character-design state owner
     * @param interfaceController report-abuse interface state owner
     * @param gameCycle current client-cycle reader
     * @param localPlayer local-player reader
     * @param currentWorldId current-world reader
     * @param playerRights moderator-rights reader
     * @param accountStatus account-panel snapshot supplier
     */
    public WidgetContentController(SocialManager socialManager, AppearanceEditor appearanceEditor,
            InterfaceController interfaceController, IntSupplier gameCycle, Supplier<Player> localPlayer,
            IntSupplier currentWorldId, IntSupplier playerRights, Supplier<AccountStatus> accountStatus) {
        this.socialManager = socialManager;
        this.appearanceEditor = appearanceEditor;
        this.interfaceController = interfaceController;
        this.gameCycle = gameCycle;
        this.localPlayer = localPlayer;
        this.currentWorldId = currentWorldId;
        this.playerRights = playerRights;
        this.accountStatus = accountStatus;
    }

    /**
     * Refreshes one cache-defined widget whose content type is interpreted by the
     * client.
     *
     * @param widget widget to refresh
     */
    public void update(Widget widget) {
        int contentType = widget.contentType;
        if (contentType >= WidgetContentType.FRIEND_NAME_FIRST && contentType <= WidgetContentType.FRIEND_NAME_LAST
                || contentType >= WidgetContentType.FRIEND_NAME_ALTERNATE_FIRST
                        && contentType <= WidgetContentType.FRIEND_NAME_ALTERNATE_LAST) {
            if (contentType == WidgetContentType.FRIEND_NAME_FIRST
                    && socialManager.friendListStatus == SocialManager.FRIEND_LIST_LOADING) {
                widget.text = "Loading friend list";
                widget.buttonType = Widget.BUTTON_NONE;
                return;
            }
            if (contentType == WidgetContentType.FRIEND_NAME_FIRST
                    && socialManager.friendListStatus == SocialManager.FRIEND_LIST_CONNECTING) {
                widget.text = "Connecting to friendserver";
                widget.buttonType = Widget.BUTTON_NONE;
                return;
            }
            if (contentType == WidgetContentType.FRIEND_NAME_SECOND
                    && socialManager.friendListStatus != SocialManager.FRIEND_LIST_READY) {
                widget.text = "Please wait...";
                widget.buttonType = Widget.BUTTON_NONE;
                return;
            }
            int friendCount = socialManager.friendCount;
            if (socialManager.friendListStatus != SocialManager.FRIEND_LIST_READY)
                friendCount = 0;
            if (contentType >= WidgetContentType.FRIEND_NAME_ALTERNATE_FIRST)
                contentType -= WidgetContentType.FRIEND_NAME_ALTERNATE_INDEX_OFFSET;
            else
                contentType -= WidgetContentType.FRIEND_NAME_FIRST;
            if (contentType >= friendCount) {
                widget.text = "";
                widget.buttonType = Widget.BUTTON_NONE;
                return;
            } else {
                widget.text = socialManager.friendNames[contentType];
                widget.buttonType = Widget.BUTTON_ACTION;
                return;
            }
        }
        if (contentType >= WidgetContentType.FRIEND_WORLD_FIRST && contentType <= WidgetContentType.FRIEND_WORLD_LAST
                || contentType >= WidgetContentType.FRIEND_WORLD_ALTERNATE_FIRST
                        && contentType <= WidgetContentType.FRIEND_WORLD_ALTERNATE_LAST) {
            int friendCount = socialManager.friendCount;
            if (socialManager.friendListStatus != SocialManager.FRIEND_LIST_READY)
                friendCount = 0;
            if (contentType > WidgetContentType.FRIEND_NAME_ALTERNATE_LAST)
                contentType -= WidgetContentType.FRIEND_WORLD_ALTERNATE_INDEX_OFFSET;
            else
                contentType -= WidgetContentType.FRIEND_WORLD_FIRST;
            if (contentType >= friendCount) {
                widget.text = "";
                widget.buttonType = Widget.BUTTON_NONE;
                return;
            }
            if (socialManager.friendWorlds[contentType] == 0)
                widget.text = "@red@Offline";
            else if (socialManager.friendWorlds[contentType] < 200) {
                if (socialManager.friendWorlds[contentType] == currentWorldId.getAsInt())
                    widget.text = "@gre@World" + (socialManager.friendWorlds[contentType] - 9);
                else
                    widget.text = "@yel@World" + (socialManager.friendWorlds[contentType] - 9);
            } else if (socialManager.friendWorlds[contentType] == currentWorldId.getAsInt())
                widget.text = "@gre@Classic" + (socialManager.friendWorlds[contentType] - 219);
            else
                widget.text = "@yel@Classic" + (socialManager.friendWorlds[contentType] - 219);
            widget.buttonType = Widget.BUTTON_ACTION;
            return;
        }
        if (contentType == WidgetContentType.FRIEND_LIST_SCROLL) {
            int friendCount = socialManager.friendCount;
            if (socialManager.friendListStatus != SocialManager.FRIEND_LIST_READY)
                friendCount = 0;
            widget.scrollHeight = friendCount * 15 + 20;
            if (widget.scrollHeight <= widget.height)
                widget.scrollHeight = widget.height + 1;
            return;
        }
        if (contentType >= WidgetContentType.IGNORE_NAME_FIRST && contentType <= WidgetContentType.IGNORE_NAME_LAST) {
            if ((contentType -= WidgetContentType.IGNORE_NAME_FIRST) == 0
                    && socialManager.friendListStatus == SocialManager.FRIEND_LIST_LOADING) {
                widget.text = "Loading ignore list";
                widget.buttonType = Widget.BUTTON_NONE;
                return;
            }
            if (contentType == WidgetContentType.FRIEND_NAME_FIRST
                    && socialManager.friendListStatus == SocialManager.FRIEND_LIST_LOADING) {
                widget.text = "Please wait...";
                widget.buttonType = Widget.BUTTON_NONE;
                return;
            }
            int ignoreCount = socialManager.ignoreCount;
            if (socialManager.friendListStatus == SocialManager.FRIEND_LIST_LOADING)
                ignoreCount = 0;
            if (contentType >= ignoreCount) {
                widget.text = "";
                widget.buttonType = Widget.BUTTON_NONE;
                return;
            } else {
                widget.text = TextFormatter.formatDisplayName(Base37.decode(socialManager.ignoreEncodedNames[contentType]));
                widget.buttonType = Widget.BUTTON_ACTION;
                return;
            }
        }
        if (contentType == WidgetContentType.IGNORE_LIST_SCROLL) {
            widget.scrollHeight = socialManager.ignoreCount * 15 + 20;
            if (widget.scrollHeight <= widget.height)
                widget.scrollHeight = widget.height + 1;
            return;
        }
        if (contentType == WidgetContentType.APPEARANCE_PREVIEW) {
            appearanceEditor.updatePreview(widget, gameCycle.getAsInt(), localPlayer.get());
            return;
        }
        if (contentType == WidgetContentType.SELECT_MALE_APPEARANCE) {
            appearanceEditor.updateGenderButton(widget, true);
            return;
        }
        if (contentType == WidgetContentType.SELECT_FEMALE_APPEARANCE) {
            appearanceEditor.updateGenderButton(widget, false);
            return;
        }
        if (contentType == WidgetContentType.REPORT_ABUSE_NAME) {
            widget.text = interfaceController.reportAbuseName();
            if (gameCycle.getAsInt() % 20 < 10) {
                widget.text += "|";
                return;
            } else {
                widget.text += " ";
                return;
            }
        }
        if (contentType == WidgetContentType.REPORT_ABUSE_MUTE)
            if (playerRights.getAsInt() >= 1) {
                if (interfaceController.reportAbuseMutePlayer()) {
                    widget.color = 0xff0000;
                    widget.text = "Moderator option: Mute player for 48 hours: <ON>";
                } else {
                    widget.color = 0xffffff;
                    widget.text = "Moderator option: Mute player for 48 hours: <OFF>";
                }
            } else {
                widget.text = "";
            }
        if (isAccountContent(contentType)) {
            updateAccountContent(widget, contentType, accountStatus.get());
        }
    }

    /**
     * Reports whether a content type belongs to the legacy account-status panel.
     *
     * @param contentType widget content-type identifier
     * @return whether the content type requires account-panel state
     */
    private static boolean isAccountContent(int contentType) {
        return contentType == WidgetContentType.ACCOUNT_LAST_LOGIN
                || contentType == WidgetContentType.ACCOUNT_RECOVERY_QUESTIONS
                || contentType == WidgetContentType.ACCOUNT_UNREAD_MESSAGES
                || contentType == WidgetContentType.ACCOUNT_PASSWORD_CHANGE
                || contentType == WidgetContentType.ACCOUNT_MEMBERSHIP_STATUS
                || contentType == WidgetContentType.ACCOUNT_MEMBERSHIP_HELP
                || contentType == WidgetContentType.ACCOUNT_RECOVERY_HELP;
    }

    /**
     * Refreshes one account-status content widget from an immutable snapshot.
     *
     * @param widget widget to refresh
     * @param contentType widget content-type identifier
     * @param status current account-panel values
     */
    private static void updateAccountContent(Widget widget, int contentType, AccountStatus status) {
        if (contentType == WidgetContentType.ACCOUNT_LAST_LOGIN) {
            int daysSinceLogin = status.currentDay() - status.lastLoginDay();
            String lastLoginText;
            if (daysSinceLogin <= 0)
                lastLoginText = "earlier today";
            else if (daysSinceLogin == 1)
                lastLoginText = "yesterday";
            else
                lastLoginText = daysSinceLogin + " days ago";
            widget.text = "You last logged in @red@" + lastLoginText + "@bla@ from: @red@" + status.lastLoginHost();
        }
        if (contentType == WidgetContentType.ACCOUNT_RECOVERY_QUESTIONS)
            if (status.recoveryQuestionsDate() == 0)
                widget.text = "\\nYou have not yet set any recovery questions.\\nIt is @lre@strongly@yel@ recommended that you do so.\\n\\nIf you don't you will be @lre@unable to recover your\\n@lre@password@yel@ if you forget it, or it is stolen.";
            else if (status.recoveryQuestionsDate() <= status.currentDay()) {
                widget.text = "\\n\\nRecovery Questions Last Set:\\n@gre@"
                        + formatAccountDate(status.recoveryQuestionsDate(), status.currentDay());
            } else {
                int daysUntilRecoveryChange = (status.currentDay() + 14) - status.recoveryQuestionsDate();
                String recoveryChangeText;
                if (daysUntilRecoveryChange <= 0)
                    recoveryChangeText = "Earlier today";
                else if (daysUntilRecoveryChange == 1)
                    recoveryChangeText = "Yesterday";
                else
                    recoveryChangeText = daysUntilRecoveryChange + " days ago";
                widget.text = recoveryChangeText
                        + " you requested@lre@ new recovery\\n@lre@questions.@yel@ The requested change will occur\\non: @lre@"
                        + formatAccountDate(status.recoveryQuestionsDate(), status.currentDay())
                        + "\\n\\nIf you do not remember making this request\\ncancel it immediately, and change your password.";
            }
        if (contentType == WidgetContentType.ACCOUNT_UNREAD_MESSAGES) {
            String messageSummary;
            if (status.unreadMessageCount() == 0)
                messageSummary = "@yel@0 unread messages";
            else if (status.unreadMessageCount() == 1)
                messageSummary = "@gre@1 unread message";
            else
                messageSummary = "@gre@" + status.unreadMessageCount() + " unread messages";
            widget.text = "You have " + messageSummary + "\\nin your message centre.";
        }
        if (contentType == WidgetContentType.ACCOUNT_PASSWORD_CHANGE)
            if (status.lastPasswordChangeDate() <= 0
                    || status.lastPasswordChangeDate() > status.currentDay() + 10)
                widget.text = "Last password change:\\n@gre@Never changed";
            else
                widget.text = "Last password change:\\n@gre@"
                        + formatAccountDate(status.lastPasswordChangeDate(), status.currentDay());
        if (contentType == WidgetContentType.ACCOUNT_MEMBERSHIP_STATUS)
            if (status.membershipDays() > 2 && !status.membersWorld())
                widget.text = "This is a non-members\\nworld. To enjoy your\\nmembers benefits we\\nrecommend you play on a\\nmembers world instead.";
            else if (status.membershipDays() > 2)
                widget.text = "\\n\\nYou have @gre@" + status.membershipDays()
                        + "@yel@ days of\\nmember credit remaining.";
            else if (status.membershipDays() > 0)
                widget.text = "You have @gre@" + status.membershipDays()
                        + "@yel@ days of\\nmember credit remaining.\\n\\n@lre@Credit low! Renew now\\n@lre@to avoid losing members.";
            else
                widget.text = "You are not a member.\\n\\nChoose to subscribe and\\nyou'll get loads of extra\\nbenefits and features.";
        if (contentType == WidgetContentType.ACCOUNT_MEMBERSHIP_HELP)
            if (status.membershipDays() > 2 && !status.membersWorld())
                widget.text = "To switch to a members-only world:\\n1) Logout and return to the world selection page.\\n2) Choose one of the members world with a gold star next to it's name.\\n\\nIf you prefer you can continue to use this world,\\nbut members only features will be unavailable here.";
            else if (status.membershipDays() > 0)
                widget.text = "To extend or cancel a subscription:\\n1) Logout and return to the frontpage of this website.\\n2)Choose the relevant option from the 'membership' section.\\n\\nNote: If you are a credit card subscriber a top-up payment will\\nautomatically be taken when 3 days credit remain.\\n(unless you cancel your subscription, which can be done at any time.)";
            else
                widget.text = "To start a subscripton:\\n1) Logout and return to the frontpage of this website.\\n2) Choose 'Start a new subscription'";
        if (contentType == WidgetContentType.ACCOUNT_RECOVERY_HELP) {
            if (status.recoveryQuestionsDate() > status.currentDay()) {
                widget.text = "To cancel this request:\\n1) Logout and return to the frontpage of this website.\\n2) Choose 'Cancel recovery questions'.";
                return;
            }
            widget.text = "To change your recovery questions:\\n1) Logout and return to the frontpage of this website.\\n2) Choose 'Set new recovery questions'.";
        }
    }

    /**
     * Formats an account-status day count using the original client calendar
     * convention.
     *
     * @param dayValue account day value to format
     * @param currentDay current account day used for the original unknown guard
     * @return formatted account date or {@code "Unknown"}
     */
    private static String formatAccountDate(int dayValue, int currentDay) {
        if (dayValue > currentDay + 10) {
            return "Unknown";
        }
        long timestampMillis = ((long) dayValue + 11745L) * 0x5265c00L;
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date(timestampMillis));
        int dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH);
        int monthIndex = calendar.get(Calendar.MONTH);
        int year = calendar.get(Calendar.YEAR);
        String[] monthNames = { "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec" };
        return dayOfMonth + "-" + monthNames[monthIndex] + "-" + year;
    }
}
