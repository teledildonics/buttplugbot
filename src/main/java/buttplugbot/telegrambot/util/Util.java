package buttplugbot.telegrambot.util;

import org.apache.commons.codec.Charsets;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.LoggerFactory;

public class Util {

	private static final CloseableHttpClient httpclient = HttpClients.createDefault();

	public static final ContentType TEXT_PLAIN = ContentType.create("text/plain", Charsets.UTF_8);

	private static final String w = "_w";

	public static String jidToEmail(String jid) {
		String email = StringUtils.substringBefore(jid, "@");
		email = StringUtils.removeEnd(email, w);
		email = StringUtils.replace(email, "!!!", "@");
		return email;
	}

	public static String emailToJidUser(String email) {
		final String jid = StringUtils.replace(email, "@", "!!!");
		return jid + w;
	}

	public static CloseableHttpClient getHttpclient() {
		return httpclient;
	}

	public static double parsePositiveDouble(String... string) {
		if (string == null || string.length < 1 || StringUtils.isEmpty(string[0])) {
			return Double.NaN;
		}
		try {
			final double value = Double.parseDouble(string[0]);
			if (!Double.isFinite(value) || value <= 0.0) {
				return Double.NaN;
			} else {
				return value;
			}
		} catch (final NumberFormatException e) {
			LoggerFactory.getLogger(Util.class).info("Unable to parse number: {}", string, e);
			return Double.NaN;
		}
	}
}
