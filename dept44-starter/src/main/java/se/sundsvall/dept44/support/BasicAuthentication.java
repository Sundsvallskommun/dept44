package se.sundsvall.dept44.support;

import static se.sundsvall.dept44.util.ResourceUtils.requireNotBlank;

public record BasicAuthentication(String username, String password) {

	public BasicAuthentication {
		username = requireNotBlank(username, "Username must be set");
		password = requireNotBlank(password, "Password must be set");
	}
}
