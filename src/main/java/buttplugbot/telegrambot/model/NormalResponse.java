package buttplugbot.telegrambot.model;

public class NormalResponse {
	private int code;
	private Object data;
	private String message;
	private boolean result;

	public NormalResponse(boolean result, String message, int code, Object data) {
		this.result = result;
		this.message = message;
		this.code = code;
		this.data = data;
	}

	public int getCode() {
		return code;
	}

	public void setCode(int code) {
		this.code = code;
	}

	public Object getData() {
		return data;
	}

	public void setData(Object data) {
		this.data = data;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public boolean isResult() {
		return result;
	}

	public void setResult(boolean result) {
		this.result = result;
	}

}
