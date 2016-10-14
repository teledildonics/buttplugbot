package buttplugbot.telegrambot.model;

public class Pattern {
	private final String id;

	private final String url;

	private String timer;

	public Pattern(String id, String url, String timer) {
		this.id = id;
		this.url = url;
		this.timer = timer;
	}

	public String getId() {
		return id;
	}

	public String getUrl() {
		return url;
	}

	public String getTimer() {
		return timer;
	}

	public void setTimer(String timer) {
		this.timer = timer;
	}
}
