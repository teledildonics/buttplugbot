package buttplugbot.telegrambot.dao;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.codec.Charsets;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.util.EntityUtils;
import org.jivesoftware.smack.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import buttplugbot.telegrambot.Config;
import buttplugbot.telegrambot.model.NormalResponse;
import buttplugbot.telegrambot.model.Pattern;
import buttplugbot.telegrambot.util.Util;

public class PatternDao {
	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final Gson gson = new Gson();

	private final Map<String, Pattern> patternUrls = new HashMap<>();

	public PatternDao() {
		try {
			loadDB();
			if (Config.createPatterns) {
				if (checkPatterns()) {
					storeDB();
				}
			}
		} catch (final Exception e) {
			logger.warn("Failed to load patterns", e);
		}
	}

	private boolean checkPatterns() throws Exception {
		boolean failed = false;
		for (int i = 0; i < 12; i++) {
			for (int a = 1; a < 17; a++) {

				Pattern pattern = patternUrls.get(key(i, a));
				if (pattern == null || !isValidPattern(pattern.getUrl(), i, a)) {
					pattern = uploadPattern(i, a);
					if (pattern == null) {
						throw new Exception("Url is null!");
					}
					logger.info("Uploaded pattern " + key(i, a) + " to " + pattern.getUrl());
					patternUrls.put(key(i, a), pattern);
					failed = true;
				}
				if (pattern.getTimer() == null) {
					pattern.setTimer(timer(getLength(i)));
					failed = true;
				}
			}
		}
		return failed;
	}

	private Pattern uploadPattern(int i, int a) throws Exception {
		final String pattern = createPattern(i, a);
		final String uploadUrl = "/wear/chat/saveFile/pattern";
		final HttpPost httpPost = new HttpPost(Config.apiUrl+uploadUrl);

		final String fileName = UUID.randomUUID().toString().replace("-", "");
		final HttpEntity mulitpartEntity = MultipartEntityBuilder.create()
		.addTextBody("email", Config.email, Util.TEXT_PLAIN)
		.addBinaryBody("file", pattern.getBytes(Charsets.UTF_8), Util.TEXT_PLAIN, fileName).build();
		httpPost.setEntity(mulitpartEntity);
		final CloseableHttpResponse response = Util.getHttpclient().execute(httpPost);
		try {
			if (response.getStatusLine().getStatusCode() == 200) {
				final HttpEntity entity = response.getEntity();
				final String json = EntityUtils.toString(entity);
				final NormalResponse normalResponse = gson.fromJson(json, NormalResponse.class);
				return new Pattern(fileName, normalResponse.getMessage(), timer(getLength(i)));
			}
		} finally {
			response.close();
		}
		throw new Exception("Failed to upload file");
	}

	private String timer(long value) {
		final long minutes = value / 60000;
		final long seconds = value % 60000 / 1000;
		return String.format("%02d:%02d", minutes, seconds);
	}

	private boolean isValidPattern(String url, int i, int a) throws Exception {
		logger.info("Checking pattern " + key(i, a) + " at " + url);
		final String externalPattern = downloadPattern(url);
		final String pattern = createPattern(i, a);
		return pattern.equals(externalPattern);
	}

	private String downloadPattern(String url) throws Exception {
		final HttpURLConnection httpURLConnection = (HttpURLConnection) new URL(Config.apiUrl + url).openConnection();
		try {
			httpURLConnection.setConnectTimeout(5000);
			httpURLConnection.setRequestMethod("GET");
			httpURLConnection.setDoInput(true);
			if (httpURLConnection.getResponseCode() == 200) {
				final BufferedReader bufferedReader = new BufferedReader(
						new InputStreamReader(httpURLConnection.getInputStream()));
				try {
					String line;
					final StringBuffer stringBuffer = new StringBuffer();
					while (true) {
						line = bufferedReader.readLine();
						if (line == null) {
							break;
						}
						stringBuffer.append(line);
					}
					return stringBuffer.toString();
				} finally {
					bufferedReader.close();
				}
			}
		} finally {
			httpURLConnection.disconnect();
		}
		throw new Exception("Failed to download file " + url);
	}

	public Pattern getPatternUrl(long interval, long amplitude) {
		return patternUrls.get(key(interval, amplitude));
	}

	private String key(long interval, long amplitude) {
		if (interval < 0 || interval > 11 || amplitude < 1 || amplitude > 16) {
			throw new IllegalArgumentException();
		}
		return interval + "_" + amplitude;
	}

	public long getLength(long interval) {
		final long stepSize = getStepSize(interval);
		final long count = getCount(stepSize);
		return count * stepSize * 100;
	}

	private long getCount(long stepSize) {
		return Config.vibrationLength * 10 / stepSize;
	}

	private String createPattern(int interval, int amplitude) {

		final long stepSize = getStepSize(interval);
		final long count = getCount(stepSize);
		final StringBuilder b = new StringBuilder();

		for (int j = 0; j < count; j++) {
			for (int i = 0; i < stepSize; i++) {
				final double x = i % stepSize / (double) stepSize;
				final double sinWave = (Math.sin(x * Math.PI * 2.0 + Math.PI * 1.5) + 1.0) / 2.0;
				// On the transport protocol the values have to be between 20
				// and 100, but the vibrator only supports 4 to 20.
				final long power = Math.round(amplitude * sinWave) * 5 + 20;
				b.append(power).append(",");
			}
		}
		return b.toString();
	}

	private long getStepSize(long interval) {
		return Math.round(getInterval(interval) * 10.0);
	}

	private double getInterval(double interval) {
		return 0.03 * interval * interval + 0.085 * interval + 0.45;
	}

	private synchronized void loadDB() {
		final Gson gson = new Gson();
		try {
			final TypeToken<Map<String, Pattern>> patternUrlsType = new TypeToken<Map<String, Pattern>>() {
			};
			patternUrls.putAll(
					gson.fromJson(FileUtils.readFileOrThrow(new File("patterns.json")), patternUrlsType.getType()));
		} catch (final IOException e) {
			logger.warn("Unable to load from Json DB", e);
		}
	}

	private synchronized void storeDB() {
		final Gson gson = new Gson();
		try {
			FileUtils.writeFileOrThrow(new File("patterns.json"), gson.toJson(patternUrls));
		} catch (final IOException e) {
			logger.warn("Unable to store to Json DB", e);
		}
	}
}
