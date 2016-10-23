package buttplugbot.telegrambot.util;

import org.apache.commons.codec.Charsets;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

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

}
