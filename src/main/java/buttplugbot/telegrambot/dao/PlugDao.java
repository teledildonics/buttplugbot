package buttplugbot.telegrambot.dao;

import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.jivesoftware.smack.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import buttplugbot.telegrambot.model.Plug;
import buttplugbot.telegrambot.model.TemporaryPlug;
import buttplugbot.telegrambot.util.PlugSerializer;

public class PlugDao {
	private final SecureRandom sr = new SecureRandom();

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final Map<String, Plug> plugsById = new ConcurrentHashMap<>();

	private final Map<String, Plug> plugsByEmails = new ConcurrentHashMap<>();

	private final Map<Long, Plug> plugsByUsers = new ConcurrentHashMap<>();

	private final Map<String, TemporaryPlug> temporaryPlugs = new ConcurrentHashMap<>();

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
		final TemporaryPlug temporaryPlug = temporaryPlugs.get(id);
		if (temporaryPlug != null) {
			return temporaryPlug.getPlug();
		}
		return plugsById.get(id);
	}

	public Plug getPlugByUserId(long id) {
		return plugsByUsers.get(id);
	}

	public Collection<Plug> getAllPlugs() {
		return plugsByEmails.values();
	}

	public long createId() {
		long id = sr.nextLong();
		while (id < 0 || plugsById.containsKey(Long.toHexString(id))
				|| temporaryPlugs.containsKey(Long.toHexString(id))) {
			id = sr.nextLong();
		}
		return id;
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

	public String addTemporary(Plug plug, int hours) {
		final long now = System.currentTimeMillis();
		final TemporaryPlug temporaryPlug = new TemporaryPlug(null, now + hours * 3600000);
		final String id = Long.toHexString(createId());
		temporaryPlugs.put(id, temporaryPlug);
		return id;
	}

	public void cleanTemporary() {
		final Iterator<Entry<String, TemporaryPlug>> it = temporaryPlugs.entrySet().iterator();
		final long now = System.currentTimeMillis();
		while (it.hasNext()) {
			final TemporaryPlug temporaryPlug = it.next().getValue();
			if (temporaryPlug.getTimeout() < now) {
				it.remove();
			}
		}
	}

}
