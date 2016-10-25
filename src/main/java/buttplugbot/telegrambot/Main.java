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
			final HushPlugBot bot = new HushPlugBot(connection);
			Runtime.getRuntime().addShutdownHook(new Thread(() -> bot.cleanup()));
			telegramBotsApi.registerBot(bot);
		} catch (final Exception e) {
			e.printStackTrace();
		}

	}

}
