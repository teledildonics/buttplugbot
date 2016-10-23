package buttplugbot.telegrambot.util;

import java.lang.reflect.Type;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import buttplugbot.telegrambot.model.Plug;
import buttplugbot.telegrambot.model.Plug.Trace;

public class PlugSerializer implements JsonDeserializer<Plug>, JsonSerializer<Plug> {

	@Override
	public JsonElement serialize(Plug src, Type typeOfSrc, JsonSerializationContext context) {
		final JsonObject obj = new JsonObject();
		obj.addProperty("id", src.getId());
		obj.addProperty("targetJid", src.getTargetJid());
		obj.addProperty("userId", src.getUserId());
		obj.addProperty("name", src.getName());
		obj.addProperty("trace", src.getTrace().toString());
		if (src.getUserChatId() != null) {
			obj.addProperty("userChatId", src.getUserChatId());
		}
		return obj;
	}

	@Override
	public Plug deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
			throws JsonParseException {
		final JsonObject obj = json.getAsJsonObject();
		final long id = Long.parseLong(obj.get("id").getAsString(), 16);
		final String targetJid = obj.get("targetJid").getAsString();
		final long userId = obj.get("userId").getAsLong();
		final String name = obj.get("name").getAsString();
		final Trace trace = obj.get("trace") == null ? Trace.NO_TRACE : Trace.valueOf(obj.get("trace").getAsString());
		final Long userChatId = obj.get("userChatId") == null ? null : obj.get("userChatId").getAsLong();
		return new Plug(id, targetJid, userId, name, trace, userChatId);
	}

}
