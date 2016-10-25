package buttplugbot.telegrambot;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

import buttplugbot.telegrambot.HushPlugBot.StatusUpdateMessageSender;
import buttplugbot.telegrambot.model.StatusUpdate;

public class StatusMessageUpdater {

	private volatile StatusUpdate lastUpdate = null;

	private final Set<StatusUpdateMessageSender> statusUpdateMessageSenderSet = new ConcurrentSkipListSet<>();

	public void update(StatusUpdate update) {
		if (update.equals(lastUpdate)) {
			return;
		}
		lastUpdate = update;
		final List<StatusUpdateMessageSender> remove = new ArrayList<>();
		for (final StatusUpdateMessageSender statusUpdateMessageSender : statusUpdateMessageSenderSet) {
			statusUpdateMessageSender.update(update, () -> {
				remove.add(statusUpdateMessageSender);
			});
		}
		for (final StatusUpdateMessageSender statusUpdateMessage : remove) {
			statusUpdateMessageSenderSet.remove(statusUpdateMessage);
		}
	}

	public void addStatusUpdateMessage(StatusUpdateMessageSender statusUpdateMessageSender, boolean force) {
		if (force && statusUpdateMessageSenderSet.contains(statusUpdateMessageSender)) {
			statusUpdateMessageSenderSet.remove(statusUpdateMessageSender);
		}
		final boolean added = statusUpdateMessageSenderSet.add(statusUpdateMessageSender);
		if (added && lastUpdate != null) {
			statusUpdateMessageSender.update(lastUpdate, () -> {
				statusUpdateMessageSenderSet.remove(statusUpdateMessageSender);
			});
		}

	}
}
