package buttplugbot.telegrambot;

import org.telegram.telegrambots.TelegramBotsApi;

import buttplugbot.telegrambot.smack.SmackConnection;

public class Main {

	public static void main(String[] args) {
		final SmackConnection connection = new SmackConnection();
		try {
			connection.connect();
			connection.authenticate();
			final TelegramBotsApi telegramBotsApi = new TelegramBotsApi();
			telegramBotsApi.registerBot(new HushPlugBot(connection));
		} catch (final Exception e) {
			e.printStackTrace();
		}
	}

}
