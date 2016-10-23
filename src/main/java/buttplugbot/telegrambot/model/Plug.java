package buttplugbot.telegrambot.model;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

public class Plug {
	public static enum State {
		IDLE, BUZZ, SINE
	}

	private final static SecureRandom sr = new SecureRandom();

	private final long id;

	private final String targetJid;

	private final long userId;

	private volatile String name;

	private volatile State state = State.IDLE;

	private volatile State nextState = State.IDLE;

	private final AtomicLong nextStateChange = new AtomicLong();

	private final LimitedValue interval = new LimitedValue(0, 3, 11) {

		@Override
		public String toString() {
			final long value = getValue();
			final long temp = getTempValue();
			if (value == temp) {
				return String.format(Locale.US, "∿  %.1f s", intervalToSeconds(value));
			} else {
				return String.format(Locale.US, "∿  %.1f s ⇒ %.1f s", intervalToSeconds(value),
						intervalToSeconds(temp));
			}
		}
	};

	private final LimitedValue amplitude = new LimitedValue(0, 16, 16) {

		@Override
		public String toString() {
			final long value = getValue();
			final long temp = getTempValue();
			if (value == temp) {
				return String.format(Locale.US, "%d %%", asPercentage(value));
			} else {
				return String.format(Locale.US, "%d %% ⇒ %d %%", asPercentage(value), asPercentage(temp));
			}
		}

		public long asPercentage(long value) {
			return (value + 4) * 100 / (getMax() + 4);
		}
	};

	private volatile boolean online = false;

	private volatile boolean userOnline = false;

	private static long createId() {
		long id = sr.nextLong();
		while (id < 0) {
			id = sr.nextLong();
		}
		return id;
	}

	public Plug(String targetJid, long userId) {
		this(createId(), targetJid, userId, "unknown");
	}

	public Plug(long id, String targetJid, long userId, String name) {
		this.id = id;
		this.targetJid = targetJid;
		this.userId = userId;
		this.name = name;
	}

	public String getId() {
		return Long.toHexString(id);
	}

	public boolean isOnline() {
		return online;
	}

	public LimitedValue getInterval() {
		return interval;
	}

	public LimitedValue getAmplitude() {
		return amplitude;
	}

	public String getTargetJid() {
		return targetJid;
	}

	public void setOnline(boolean online) {
		this.online = online;
	}

	public boolean isUserOnline() {
		return userOnline;
	}

	public void setUserOnline(boolean userOnline) {
		this.userOnline = userOnline;
	}

	public long getUserId() {
		return userId;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public static double intervalToSeconds(long interval) {
		return 0.03 * interval * interval + 0.085 * interval + 0.45;
	}

	public State getState() {
		return state;
	}

	public State getNextState() {
		return nextState;
	}

	public void buzz() {
		nextState = State.BUZZ;
	}

	public double getRemainingSeconds() {
		return (nextStateChange.get() - System.currentTimeMillis()) / 1000;
	}

	@Override
	public String toString() {
		final StringBuilder b = new StringBuilder();
		b.append("*Buttplug of ").append(getName()).append("*\n");
		if (!isOnline()) {
			b.append("Plug not connected, User " + (isUserOnline() ? "online" : " offline"));
		} else {
			if (state == State.IDLE) {
				b.append("Idling ").append(getAmplitude()).append("\n");
				b.append("  ").append(getInterval());
			}
			if (state == State.BUZZ) {
				b.append("Buzzing ").append(getAmplitude()).append("\n");
				b.append("  ").append(getInterval());
			}
			if (state == State.SINE) {
				b.append("Playing sine pattern ").append(getAmplitude()).append(" for ~")
						.append(Math.ceil(getRemainingSeconds() / 5) * 5).append(" s");
				b.append("  ").append(getInterval()).append("\n");
			}
		}
		return b.toString();
	}

	public void changeState(State state, long length) {
		this.state = state;
		this.nextState = State.IDLE;
		this.nextStateChange.set(System.currentTimeMillis() + length);
	}

	public void playPattern() {
		this.nextState = State.SINE;
	}
}
