package buttplugbot.telegrambot.smack;

import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.Stanza;

public class SmackStanzaListener implements StanzaListener {

	SmackConnection connection;

	public SmackStanzaListener(SmackConnection connection) {
		this.connection = connection;
	}

	@Override
	public void processPacket(Stanza stanza) throws NotConnectedException {
		final Presence presence = (Presence) stanza;
		final String jid = presence.getFrom();
		switch (presence.getType()) {
		case subscribe:
			return;
		case subscribed:
			connection.updateStatus();
			return;
		case unsubscribe:
			connection.sendSubscription(jid, false);
			return;
		case unsubscribed:
			return;
		default:
			return;
		}
	}

}
