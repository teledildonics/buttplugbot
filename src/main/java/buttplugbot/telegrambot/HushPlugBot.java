package buttplugbot.telegrambot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.api.methods.AnswerInlineQuery;
import org.telegram.telegrambots.api.methods.BotApiMethod;
import org.telegram.telegrambots.api.methods.send.SendMessage;
import org.telegram.telegrambots.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.api.objects.CallbackQuery;
import org.telegram.telegrambots.api.objects.Chat;
import org.telegram.telegrambots.api.objects.Message;
import org.telegram.telegrambots.api.objects.Update;
import org.telegram.telegrambots.api.objects.User;
import org.telegram.telegrambots.api.objects.inlinequery.ChosenInlineQuery;
import org.telegram.telegrambots.api.objects.inlinequery.InlineQuery;
import org.telegram.telegrambots.api.objects.inlinequery.inputmessagecontent.InputTextMessageContent;
import org.telegram.telegrambots.api.objects.inlinequery.result.InlineQueryResultArticle;
import org.telegram.telegrambots.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.bots.AbsSender;
import org.telegram.telegrambots.bots.TelegramLongPollingCommandBot;
import org.telegram.telegrambots.bots.commands.BotCommand;
import org.telegram.telegrambots.exceptions.TelegramApiException;
import org.telegram.telegrambots.exceptions.TelegramApiRequestException;
import org.telegram.telegrambots.updateshandlers.SentCallback;

import buttplugbot.telegrambot.dao.PatternDao;
import buttplugbot.telegrambot.dao.PlugDao;
import buttplugbot.telegrambot.model.Plug;
import buttplugbot.telegrambot.model.StatusUpdate;
import buttplugbot.telegrambot.smack.SmackConnection;
import buttplugbot.telegrambot.util.EmailUtil;

public class HushPlugBot extends TelegramLongPollingCommandBot {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final SmackConnection connection;

	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

	private final PatternDao patternDao = new PatternDao();

	private volatile int updateStatus = 0;

	private final PlugDao plugDao = new PlugDao();

	private final Set<Long> waitingForEmail = new ConcurrentSkipListSet<>();

	private final Map<String, PlugControl> plugs = new ConcurrentHashMap<>();

	public HushPlugBot(SmackConnection connection) {
		this.connection = connection;
		register(new RegisterCommand());
		register(new StartCommmand());
		register(new ShareCommand());
		register(new PlugCommand());
		register(new UnregisterCommand());
		register(new TraceCommand());
		register(new TemporaryCommand());
		executor.scheduleAtFixedRate(() -> {
			for (final PlugControl plugControl : plugs.values()) {
				plugControl.sendUpdate();
			}
			updateStatus++;
			if (updateStatus % 300 == 0) {
				connection.updateStatus();
				cleanupTemporary();
			}
		}, 100, 100, TimeUnit.MILLISECONDS);

	}

	private void cleanupTemporary() {
		final Map<String, Plug> deleted = plugDao.cleanTemporary();
		for (final Entry<String, Plug> entry : deleted.entrySet()) {
			final PlugControl plugControl = plugs.get(entry.getValue().getId());
			if (plugControl != null) {
				plugControl.getStatusMessageUpdater().removeByPlugId(entry.getKey());
			}
		}
	}

	@Override
	public String getBotUsername() {
		return Config.botName;
	}

	@Override
	public void processNonCommandUpdate(Update update) {
		logger.info("Received non-command: {}", update);
		if (update.hasMessage()) {
			final Message message = update.getMessage();
			logger.info("Telegram-Message: {}", update);
			if (message.getChat().isUserChat() && waitingForEmail.contains(message.getChatId())) {
				waitingForEmail.remove(message.getChatId());
				registerEmail(this, message);
			}

		}
		if (update.hasCallbackQuery()) {
			final CallbackQuery query = update.getCallbackQuery();
			handleCallbackQuery(query);
		}
		if (update.hasInlineQuery()) {
			final InlineQuery query = update.getInlineQuery();
			handleInlineQuery(query);
		}
		if (update.hasChosenInlineQuery()) {
			final ChosenInlineQuery query = update.getChosenInlineQuery();
			handleChosenInlineQuery(query);
		}
	}

	private void handleChosenInlineQuery(ChosenInlineQuery query) {
		final Plug plug = plugDao.getPlugByUserId(query.getFrom().getId());
		if (plug != null && !StringUtils.isEmpty(query.getInlineMessageId())) {
			PlugControl plugControl = plugs.get(plug.getId());
			if (plugControl == null) {
				plugControl = new PlugControl(plug, connection, patternDao, plugDao, new TraceSender());
				plugs.put(plug.getId(), plugControl);
			}
			final String id = StringUtils.substringAfter(query.getResultId(), "plug_");
			plugControl.getStatusMessageUpdater()
					.addStatusUpdateMessage(new StatusUpdateMessageSender(query.getInlineMessageId(), id), true);
		}
	}

	private void handleInlineQuery(InlineQuery query) {
		final AnswerInlineQuery answer = new AnswerInlineQuery();
		answer.setPersonal(true);
		answer.setInlineQueryId(query.getId());
		final Plug plug = plugDao.getPlugByUserId(query.getFrom().getId());
		if (plug == null) {
			answer.setSwitchPmText("Register your plug");
			answer.setResults(Collections.emptyList());
			answer.setCacheTime(10);
		} else {
			int hours = -1;
			if (query.hasQuery() && query.getQuery().length() > 0) {
				try {
					hours = Integer.parseInt(query.getQuery());
				} catch (final NumberFormatException e) {
					logger.info("Unable to parse number: {}", query.getQuery(), e);
				}
			}
			final String id;
			if (hours > 0) {
				id = plugDao.addTemporary(plug, hours);
			} else {
				id = plug.getId();
			}
			final InlineKeyboardMarkup replyMarkup = createKeyboard(id);
			final InlineQueryResultArticle article = new InlineQueryResultArticle();
			article.setTitle("Share your plug " + (hours > 0 ? "for " + hours + " hours" : "indefinitely"));
			article.setReplyMarkup(replyMarkup);
			article.setId("plug_" + id);
			final InputTextMessageContent textMessageContent = new InputTextMessageContent();
			textMessageContent.setMessageText("*Buttplug*\nConnecting...");
			article.setInputMessageContent(textMessageContent);
			answer.setResults(Collections.singletonList(article));
		}
		try {
			answerInlineQuery(answer);
		} catch (final TelegramApiException e) {
			logger.warn("Failed to send message: {}", e, e);
		}
	}

	private void handleCallbackQuery(final CallbackQuery query) {
		final String data = query.getData();
		final String plugId = StringUtils.substringBefore(data, "|");
		final String command = StringUtils.substringAfter(data, "|");
		final String answerText;
		if (StringUtils.isEmpty(plugId) || StringUtils.isEmpty(command)) {
			answerText = "Unknown command.";
		} else {
			final Plug plug = plugDao.getPlugById(plugId);
			if (plug == null) {
				answerText = "Unknown plug.";
			} else {
				PlugControl plugControl = plugs.get(plugId);
				if (plugControl == null) {
					plugControl = new PlugControl(plug, connection, patternDao, plugDao, new TraceSender());
					plugs.put(plug.getId(), plugControl);
				}
				if (!command.contains("trace")) {
					if (query.getMessage() != null) {
						plugControl.getStatusMessageUpdater().addStatusUpdateMessage(new StatusUpdateMessageSender(
								query.getMessage().getChatId(), query.getMessage().getMessageId(), plugId), false);
					}
					if (query.getInlineMessageId() != null) {
						plugControl.getStatusMessageUpdater().addStatusUpdateMessage(
								new StatusUpdateMessageSender(query.getInlineMessageId(), plugId), false);
					}
				}
				answerText = plugControl.processMessage(query.getFrom().getId(), command);
				traceCommand(query, command, plug);
			}
		}
		final AnswerCallbackQuery answer = new AnswerCallbackQuery();
		answer.setCallbackQueryId(query.getId());
		if (answerText != null) {
			answer.setText(answerText);
		}
		try {
			answerCallbackQuery(answer);
		} catch (final TelegramApiException e) {
			logger.warn("Failed to send message: {}", e, e);
		}
	}

	private void traceCommand(final CallbackQuery query, final String command, final Plug plug) {
		final String name = createName(query.getFrom());
		if ("sine".equals(command) || "buzz".equals(command)) {
			plug.setLastInteractedUser(name);
		}
		if (command.contains("trace") && query.getMessage().getChat().isUserChat()
				&& query.getFrom().getId() == plug.getUserId()) {
			plug.setUserChatId(query.getMessage().getChatId());
			plugDao.storeDB();
		}
	}

	@Override
	public String getBotToken() {
		return Config.botToken;
	}

	public class RegisterCommand extends BotCommand {

		public RegisterCommand() {
			super("register", "Register an email to connect over the Lovense network.");
		}

		@Override
		public void execute(AbsSender absSender, User user, Chat chat, String[] arguments) {
			logger.info("Received /register command from user {} in chat {} with arguments {}",
					new Object[] { user, chat, arguments });
			startRegistering(absSender, chat);
		}
	}

	private void startRegistering(AbsSender absSender, Chat chat) {
		if (!chat.isUserChat()) {
			sendMessage(absSender, chat, "Please chat with me directly.");
			return;
		}
		waitingForEmail.add(chat.getId());
		sendMessage(absSender, chat, "Please send me the email address that you registered with Lovense.");
	}

	private void registerEmail(AbsSender absSender, Message message) {
		final String email = message.getText();
		final String checkedEmail = EmailUtil.check(email);
		if (checkedEmail == null) {
			sendMessage(absSender, message.getChat(), "Unable to send friend request to " + email);
			return;
		}
		final String jid = connection.connectToUser(checkedEmail);
		if (jid == null) {
			sendMessage(absSender, message.getChat(), "Unable to send friend request to " + checkedEmail);
			return;
		}
		Plug plug = plugDao.getPlugByUserId(message.getFrom().getId());
		if (plug != null && plug.getTargetJid().equals(jid)) {
			connection.removeUser(plug.getTargetJid());
			plugDao.remove(plug);
			plugs.remove(plug.getId());
		}
		plug = plugDao.getPlugByEmail(jid);
		if (plug == null) {
			plug = new Plug(plugDao.createId(), jid, message.getFrom().getId());
			plug.setName(createName(message.getFrom()));
			plug.setUserChatId(message.getChatId());
			plugDao.add(plug);
		}
		sendMessage(absSender, message.getChat(),
				"You have been registered now.\n" + "You can type /share in any group chat, that contains this bot. "
						+ "You can limit the usage time by typing for example /share 2 to limit it to 2 hours.\n\n"
						+ "I created an unlimited id for you: /plug " + plug.getId() + "\n"
						+ "If you share this id with someone, they can view and share your controls, until you use the /unregister command.\n"
						+ "You can create temporary ids with the /temporary command.");
	}

	public class UnregisterCommand extends BotCommand {

		public UnregisterCommand() {
			super("unregister", "Unregister from bot.");
		}

		@Override
		public void execute(AbsSender absSender, User user, Chat chat, String[] arguments) {
			logger.info("Received /unregister command from user {} in chat {} with arguments {}",
					new Object[] { user, chat, arguments });
			final Plug plug = plugDao.getPlugByUserId(user.getId());
			if (plug != null) {
				connection.removeUser(plug.getTargetJid());
				plugDao.remove(plug);
				plugs.remove(plug.getId());
			}
		}
	}

	private String createName(User user) {
		if (!StringUtils.isEmpty(user.getUserName())) {
			return "@" + user.getUserName();
		}
		return StringUtils.join(new String[] { user.getFirstName(), user.getLastName() }, " ");
	}

	public class ShareCommand extends BotCommand {
		public ShareCommand() {
			super("share", "Share the control of your plug with the current chat.");
		}

		@Override
		public void execute(AbsSender absSender, User user, Chat chat, String[] arguments) {
			logger.info("Received /share command from user {} in chat {} with arguments {}",
					new Object[] { user, chat, arguments });
			int hours = -1;
			if (arguments.length > 0) {
				try {
					hours = Integer.parseInt(arguments[0]);
				} catch (final NumberFormatException e) {
					logger.info("Unable to parse number: {}", arguments[0], e);
				}
			}
			final Plug plug = plugDao.getPlugByUserId(user.getId());
			if (hours > 0) {
				final String newId = plugDao.addTemporary(plug, hours);
				showButtonsForPlug(absSender, chat, plug, newId);
			} else {
				showButtonsForPlug(absSender, chat, plug, plug.getId());
			}
		}
	}

	public class TemporaryCommand extends BotCommand {
		public TemporaryCommand() {
			super("temporary", "Create an id that is only available for some hours.");
		}

		@Override
		public void execute(AbsSender absSender, User user, Chat chat, String[] arguments) {
			logger.info("Received /temporary command from user {} in chat {} with arguments {}",
					new Object[] { user, chat, arguments });
			int hours = -1;
			if (arguments.length > 0) {
				try {
					hours = Integer.parseInt(arguments[0]);
				} catch (final NumberFormatException e) {
					logger.info("Unable to parse number: {}", arguments[0], e);
				}
			}
			final Plug plug = plugDao.getPlugByUserId(user.getId());
			if (hours > 0) {
				final String newId = plugDao.addTemporary(plug, hours);
				sendMessage(absSender, chat,
						"Your temporary id is: /plug " + newId + "\nIt's valid for " + hours + " hours.");
			} else {
				sendMessage(absSender, chat,
						"Please type \"/temporary hours\", e.g. \"/temporary 2\" for an id that is valid for 2 hours.");
			}
		}
	}

	public class PlugCommand extends BotCommand {
		public PlugCommand() {
			super("plug", "Show the buttons for a plug in the current chat.");
		}

		@Override
		public void execute(AbsSender absSender, User user, Chat chat, String[] arguments) {
			logger.info("Received /plug command from user {} in chat {} with arguments {}",
					new Object[] { user, chat, arguments });

			if (arguments.length != 1 || arguments[0] == null) {
				sendMessage(absSender, chat, "Please type /plug id");
				return;
			}
			final Plug plug = plugDao.getPlugById(arguments[0]);
			showButtonsForPlug(absSender, chat, plug, arguments[0]);
		}
	}

	public class TraceCommand extends BotCommand {
		public TraceCommand() {
			super("trace", "Select, if you want to be notified, if someone presses a button.");
		}

		@Override
		public void execute(AbsSender absSender, User user, Chat chat, String[] arguments) {
			logger.info("Received /trace command from user {} in chat {} with arguments {}",
					new Object[] { user, chat, arguments });

			if (!chat.isUserChat()) {
				sendMessage(absSender, chat, "Please chat with me directly.");
				return;
			}
			final Plug plug = plugDao.getPlugByUserId(user.getId());
			if (plug == null) {
				startRegistering(absSender, chat);
				return;
			}
			final SendMessage answer = new SendMessage();
			answer.disableNotification();
			final InlineKeyboardMarkup replyMarkup = new InlineKeyboardMarkup();
			final List<List<InlineKeyboardButton>> rows = new ArrayList<>();
			rows.add(new ArrayList<>());
			rows.get(0).add(new InlineKeyboardButton().setText("No Trace").setCallbackData(plug.getId() + "|notrace"));
			rows.get(0).add(
					new InlineKeyboardButton().setText("Single trace").setCallbackData(plug.getId() + "|singletrace"));
			rows.get(0)
					.add(new InlineKeyboardButton().setText("Full trace").setCallbackData(plug.getId() + "|fulltrace"));
			replyMarkup.setKeyboard(rows);
			answer.setReplyMarkup(replyMarkup);
			answer.enableMarkdown(true);
			answer.setChatId(chat.getId().toString());
			answer.setText("Do you want to be notified, if someone presses a button?\n"
					+ "No trace (default) won't show you any informations about who pressed the buttons.\n"
					+ "Single trace means, that the last person, who pressed a button, will be shown in the output.\n"
					+ "Full trace will send you a message every time someone activates the plug.\n"
					+ "Your current setting is: " + plug.getTraceAsString());
			try {
				logger.info("Sending message: {}", answer);
				absSender.sendMessage(answer);
			} catch (final TelegramApiException e) {
				logger.warn("Failed to send message: {}", e, e);
			}
		}
	}

	private void showButtonsForPlug(AbsSender absSender, Chat chat, Plug plug, String id) {
		if (plug == null) {
			sendMessage(absSender, chat, "You've not registered your plug yet, type /register in direct chat.");
			return;
		}

		PlugControl plugControl = plugs.get(plug.getId());
		if (plugControl == null) {
			plugControl = new PlugControl(plug, connection, patternDao, plugDao, new TraceSender());
			plugs.put(plug.getId(), plugControl);
		}
		final Message message = sendButtons(absSender, chat, plug, id);
		if (message != null) {
			plugControl.getStatusMessageUpdater().addStatusUpdateMessage(
					new StatusUpdateMessageSender(message.getChatId(), message.getMessageId(), id), true);
		}
	}

	public class StartCommmand extends BotCommand {

		public StartCommmand() {
			super("start", "Register an email to connect over the Lovense network.");
		}

		@Override
		public void execute(AbsSender absSender, User user, Chat chat, String[] arguments) {
			logger.info("Received /start command from user {} in chat {} with arguments {}",
					new Object[] { user, chat, arguments });
			final Plug plug = plugDao.getPlugByUserId(user.getId());
			if (plug == null) {
				startRegistering(absSender, chat);
			} else {
				showButtonsForPlug(absSender, chat, plug, plug.getId());
			}
		}

	}

	private Message sendButtons(AbsSender absSender, Chat chat, final Plug plug, String id) {
		final SendMessage answer = new SendMessage();
		answer.disableNotification();
		final InlineKeyboardMarkup replyMarkup = createKeyboard(id);
		answer.setReplyMarkup(replyMarkup);
		answer.enableMarkdown(true);
		answer.setChatId(chat.getId().toString());
		answer.setText("*Buttplug*\nConnecting...");
		try {
			logger.info("Sending message: {}", answer);
			return absSender.sendMessage(answer);
		} catch (final TelegramApiException e) {
			logger.warn("Failed to send message: {}", e, e);
			return null;
		}
	}

	private InlineKeyboardMarkup createKeyboard(String id) {
		final InlineKeyboardMarkup replyMarkup = new InlineKeyboardMarkup();
		final List<List<InlineKeyboardButton>> rows = new ArrayList<>();
		rows.add(new ArrayList<>());
		rows.get(0).add(new InlineKeyboardButton().setText("💓 2s").setCallbackData(id + "|buzz"));
		rows.get(0).add(
				new InlineKeyboardButton().setText("💓 " + Config.vibrationLength + "s").setCallbackData(id + "|sine"));
		rows.add(new ArrayList<>());
		rows.get(1).add(new InlineKeyboardButton().setText("∿ slower").setCallbackData(id + "|interval+"));
		rows.get(1).add(new InlineKeyboardButton().setText("∿ faster").setCallbackData(id + "|interval-"));
		rows.add(new ArrayList<>());
		rows.get(2).add(new InlineKeyboardButton().setText("- 10 %").setCallbackData(id + "|amplitude-"));
		rows.get(2).add(new InlineKeyboardButton().setText("+ 10 %").setCallbackData(id + "|amplitude+"));
		replyMarkup.setKeyboard(rows);
		return replyMarkup;
	}

	private void sendMessage(AbsSender absSender, Chat chat, String message) {
		final SendMessage answer = new SendMessage();
		answer.setChatId(chat.getId().toString());
		answer.setText(message);
		try {
			logger.info("Sending message: {}", answer);
			absSender.sendMessage(answer);
		} catch (final TelegramApiException e) {
			logger.warn("Failed to send message: {}", e, e);
		}
	}

	public class StatusUpdateMessageSender implements Comparable<StatusUpdateMessageSender> {

		private final Long chatId;

		private final Integer messageId;

		private final String inlineMessageId;

		private final String plugId;

		public StatusUpdateMessageSender(Long chatId, Integer messageId, String plugId) {
			this.chatId = chatId;
			this.messageId = messageId;
			this.inlineMessageId = null;
			this.plugId = plugId;
		}

		public StatusUpdateMessageSender(String inlineMessageId, String plugId) {
			this.chatId = null;
			this.messageId = null;
			this.inlineMessageId = inlineMessageId;
			this.plugId = plugId;
		}

		public void update(StatusUpdate status, Runnable remove) {
			final EditMessageText edit = new EditMessageText();
			if (inlineMessageId == null) {
				edit.setChatId(Long.toString(chatId));
				edit.setMessageId(messageId);
			} else {
				edit.setInlineMessageId(inlineMessageId);
			}
			edit.setText(status.getMessage());
			if (status.isKeyboard()) {
				edit.setReplyMarkup(createKeyboard(plugId));
			}
			edit.enableMarkdown(true);
			try {
				editMessageTextAsync(edit, new SentCallback<Message>() {

					@Override
					public void onResult(BotApiMethod<Message> method, JSONObject jsonObject) {

					}

					@Override
					public void onError(BotApiMethod<Message> method, JSONObject jsonObject) {
						logger.warn(new TelegramApiRequestException("Failed to send message", jsonObject).toString());
						remove.run();
					}

					@Override
					public void onException(BotApiMethod<Message> method, Exception exception) {
						logger.warn("Failed to send message", exception);
						remove.run();
					}
				});
			} catch (final TelegramApiException e) {
				logger.warn("Failed to send message", e);
			}
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + getOuterType().hashCode();
			result = prime * result + (chatId == null ? 0 : chatId.hashCode());
			result = prime * result + (inlineMessageId == null ? 0 : inlineMessageId.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (getClass() != obj.getClass()) {
				return false;
			}
			final StatusUpdateMessageSender other = (StatusUpdateMessageSender) obj;
			if (!getOuterType().equals(other.getOuterType())) {
				return false;
			}
			if (chatId == null) {
				if (other.chatId != null) {
					return false;
				}
			} else if (!chatId.equals(other.chatId)) {
				return false;
			}
			if (inlineMessageId == null) {
				if (other.inlineMessageId != null) {
					return false;
				}
			} else if (!inlineMessageId.equals(other.inlineMessageId)) {
				return false;
			}
			return true;
		}

		@Override
		public int compareTo(StatusUpdateMessageSender o) {
			final String a = chatId == null ? "i_" + inlineMessageId : "c_" + chatId;
			final String b = o.chatId == null ? "i_" + o.inlineMessageId : "c_" + o.chatId;
			return a.compareTo(b);
		}

		private HushPlugBot getOuterType() {
			return HushPlugBot.this;
		}

		public String getPlugId() {
			return plugId;
		}
	}

	public class TraceSender {
		public void sendTrace(Long userChatId, String traceMessage) {
			try {
				final SendMessage message = new SendMessage();
				message.setChatId(String.valueOf(userChatId));
				message.setText(traceMessage);
				message.disableNotification();
				sendMessageAsync(message, new SentCallback<Message>() {

					@Override
					public void onResult(BotApiMethod<Message> method, JSONObject jsonObject) {

					}

					@Override
					public void onError(BotApiMethod<Message> method, JSONObject jsonObject) {
						logger.warn("Failed to send message: "
								+ new TelegramApiRequestException("", jsonObject).getApiResponse());
					}

					@Override
					public void onException(BotApiMethod<Message> method, Exception exception) {
						logger.warn("Failed to send message", exception);
					}
				});
			} catch (final TelegramApiException e) {
				logger.warn("Failed to send message: {}", e, e);
			}
		}
	}
}
