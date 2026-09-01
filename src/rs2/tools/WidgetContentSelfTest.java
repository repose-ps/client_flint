package rs2.tools;

import rs2.chat.SocialManager;
import rs2.media.sprite.ImageRGB;
import rs2.text.Base37;
import rs2.ui.AppearanceEditor;
import rs2.ui.InterfaceController;
import rs2.ui.Widget;
import rs2.ui.WidgetContentController;
import rs2.ui.WidgetContentType;

/** Permanent regression checks for dynamic revision-377 widget content. */
final class WidgetContentSelfTest {

	/** Prevents instantiation. */
	private WidgetContentSelfTest() {
	}

	/**
	 * Runs dynamic widget-content ownership and behavior checks.
	 *
	 * @return completed assertion count
	 */
	static int run() {
		SelfTestSupport test = new SelfTestSupport();
		SocialManager social = new SocialManager();
		AppearanceEditor appearance = new AppearanceEditor();
		InterfaceController interfaces = new InterfaceController();
		int[] gameCycle = { 0 };
		int[] worldId = { 10 };
		int[] rights = { 0 };
		WidgetContentController.AccountStatus[] account = {
				new WidgetContentController.AccountStatus(100, 99, 0, 2, 0, 3, false, "example.test") };
		WidgetContentController controller = new WidgetContentController(social, appearance, interfaces,
				() -> gameCycle[0], () -> null, () -> worldId[0], () -> rights[0], () -> account[0]);

		testSocialContent(test, controller, social, worldId);
		testAppearanceAndReportContent(test, controller, interfaces, gameCycle, rights);
		testAccountContent(test, controller, account);
		testUninterpretedContent(test, controller);
		return test.checks();
	}

	/**
	 * Verifies friend/ignore rows and scroll extents.
	 *
	 * @param test       assertion sink
	 * @param controller dynamic content owner
	 * @param social     social-list state
	 * @param worldId    mutable current-world fixture
	 */
	private static void testSocialContent(SelfTestSupport test, WidgetContentController controller,
			SocialManager social, int[] worldId) {
		Widget widget = widget(WidgetContentType.FRIEND_NAME_FIRST);
		social.friendListStatus = SocialManager.FRIEND_LIST_LOADING;
		controller.update(widget);
		test.equal(widget.text, "Loading friend list", "friend loading text");
		test.equal(widget.buttonType, Widget.BUTTON_NONE, "friend loading button disabled");

		social.friendListStatus = SocialManager.FRIEND_LIST_READY;
		social.friendCount = 2;
		social.friendNames[0] = "Alice";
		social.friendNames[1] = "Bob";
		social.friendWorlds[0] = 10;
		social.friendWorlds[1] = 11;
		controller.update(widget);
		test.equal(widget.text, "Alice", "friend row text");
		test.equal(widget.buttonType, Widget.BUTTON_ACTION, "friend row button enabled");

		social.friendCount = 102;
		social.friendNames[101] = "Bob Alternate";
		Widget alternateFriend = widget(WidgetContentType.FRIEND_NAME_ALTERNATE_FIRST + 1);
		controller.update(alternateFriend);
		test.equal(alternateFriend.text, "Bob Alternate", "alternate friend row mapping");
		social.friendCount = 2;

		Widget world = widget(WidgetContentType.FRIEND_WORLD_FIRST);
		controller.update(world);
		test.equal(world.text, "@gre@World1", "friend current-world label");
		worldId[0] = 11;
		controller.update(world);
		test.equal(world.text, "@yel@World1", "friend remote-world label");

		Widget friendScroll = widget(WidgetContentType.FRIEND_LIST_SCROLL);
		friendScroll.height = 20;
		controller.update(friendScroll);
		test.equal(friendScroll.scrollHeight, 50, "friend list scroll height");

		social.ignoreCount = 1;
		social.ignoreEncodedNames[0] = Base37.encode("ignored user");
		Widget ignore = widget(WidgetContentType.IGNORE_NAME_FIRST);
		controller.update(ignore);
		test.equal(ignore.text, "Ignored User", "ignore row display name");
		test.equal(ignore.buttonType, Widget.BUTTON_ACTION, "ignore row button enabled");

		Widget ignoreScroll = widget(WidgetContentType.IGNORE_LIST_SCROLL);
		ignoreScroll.height = 40;
		controller.update(ignoreScroll);
		test.equal(ignoreScroll.scrollHeight, 41, "ignore scroll minimum extent");
	}

	/**
	 * Verifies appearance-button delegation and report-abuse dynamic content.
	 *
	 * @param test       assertion sink
	 * @param controller dynamic content owner
	 * @param interfaces interface state owner
	 * @param gameCycle  mutable cycle fixture
	 * @param rights     mutable player-rights fixture
	 */
	private static void testAppearanceAndReportContent(SelfTestSupport test, WidgetContentController controller,
			InterfaceController interfaces, int[] gameCycle, int[] rights) {
		ImageRGB unselected = new ImageRGB(1, 1);
		ImageRGB selected = new ImageRGB(1, 1);
		Widget male = widget(WidgetContentType.SELECT_MALE_APPEARANCE);
		male.sprite = unselected;
		male.activeSprite = selected;
		controller.update(male);
		test.check(male.sprite == selected, "male appearance widget delegated to appearance editor");

		interfaces.setReportAbuseName("Rulebreaker");
		Widget name = widget(WidgetContentType.REPORT_ABUSE_NAME);
		gameCycle[0] = 5;
		controller.update(name);
		test.equal(name.text, "Rulebreaker|", "report-abuse cursor visible");
		gameCycle[0] = 15;
		controller.update(name);
		test.equal(name.text, "Rulebreaker ", "report-abuse cursor hidden");

		Widget mute = widget(WidgetContentType.REPORT_ABUSE_MUTE);
		rights[0] = 0;
		controller.update(mute);
		test.equal(mute.text, "", "report-abuse moderator option hidden");
		rights[0] = 1;
		interfaces.setReportAbuseMutePlayer(false);
		controller.update(mute);
		test.equal(mute.text, "Moderator option: Mute player for 48 hours: <OFF>", "report-abuse mute off text");
		test.equal(mute.color, 0xffffff, "report-abuse mute off color");
		interfaces.setReportAbuseMutePlayer(true);
		controller.update(mute);
		test.equal(mute.text, "Moderator option: Mute player for 48 hours: <ON>", "report-abuse mute on text");
		test.equal(mute.color, 0xff0000, "report-abuse mute on color");

	}

	/**
	 * Verifies account-panel strings from the cohesive account snapshot.
	 *
	 * @param test       assertion sink
	 * @param controller dynamic content owner
	 * @param account    mutable account snapshot fixture
	 */
	private static void testAccountContent(SelfTestSupport test, WidgetContentController controller,
			WidgetContentController.AccountStatus[] account) {
		Widget lastLogin = widget(WidgetContentType.ACCOUNT_LAST_LOGIN);
		controller.update(lastLogin);
		test.equal(lastLogin.text, "You last logged in @red@yesterday@bla@ from: @red@example.test",
				"account last-login text");

		Widget recovery = widget(WidgetContentType.ACCOUNT_RECOVERY_QUESTIONS);
		controller.update(recovery);
		test.check(recovery.text.startsWith("\\nYou have not yet set any recovery questions."),
				"account recovery unset text");

		Widget unread = widget(WidgetContentType.ACCOUNT_UNREAD_MESSAGES);
		controller.update(unread);
		test.equal(unread.text, "You have @gre@2 unread messages\\nin your message centre.",
				"account unread-message text");

		Widget password = widget(WidgetContentType.ACCOUNT_PASSWORD_CHANGE);
		controller.update(password);
		test.equal(password.text, "Last password change:\\n@gre@Never changed", "account password-never text");

		Widget membership = widget(WidgetContentType.ACCOUNT_MEMBERSHIP_STATUS);
		controller.update(membership);
		test.equal(membership.text,
				"This is a non-members\\nworld. To enjoy your\\nmembers benefits we\\nrecommend you play on a\\nmembers world instead.",
				"account non-members-world warning");

		Widget membershipHelp = widget(WidgetContentType.ACCOUNT_MEMBERSHIP_HELP);
		controller.update(membershipHelp);
		test.check(membershipHelp.text.startsWith("To switch to a members-only world:"),
				"account membership world-switch help");

		Widget recoveryHelp = widget(WidgetContentType.ACCOUNT_RECOVERY_HELP);
		controller.update(recoveryHelp);
		test.check(recoveryHelp.text.startsWith("To change your recovery questions:"), "account recovery-change help");

		account[0] = new WidgetContentController.AccountStatus(100, 100, 111, 1, 50, 1, true, "host-two");
		controller.update(lastLogin);
		test.equal(lastLogin.text, "You last logged in @red@earlier today@bla@ from: @red@host-two",
				"account snapshot refresh");
		controller.update(recovery);
		test.check(recovery.text.contains("The requested change will occur\\non: @lre@Unknown"),
				"account future recovery unknown-date guard");
		controller.update(unread);
		test.equal(unread.text, "You have @gre@1 unread message\\nin your message centre.",
				"account singular unread-message text");
		controller.update(membership);
		test.check(membership.text.startsWith("You have @gre@1@yel@ days of"), "account low membership warning");
	}

	/**
	 * Verifies unrecognized/no-content widgets are left unchanged.
	 *
	 * @param test       assertion sink
	 * @param controller dynamic content owner
	 */
	private static void testUninterpretedContent(SelfTestSupport test, WidgetContentController controller) {
		Widget widget = widget(WidgetContentType.NONE);
		widget.text = "unchanged";
		widget.color = 1234;
		controller.update(widget);
		test.equal(widget.text, "unchanged", "content type zero text unchanged");
		test.equal(widget.color, 1234, "content type zero color unchanged");
	}

	/**
	 * Creates a minimal widget fixture for one content type.
	 *
	 * @param contentType widget content-type identifier
	 * @return new widget fixture
	 */
	private static Widget widget(int contentType) {
		Widget widget = new Widget();
		widget.contentType = contentType;
		return widget;
	}
}
