package buttplugbot.telegrambot.smack;

import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.net.ssl.SSLSocketFactory;

import org.apache.commons.codec.binary.Base64;
import org.jivesoftware.smack.ConnectionConfiguration.SecurityMode;
import org.jivesoftware.smack.ReconnectionManager;
import org.jivesoftware.smack.SmackConfiguration;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.chat.Chat;
import org.jivesoftware.smack.chat.ChatManager;
import org.jivesoftware.smack.chat.ChatMessageListener;
import org.jivesoftware.smack.filter.StanzaTypeFilter;
import org.jivesoftware.smack.packet.DefaultExtensionElement;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.Presence.Mode;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.roster.Roster;
import org.jivesoftware.smack.roster.Roster.SubscriptionMode;
import org.jivesoftware.smack.roster.RosterEntry;
import org.jivesoftware.smack.roster.packet.RosterPacket.ItemType;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration.Builder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import buttplugbot.telegrambot.Config;
import buttplugbot.telegrambot.model.UserMessage;
import buttplugbot.telegrambot.model.UserMessage.Type;
import buttplugbot.telegrambot.util.DateSerializer;
import buttplugbot.telegrambot.util.EmailUtil;
import buttplugbot.telegrambot.util.UserMessageTypeEnumSerializer;
import buttplugbot.telegrambot.util.Util;

public class SmackConnection {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private XMPPTCPConnection xmpp;

	private ChatManager chatManager;

	private Roster roster;

	static final ChatMessageListener messageListener = new SmackChatMessageListener();

	private static final Gson gson;

	private final Map<String, Long> activeUsers = new ConcurrentHashMap<>();

	private final Map<String, Long> activePlugs = new ConcurrentHashMap<>();

	static {
		final GsonBuilder gsonBuilder = new GsonBuilder();
		gsonBuilder.registerTypeAdapter(Date.class, new DateSerializer());
		gsonBuilder.registerTypeAdapter(Type.class, new UserMessageTypeEnumSerializer());
		gson = gsonBuilder.create();
	}

	public void connect() throws Exception {
		SmackConfiguration.DEBUG = false;
		SmackConfiguration.setDefaultPacketReplyTimeout(15000);
		final Builder builder = XMPPTCPConnectionConfiguration.builder();
		builder.setServiceName(Config.serviceName);
		builder.setHost(Config.host);
		builder.setPort(Config.port);
		builder.setConnectTimeout(15000);
		builder.setCompressionEnabled(false);
		builder.setSendPresence(false);
		builder.setSecurityMode(SecurityMode.ifpossible);
		builder.setSocketFactory(SSLSocketFactory.getDefault());
		xmpp = new XMPPTCPConnection(builder.build());
		xmpp.setPacketReplyTimeout(15000);
		xmpp.addConnectionListener(new SmackConnectionListener());
		xmpp.connect();
		ReconnectionManager.getInstanceFor(xmpp).enableAutomaticReconnection();
		chatManager = ChatManager.getInstanceFor(xmpp);
		return;

	}

	public void authenticate() throws Exception {
		if (!xmpp.isAuthenticated()) {
			roster = Roster.getInstanceFor(xmpp);
			roster.setSubscriptionMode(SubscriptionMode.manual);
			xmpp.addSyncStanzaListener(new SmackStanzaListener(this), StanzaTypeFilter.PRESENCE);
			roster.addRosterListener(new SmackRosterListener(this));
			String email = EmailUtil.check(Config.email);
			if (email == null) {
				throw new IllegalStateException("Bot account "+Config.email+" does not exists at Lovense!");
			}
			xmpp.login(Util.emailToJidUser(email), Config.password, Config.resource);
			if (!roster.isLoaded()) {
				roster.reloadAndWait();
			}
			final Collection<RosterEntry> entries = roster.getEntries();
			for (final RosterEntry rosterEntry : entries) {
				if (rosterEntry.getType().equals(ItemType.to)) {
					final Stanza presence = new Presence(Presence.Type.subscribed);
					presence.setTo(rosterEntry.getUser());
					try {
						xmpp.sendStanza(presence);
					} catch (final NotConnectedException e2) {
						logger.warn("Failed to send Stanza", e2);
					}
				}
			}
			chatManager.addChatListener(new SmackChatManagerListener());
			updateStatus();

			return;
		}
	}

	public void sendMessage(UserMessage userMessage) {
		userMessage.setFromJid(emailToJid(Config.email));
		final String json = gson.toJson(userMessage);
		final String target = userMessage.getToJid();

		final Chat createChat = chatManager.createChat(target, messageListener);
		try {
			createChat.sendMessage(new String(Base64.encodeBase64(json.getBytes())));
		} catch (final NotConnectedException e) {
			logger.warn("Failed to send message", e);
		}
	}

	public void sendSubscription(String jid, boolean z) {
		final Stanza presence = new Presence(z ? Presence.Type.subscribed : Presence.Type.unsubscribed);
		presence.setTo(jid);
		try {
			xmpp.sendStanza(presence);
		} catch (final NotConnectedException e) {
			e.printStackTrace();
		}
	}

	public String connectToUser(String email2) {
		final String jid = emailToJid(email2);
		try {
			roster.createEntry(jid, null, null);
			sendSubscription(jid, true);
			return jid;
		} catch (final Exception e) {
			logger.info("Unable to add user to roster " + email2, e);
			return null;
		}
	}

	public String emailToJid(String email) {
		return Util.emailToJidUser(email) + "@" + Config.serviceName;
	}

	public boolean isOnline(String targetJid) {
		final Long timestamp = activeUsers.get(targetJid);
		if (timestamp == null) {
			return false;
		}
		return timestamp > System.currentTimeMillis() - 60 * 1000;
	}

	public boolean isPlugOnline(String targetJid) {
		final Long timestamp = activePlugs.get(targetJid);
		if (timestamp == null) {
			return false;
		}
		return timestamp > System.currentTimeMillis() - 60 * 1000;
	}

	public void setOnline(String targetJid, boolean status) {
		activeUsers.put(targetJid, status ? System.currentTimeMillis() : 0);
	}

	public void setPlugOnline(String targetJid, boolean status) {
		activePlugs.put(targetJid, status ? System.currentTimeMillis() : 0);
	}

	public void updateStatus() {
		final Presence presence2 = new Presence(Presence.Type.available);
		presence2.setMode(Mode.chat);

		final DefaultExtensionElement defaultExtensionElement = new DefaultExtensionElement("toy", null);
		defaultExtensionElement.setValue("name", "Hush Butt Plug");
		defaultExtensionElement.setValue("status", "true");
		presence2.addExtension(defaultExtensionElement);

		final DefaultExtensionElement defaultExtensionElement2 = new DefaultExtensionElement("alltoy", null);
		defaultExtensionElement2.setValue("name", "Hush Butt Plug");
		defaultExtensionElement2.setValue("status", "true");
		presence2.addExtension(defaultExtensionElement2);

		try {
			xmpp.sendStanza(presence2);
		} catch (final NotConnectedException e2) {
			e2.printStackTrace();
		}
	}

	public void removeUser(String targetJid) {
		try {
			final RosterEntry entry = roster.getEntry(targetJid);
			if (entry != null) {
				roster.removeEntry(entry);
			}
		} catch (final Exception e) {
			logger.info("Unable to remove user from roster " + targetJid, e);
		}
		sendSubscription(targetJid, false);
	}

}
