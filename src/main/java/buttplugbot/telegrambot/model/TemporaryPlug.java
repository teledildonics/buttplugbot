package buttplugbot.telegrambot.model;

public class TemporaryPlug {
	private final Plug plug;

	private final long timeout;

	public TemporaryPlug(Plug plug, long timeout) {
		this.plug = plug;
		this.timeout = timeout;
	}

	public Plug getPlug() {
		return plug;
	}

	public long getTimeout() {
		return timeout;
	}
}
