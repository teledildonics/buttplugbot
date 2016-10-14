package buttplugbot.telegrambot.util;

import java.lang.reflect.Type;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

public class DateSerializer implements JsonDeserializer<Date>, JsonSerializer<Date> {

	@Override
	public JsonElement serialize(Date date, Type type, JsonSerializationContext jsonSerializationContext) {
		String str = null;
		try {
			str = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:S").format(date);

		} catch (final Exception e) {
		}
		return new JsonPrimitive(str);
	}

	@Override
	public Date deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) {
		try {
			return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:S").parse(jsonElement.getAsString());
		} catch (final ParseException e) {
			return null;
		}
	}
}
