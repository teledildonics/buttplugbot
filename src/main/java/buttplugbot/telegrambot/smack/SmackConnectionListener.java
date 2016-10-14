package buttplugbot.telegrambot.smack;

import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.XMPPConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SmackConnectionListener implements ConnectionListener {
	private final Logger logger = LoggerFactory.getLogger(getClass());

	@Override
	public void connected(XMPPConnection connection) {
		logger.info("XMPP connected: {}", connection);
	}

	@Override
	public void authenticated(XMPPConnection connection, boolean resumed) {
		logger.info("XMPP authenticated: {} {}", connection, resumed);
	}

	@Override
	public void connectionClosed() {
		logger.info("XMPP connectionClosed.");
	}

	@Override
	public void connectionClosedOnError(Exception e) {
		logger.warn("XMPP connectionClosedOnError:", e);

	}

	@Override
	public void reconnectionSuccessful() {
		logger.info("XMPP reconnectionSuccessful.");
	}

	@Override
	public void reconnectingIn(int seconds) {
		logger.info("XMPP reconnecting in {} seconds.", seconds);
	}

	@Override
	public void reconnectionFailed(Exception e) {
		logger.warn("XMPP reconnectionFailed:", e);
	}

}
