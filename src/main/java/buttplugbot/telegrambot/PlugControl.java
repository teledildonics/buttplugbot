package buttplugbot.telegrambot;

import buttplugbot.telegrambot.dao.PatternDao;
import buttplugbot.telegrambot.model.Pattern;
import buttplugbot.telegrambot.model.Plug;
import buttplugbot.telegrambot.model.Plug.State;
import buttplugbot.telegrambot.model.StatusUpdate;
import buttplugbot.telegrambot.smack.SmackConnection;

public class PlugControl {
	private final Plug plug;

	private final PlugRecipient plugRecipient;

	private final SmackConnection connection;

	private final PatternDao patternDao;

	private final StatusMessageUpdater statusMessageUpdater;

	public PlugControl(Plug plug, SmackConnection connection, PatternDao patternDao) {
		this.plug = plug;
		this.connection = connection;
		this.patternDao = patternDao;
		this.plugRecipient = new PlugRecipient(connection, plug.getTargetJid());
		this.statusMessageUpdater = new StatusMessageUpdater();
	}

	public void sendUpdate() {
		plug.setUserOnline(connection.isOnline(plug.getTargetJid()));
		if (connection.isPlugOnline(plug.getTargetJid())) {
			plug.setOnline(true);
		} else {
			plug.setOnline(false);
			statusMessageUpdater.update(new StatusUpdate(plug));
			return;
		}

		if ((plug.getState() == State.IDLE || plug.getRemainingSeconds() < 0) && plug.getNextState() == State.BUZZ) {
			plug.changeState(State.BUZZ, Config.buzzLength);
			final long amplitude = plug.getAmplitude().commit();
			plugRecipient.sendPowerLevel((amplitude + 4) * 5);
		} else if ((plug.getState() == State.IDLE || plug.getRemainingSeconds() < 0)
				&& plug.getNextState() == State.SINE) {
			final long amplitude = plug.getAmplitude().commit();
			final long interval = plug.getInterval().commit();
			plug.changeState(State.SINE, patternDao.getLength(interval));
			if (amplitude == 0) {
				plugRecipient.sendPowerLevel(20);
			} else {
				final Pattern pattern = patternDao.getPatternUrl(interval, amplitude);
				plugRecipient.sendPattern(pattern);
			}
		} else if (plug.getState() != State.IDLE && plug.getRemainingSeconds() < 0) {
			plug.changeState(State.IDLE, 0);
			plugRecipient.sendPowerLevel(0);
		}

		statusMessageUpdater.update(new StatusUpdate(plug));
	}

	public String processMessage(Integer senderId, String message) {
		if (!plug.isOnline()) {
			return "Vibrator is offline.";
		}

		switch (message) {
		case "sine":
			plug.playPattern();
			break;
		case "buzz":
			plug.buzz();
			break;
		case "interval-":
			plug.getInterval().modifyValue(-1);
			return "Pattern interval changed " + plug.getInterval();
		case "interval+":
			plug.getInterval().modifyValue(+1);
			return "Pattern interval changed " + plug.getInterval();
		case "amplitude-":
			plug.getAmplitude().modifyValue(-2);
			return "Power changed " + plug.getAmplitude();
		case "amplitude+":
			plug.getAmplitude().modifyValue(+2);
			return "Power changed " + plug.getAmplitude();
		}
		return null;
	}

	public StatusMessageUpdater getStatusMessageUpdater() {
		return statusMessageUpdater;
	}

}
