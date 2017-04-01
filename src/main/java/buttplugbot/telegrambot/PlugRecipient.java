package buttplugbot.telegrambot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import buttplugbot.telegrambot.model.Pattern;
import buttplugbot.telegrambot.model.UserMessage;
import buttplugbot.telegrambot.model.UserMessage.Type;
import buttplugbot.telegrambot.smack.SmackConnection;

public class PlugRecipient {
	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final SmackConnection connection;

	private final String targetJid;

	public PlugRecipient(SmackConnection connection, String targetJid) {
		this.connection = connection;
		this.targetJid = targetJid;
	}

	public void sendPattern(Pattern pattern) {
		logger.info("Pattern: " + pattern.getUrl());
		final UserMessage userMessage = new UserMessage();
		userMessage.setToJid(targetJid);
		userMessage.setType(Type.pattern);
		userMessage.setText("Pattern");
		userMessage.setUrl(pattern.getUrl());
		userMessage.setTime(pattern.getTimer());
		userMessage.setTypeDetail("resendpattern");
		//userMessage.setPatternId(pattern.getId());
		userMessage.setAutoPlay(true);
		connection.sendMessage(userMessage);
	}

	public void sendPowerLevel(long power) {
		logger.info("Power: " + power);
		final UserMessage userMessage = new UserMessage();
		userMessage.setToJid(targetJid);
		userMessage.setType(Type.toy);
		userMessage.setText(String.valueOf(power));
		connection.sendMessage(userMessage);
	}
}
