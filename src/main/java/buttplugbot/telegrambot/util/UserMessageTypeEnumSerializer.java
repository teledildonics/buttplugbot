package buttplugbot.telegrambot.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import buttplugbot.telegrambot.model.UserMessage.Type;

public class UserMessageTypeEnumSerializer implements JsonSerializer<Type> {
	@Override
	public JsonElement serialize(Type type, java.lang.reflect.Type type2,
			JsonSerializationContext jsonSerializationContext) {
		return new JsonPrimitive(type.toString());
	}
}
