package buttplugbot.telegrambot;

public interface Config {
	/**
	 * Telegram bot name
	 */
	static final String botName = "buttplug_bot";

	/**
	 * Telegram bot token
	 */
	static final String botToken = "";

	/**
	 * XMPP service name
	 */
	static final String serviceName = "im.lovense.com";

	/**
	 * XMPP host name
	 */
	static final String host = "im.lovense.com";

	/**
	 * API host for uploading patterns
	 */
	static final String apiUrl = "http://" + host;

	/**
	 * XMPP port
	 */
	static final int port = 9529;

	/**
	 * XMPP Username/Email of the bot account at lovense
	 */
	static final String email = "";

	/**
	 * XMPP Password of the bot account at lovense
	 */
	static final String password = "";

	/**
	 * XMPP Resource name
	 */
	static final String resource = "wearable";

	/**
	 * Length of the vibration pattern in seconds.
	 */
	static final int vibrationLength = 20;

	/**
	 * Length of the buzz vibration in milliseconds.
	 */
	static final long buzzLength = 2000;

	/**
	 * Timeout until a user is marked as inactive in the chat.
	 */
	static final long totalActivityTimeout = 10 * 60 * 1000;

	/**
	 * Check and create patterns, if they are missing.
	 */
	static final boolean createPatterns = false;
}
