package buttplugbot.telegrambot;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

import buttplugbot.telegrambot.HushPlugBot.StatusUpdateMessage;
import buttplugbot.telegrambot.model.StatusUpdate;

public class StatusMessageUpdater {
	
	private volatile StatusUpdate lastUpdate = null;
	
	private final Set<StatusUpdateMessage> statusUpdateMessageSet = new ConcurrentSkipListSet<>();
	
	public void update(StatusUpdate update) {
		if (update.equals(lastUpdate)) {
			return;
		}
		lastUpdate = update;
		List<StatusUpdateMessage> remove = new ArrayList<>();
		for (StatusUpdateMessage statusUpdateMessage : statusUpdateMessageSet) {
			statusUpdateMessage.update(update, () -> {
				remove.add(statusUpdateMessage);
			});
		}
		for (StatusUpdateMessage statusUpdateMessage : remove) {
			statusUpdateMessageSet.remove(statusUpdateMessage);
		}
	}
	
	public void addStatusUpdateMessage(StatusUpdateMessage statusUpdateMessage, boolean force) {
		if (force && statusUpdateMessageSet.contains(statusUpdateMessage)) {
			statusUpdateMessageSet.remove(statusUpdateMessage);
		}
		boolean added = statusUpdateMessageSet.add(statusUpdateMessage);
		if (added && lastUpdate != null) {
			statusUpdateMessage.update(lastUpdate, () -> {
				statusUpdateMessageSet.remove(statusUpdateMessage);
			});
		}

	}
}
