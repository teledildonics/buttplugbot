package buttplugbot.telegrambot.dao;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jivesoftware.smack.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import buttplugbot.telegrambot.model.Plug;
import buttplugbot.telegrambot.util.PlugSerializer;

public class PlugDao {
	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final Map<String, Plug> plugsById = new ConcurrentHashMap<>();

	private final Map<String, Plug> plugsByEmails = new ConcurrentHashMap<>();

	private final Map<Long, Plug> plugsByUsers = new ConcurrentHashMap<>();

	private static final Gson gson;

	static {
		final GsonBuilder gsonBuilder = new GsonBuilder();
		gsonBuilder.registerTypeAdapter(Plug.class, new PlugSerializer());
		gson = gsonBuilder.create();
	}

	public PlugDao() {
		loadDB();
	}

	public Plug getPlugByEmail(String email) {
		return plugsByEmails.get(email.toLowerCase(Locale.US));
	}

	public Plug getPlugById(String id) {
		return plugsById.get(id);
	}

	public Plug getPlugByUserId(long id) {
		return plugsByUsers.get(id);
	}

	public Collection<Plug> getAllPlugs() {
		return plugsByEmails.values();
	}

	public synchronized void loadDB() {
		try {
			final TypeToken<List<Plug>> plugListType = new TypeToken<List<Plug>>() {
			};
			final List<Plug> plugs = gson.fromJson(FileUtils.readFileOrThrow(new File("plugs.json")),
					plugListType.getType());
			for (final Plug plug : plugs) {
				plugsByEmails.put(plug.getTargetJid(), plug);
				plugsById.put(plug.getId(), plug);
				plugsByUsers.put(plug.getUserId(), plug);
			}
		} catch (final IOException e) {
			logger.warn("Unable to load from Json file", e);
		}
	}

	public synchronized void storeDB() {
		try {
			FileUtils.writeFileOrThrow(new File("plugs.json"), gson.toJson(plugsById.values()));
		} catch (final IOException e) {
			logger.warn("Unable to store to Json DB", e);
		}
	}

	public void add(Plug plug) {
		plugsByEmails.put(plug.getTargetJid(), plug);
		plugsById.put(plug.getId(), plug);
		plugsByUsers.put(plug.getUserId(), plug);
		storeDB();
	}

	public void remove(Plug plug) {
		plugsByEmails.remove(plug.getTargetJid());
		plugsById.remove(plug.getId());
		plugsByUsers.remove(plug.getUserId());
		storeDB();
	}

}
