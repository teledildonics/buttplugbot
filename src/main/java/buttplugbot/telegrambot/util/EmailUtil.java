package buttplugbot.telegrambot.util;

import java.net.URLEncoder;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

import buttplugbot.telegrambot.Config;
import buttplugbot.telegrambot.model.NormalResponse;

public class EmailUtil {

	private static final Logger logger = LoggerFactory.getLogger(EmailUtil.class);

	private static final Gson gson = new Gson();

	private static final String path = "/ajaxCheckEmailOrUserIdRegisted?email=";

	public static String check(String email) {
		try {

			final HttpGet httpGet = new HttpGet(Config.apiUrl + path + URLEncoder.encode(email, "UTF-8"));
			final CloseableHttpResponse response = Util.getHttpclient().execute(httpGet);
			try {
				if (response.getStatusLine().getStatusCode() == 200) {
					final HttpEntity entity = response.getEntity();
					final String json = EntityUtils.toString(entity);
					final NormalResponse normalResponse = gson.fromJson(json, NormalResponse.class);
					return normalResponse.isResult() ? normalResponse.getData().toString() : null;
				}
				return null;
			} finally {
				response.close();
			}
		} catch (final Exception e) {
			logger.warn("Failed to check email {} ", email, e);
			return null;
		}
	}

}
