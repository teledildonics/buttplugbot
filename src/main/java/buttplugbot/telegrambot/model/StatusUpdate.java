package buttplugbot.telegrambot.model;

public class StatusUpdate {

	private final String message;

	private final boolean keyboard;

	public StatusUpdate(Plug plug) {
		this.message = plug.toString();
		this.keyboard = true;
	}

	public StatusUpdate(String message) {
		this.message = message;
		this.keyboard = false;
	}

	public String getMessage() {
		return message;
	}

	public boolean isKeyboard() {
		return keyboard;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (keyboard ? 1231 : 1237);
		result = prime * result + (message == null ? 0 : message.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		final StatusUpdate other = (StatusUpdate) obj;
		if (keyboard != other.keyboard) {
			return false;
		}
		if (message == null) {
			if (other.message != null) {
				return false;
			}
		} else if (!message.equals(other.message)) {
			return false;
		}
		return true;
	}

}