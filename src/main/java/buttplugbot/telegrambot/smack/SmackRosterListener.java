package buttplugbot.telegrambot.smack;

import java.util.Collection;

import org.jivesoftware.smack.packet.DefaultExtensionElement;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.StreamOpen;
import org.jivesoftware.smack.roster.RosterListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SmackRosterListener implements RosterListener {
	private final SmackConnection connection;
	private final Logger logger = LoggerFactory.getLogger(getClass());

	public SmackRosterListener(SmackConnection connection) {
		this.connection = connection;
	}

	@Override
	public void entriesAdded(Collection<String> arg0) {
		logger.info("Entries added: " + arg0);
	}

	@Override
	public void entriesDeleted(Collection<String> arg0) {
		logger.info("Entries deleted: " + arg0);
	}

	@Override
	public void entriesUpdated(Collection<String> arg0) {
		logger.info("Entries updated: " + arg0);
	}

	@Override
	public void presenceChanged(Presence presence) {
		logger.info("Presence changed: " + presence);
		if (presence.isAvailable()) {
			if (presence.hasExtension("toy", StreamOpen.CLIENT_NAMESPACE)) {
				final DefaultExtensionElement defaultExtensionElement = (DefaultExtensionElement) presence
						.getExtension("toy", StreamOpen.CLIENT_NAMESPACE);
				final String status = defaultExtensionElement.getValue("status");
				connection.setOnline(presence.getFrom().split("/")[0], "true".equals(status));
			} else {
				connection.setOnline(presence.getFrom().split("/")[0], false);
			}
		} else {
			connection.setOnline(presence.getFrom().split("/")[0], false);
		}

	}

}
