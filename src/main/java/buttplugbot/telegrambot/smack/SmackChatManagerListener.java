package buttplugbot.telegrambot.smack;

import org.jivesoftware.smack.chat.Chat;
import org.jivesoftware.smack.chat.ChatManagerListener;

public class SmackChatManagerListener implements ChatManagerListener {

	@Override
	public void chatCreated(Chat chat, boolean created) {
		if (!created) {
			chat.addMessageListener(SmackConnection.messageListener);
		}
	}
}
