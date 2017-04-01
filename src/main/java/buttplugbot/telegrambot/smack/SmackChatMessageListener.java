package buttplugbot.telegrambot.smack;

import org.apache.commons.codec.Charsets;
import org.apache.commons.codec.binary.Base64;
import org.jivesoftware.smack.chat.Chat;
import org.jivesoftware.smack.chat.ChatMessageListener;
import org.jivesoftware.smack.packet.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SmackChatMessageListener implements ChatMessageListener {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	@Override
	public void processMessage(Chat chat, Message message) {
		String participant = chat.getParticipant();
		String body = Base64.isBase64(message.getBody())
				? new String(Base64.decodeBase64(message.getBody()), Charsets.UTF_8) : message.getBody();
		String from = message.getFrom();
		logger.info("Received message in chat with {} from {}: {}", participant, from, body);
	}

}
