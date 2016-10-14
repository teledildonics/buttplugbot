package buttplugbot.telegrambot.smack;

import org.jivesoftware.smack.chat.Chat;
import org.jivesoftware.smack.chat.ChatMessageListener;
import org.jivesoftware.smack.packet.Message;

public class SmackChatMessageListener implements ChatMessageListener {

	@Override
	public void processMessage(Chat chat, Message message) {
		// Ignore incoming messages for now
	}

}
