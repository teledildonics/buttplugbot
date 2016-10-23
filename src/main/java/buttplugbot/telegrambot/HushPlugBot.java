package buttplugbot.telegrambot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import org.telegram.telegrambots.Constants;
import org.telegram.telegrambots.TelegramApiException;
import org.telegram.telegrambots.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.api.methods.BotApiMethod;
import org.telegram.telegrambots.api.methods.send.SendMessage;
import org.telegram.telegrambots.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.api.objects.CallbackQuery;
import org.telegram.telegrambots.api.objects.Chat;
import org.telegram.telegrambots.api.objects.Message;
import org.telegram.telegrambots.api.objects.Update;
import org.telegram.telegrambots.api.objects.User;
import org.telegram.telegrambots.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.bots.AbsSender;
import org.telegram.telegrambots.bots.TelegramLongPollingCommandBot;
import org.telegram.telegrambots.bots.commands.BotCommand;
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
		executor.scheduleAtFixedRate(() -> {
			for (final PlugControl plugControl : plugs.values()) {
				plugControl.sendUpdate();
			}
			updateStatus++;
			if (updateStatus % 300 == 0) {
				connection.updateStatus();
			}
		}, 100, 100, TimeUnit.MILLISECONDS);

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
	}

	private void handleCallbackQuery(final CallbackQuery query) {
		String data = query.getData();
		String plugId = StringUtils.substringBefore(data, "|");
		String command = StringUtils.substringAfter(data, "|");
		final String answerText;
		if (StringUtils.isEmpty(plugId) || StringUtils.isEmpty(command)) {
			answerText = "Unknown command.";
		} else {
			Plug plug = plugDao.getPlugById(plugId);
			if (plug == null) {
				answerText = "Unknown plug.";
			} else {
				PlugControl plugControl = plugs.get(plugId);
				if (plugControl == null) {
					plugControl = new PlugControl(plug, connection, patternDao);
					plugs.put(plug.getId(), plugControl);
				}
				plugControl.getStatusMessageUpdater().addStatusUpdateMessage(
						new StatusUpdateMessage(query.getMessage().getChatId(), query.getMessage().getMessageId()),
						false);
				answerText = plugControl.processMessage(query.getFrom().getId(), command);
			}
		}
		AnswerCallbackQuery answer = new AnswerCallbackQuery();
		answer.setCallbackQueryId(query.getId());
		if (answerText != null) {
			answer.setText(answerText);
		}
		try {
			answerCallbackQuery(answer);
		} catch (TelegramApiException e) {
			logger.warn("Failed to send message: " + e.getApiResponse(), e);
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
		String email = message.getText();
		String checkedEmail = EmailUtil.check(email);
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
			plug = new Plug(jid, message.getFrom().getId());
			plug.setName(createName(message.getFrom()));
			plugDao.add(plug);
		}
		sendMessage(absSender, message.getChat(),
				"You have been registered, you can type /share in any chat now or anyone can type /plug " + plug.getId()
						+ " to view your controls.");
	}

	public class UnregisterCommand extends BotCommand {

		public UnregisterCommand() {
			super("unregister", "Unregister from bot.");
		}

		@Override
		public void execute(AbsSender absSender, User user, Chat chat, String[] arguments) {
			logger.info("Received /unregister command from user {} in chat {} with arguments {}",
					new Object[] { user, chat, arguments });
			Plug plug = plugDao.getPlugByUserId(user.getId());
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
			Plug plug = plugDao.getPlugByUserId(user.getId());
			showButtonsForPlug(absSender, chat, plug);
		}
	}

	public class PlugCommand extends BotCommand {
		public PlugCommand() {
			super("plug", "Show the buttons for a plug in the current chat.");
		}

		@Override
		public void execute(AbsSender absSender, User user, Chat chat, String[] arguments) {
			logger.info("Received /share command from user {} in chat {} with arguments {}",
					new Object[] { user, chat, arguments });

			if (arguments.length != 1 || arguments[0] == null) {
				sendMessage(absSender, chat, "Please type /plug id");
				return;
			}
			Plug plug = plugDao.getPlugById(arguments[0]);
			showButtonsForPlug(absSender, chat, plug);

		}
	}

	private void showButtonsForPlug(AbsSender absSender, Chat chat, Plug plug) {
		if (plug == null) {
			sendMessage(absSender, chat, "You've not registered your plug yet, type /register in direct chat.");
			return;
		}

		PlugControl plugControl = plugs.get(plug.getId());
		if (plugControl == null) {
			plugControl = new PlugControl(plug, connection, patternDao);
			plugs.put(plug.getId(), plugControl);
		}
		Message message = sendButtons(absSender, chat, plug);
		if (message != null) {
			plugControl.getStatusMessageUpdater()
					.addStatusUpdateMessage(new StatusUpdateMessage(message.getChatId(), message.getMessageId()), true);
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
			Plug plug = plugDao.getPlugByUserId(user.getId());
			if (plug == null) {
				startRegistering(absSender, chat);
			} else {
				showButtonsForPlug(absSender, chat, plug);
			}
		}

	}

	private Message sendButtons(AbsSender absSender, Chat chat, final Plug plug) {
		final SendMessage answer = new SendMessage();
		answer.disableNotification();
		final InlineKeyboardMarkup replyMarkup = createKeyboard(plug.getId());
		answer.setReplyMarkup(replyMarkup);
		answer.enableMarkdown(true);
		answer.setChatId(chat.getId().toString());
		answer.setText("*Buttplug*\nConnecting...");
		try {
			logger.info("Sending message: {}", answer);
			return absSender.sendMessage(answer);
		} catch (final TelegramApiException e) {
			logger.warn("Failed to send message: " + e.getApiResponse(), e);
			return null;
		}
	}

	private InlineKeyboardMarkup createKeyboard(String id) {
		final InlineKeyboardMarkup replyMarkup = new InlineKeyboardMarkup();
		List<List<InlineKeyboardButton>> rows = new ArrayList<>();
		rows.add(new ArrayList<>());
		rows.get(0).add(new InlineKeyboardButton().setText("💓 2s").setCallbackData(id + "|buzz"));
		rows.get(0).add(
				new InlineKeyboardButton().setText("💓 " + Config.vibrationLength + "s").setCallbackData(id + "|sine"));
		rows.add(new ArrayList<>());
		rows.get(1).add(new InlineKeyboardButton().setText("∿ faster").setCallbackData(id + "|interval-"));
		rows.get(1).add(new InlineKeyboardButton().setText("∿ slower").setCallbackData(id + "|interval+"));
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
			logger.warn("Failed to send message: " + e.getApiResponse(), e);
		}
	}

	public class StatusUpdateMessage implements Comparable<StatusUpdateMessage> {

		private Long chatId;

		private Integer messageId;

		public StatusUpdateMessage(Long chatId, Integer messageId) {
			this.chatId = chatId;
			this.messageId = messageId;
		}

		public void update(StatusUpdate status, Runnable remove) {
			EditMessageText edit = new EditMessageText();
			edit.setChatId(Long.toString(chatId));
			edit.setMessageId(messageId);
			edit.setText(status.getMessage());
			edit.setReplyMarkup(createKeyboard(status.getId()));
			edit.enableMarkdown(true);
			try {
				editMessageTextAsync(edit, new SentCallback<Message>() {

					@Override
					public void onResult(BotApiMethod<Message> method, JSONObject jsonObject) {

					}

					@Override
					public void onError(BotApiMethod<Message> method, JSONObject jsonObject) {
						logger.warn("Failed to send message: " + jsonObject.getString(Constants.ERRORDESCRIPTIONFIELD));
						remove.run();
					}

					@Override
					public void onException(BotApiMethod<Message> method, Exception exception) {
						logger.warn("Failed to send message", exception);
						remove.run();
					}
				});
			} catch (TelegramApiException e) {
				logger.warn("Failed to send message", e);
			}
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((chatId == null) ? 0 : chatId.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			StatusUpdateMessage other = (StatusUpdateMessage) obj;
			if (chatId == null) {
				if (other.chatId != null)
					return false;
			} else if (!chatId.equals(other.chatId))
				return false;
			return true;
		}

		@Override
		public int compareTo(StatusUpdateMessage o) {
			return Long.compare(chatId, o.chatId);
		}
	}
}
