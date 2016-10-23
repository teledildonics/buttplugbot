package buttplugbot.telegrambot.model;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

public class Plug {
	public static enum State {
		IDLE, BUZZ, SINE
	}

	public static enum Trace {
		NO_TRACE, SINGLE_TRACE, FULL_TRACE
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

	private volatile Trace trace = Trace.NO_TRACE;

	private volatile Long userChatId = null;

	private volatile String lastInteractedUser = null;

	private static long createId() {
		long id = sr.nextLong();
		while (id < 0) {
			id = sr.nextLong();
		}
		return id;
	}

	public Plug(String targetJid, long userId) {
		this(createId(), targetJid, userId, "unknown", Trace.NO_TRACE, null);
	}

	public Plug(long id, String targetJid, long userId, String name, Trace trace, Long userChatId) {
		this.id = id;
		this.targetJid = targetJid;
		this.userId = userId;
		this.userChatId = userChatId;
		this.name = name;
		this.trace = trace;
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

	public Trace getTrace() {
		return trace;
	}

	public void setTrace(Trace trace) {
		this.trace = trace;
	}

	public String getTraceAsString() {
		switch (trace) {
		default:
		case NO_TRACE:
			return "no trace";
		case SINGLE_TRACE:
			return "single trace";
		case FULL_TRACE:
			return "full trace";
		}
	}

	public void setLastInteractedUser(String lastInteractedUser) {
		this.lastInteractedUser = lastInteractedUser;
	}

	public Long getUserChatId() {
		return userChatId;
	}

	public void setUserChatId(Long userChatId) {
		this.userChatId = userChatId;
	}

	@Override
	public String toString() {
		final StringBuilder b = new StringBuilder();
		b.append("*Buttplug of ").append(getName()).append("* (" + getTraceAsString() + ")\n");
		if (!isOnline()) {
			b.append("Plug not connected, User " + (isUserOnline() ? "online" : "offline"));
		} else {
			if (state == State.IDLE) {
				b.append("Idling ").append(getAmplitude()).append("\n");
				b.append("  ").append(getInterval());
			}
			if (state == State.BUZZ) {
				b.append("Buzzing ").append(getAmplitude());
				if ((getTrace() == Trace.SINGLE_TRACE || getTrace() == Trace.FULL_TRACE)
						&& lastInteractedUser != null) {
					b.append(" by ").append(lastInteractedUser);
				}
				b.append("\n");
				b.append("  ").append(getInterval());
			}
			if (state == State.SINE) {
				b.append("Playing sine pattern ").append(getAmplitude()).append(" for ~")
						.append(Math.ceil(getRemainingSeconds() / 5) * 5).append(" s");
				if ((getTrace() == Trace.SINGLE_TRACE || getTrace() == Trace.FULL_TRACE)
						&& lastInteractedUser != null) {
					b.append(" by ").append(lastInteractedUser);
				}
				b.append("\n");
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
