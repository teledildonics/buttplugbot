package buttplugbot.telegrambot.model;

import java.util.UUID;

public class UserMessage {
	private final String id;
	private String data;
	private String fromJid;
	private boolean isAutoPlay;
	private Integer isPlay;
	private String patternId;
	private String text;
	private String time;
	private String toJid;
	private Type type;
	private String typeDetail;
	private String url;

	public enum Type {
		chat, toy, pattern, audio, picture, gif, live, sync, video;
	}

	public UserMessage() {
		this.id = UUID.randomUUID().toString().replaceAll("-", "");
		this.isAutoPlay = false;
		this.type = Type.chat;
	}

	public String getId() {
		return id;
	}


	public String getData() {
		return data;
	}

	public void setData(String data) {
		this.data = data;
	}

	public String getFromJid() {
		return fromJid;
	}

	public void setFromJid(String fromJid) {
		this.fromJid = fromJid;
	}

	public boolean isAutoPlay() {
		return isAutoPlay;
	}

	public void setAutoPlay(boolean isAutoPlay) {
		this.isAutoPlay = isAutoPlay;
	}

	public Integer getIsPlay() {
		return isPlay;
	}

	public void setIsPlay(Integer isPlay) {
		this.isPlay = isPlay;
	}

	public String getPatternId() {
		return patternId;
	}

	public void setPatternId(String patternId) {
		this.patternId = patternId;
	}

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
	}

	public String getTime() {
		return time;
	}

	public void setTime(String time) {
		this.time = time;
	}

	public String getToJid() {
		return toJid;
	}

	public void setToJid(String toJid) {
		this.toJid = toJid;
	}

	public Type getType() {
		return type;
	}

	public void setType(Type type) {
		this.type = type;
	}

	public String getTypeDetail() {
		return typeDetail;
	}

	public void setTypeDetail(String typeDetail) {
		this.typeDetail = typeDetail;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

}