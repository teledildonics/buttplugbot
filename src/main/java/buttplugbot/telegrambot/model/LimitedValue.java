package buttplugbot.telegrambot.model;

import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.lang3.Validate;

public class LimitedValue {
	private final long min;

	private final long max;

	private final AtomicLong value;

	private final AtomicLong temp;

	public LimitedValue(long min, long value, long max) {
		Validate.inclusiveBetween(min, max, value);
		this.min = min;
		this.max = max;
		this.value = new AtomicLong(value);
		this.temp = new AtomicLong(value);
	}

	public long modifyValue(final long delta) {
		return this.temp.updateAndGet((long operand) -> {
			return Math.min(max, Math.max(min, operand + delta));
		});
	}

	public long setValue(final long value) {
		return this.temp.updateAndGet((long operand) -> {
			return Math.min(max, Math.max(min, value));
		});
	}

	public long commit() {
		value.set(getTempValue());
		return value.get();
	}

	public long getValue() {
		return value.get();
	}

	public long getTempValue() {
		return temp.get();
	}

	public long getMin() {
		return min;
	}

	public long getMax() {
		return max;
	}
}
