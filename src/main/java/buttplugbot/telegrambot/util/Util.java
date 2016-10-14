package buttplugbot.telegrambot.util;

import org.apache.commons.lang3.StringUtils;

public class Util {

	private static final String w = "_w";

	public static String jidToEmail(String jid) {
		String email = StringUtils.substringBefore(jid, "@");
		email = StringUtils.removeEnd(email, w);
		email = StringUtils.replace(email, "!!!", "@");
		return email;
	}
	
	public static String emailToJidUser(String email) {
		String jid = StringUtils.replace(email, "@", "!!!");
		return jid + w;
	}


}
