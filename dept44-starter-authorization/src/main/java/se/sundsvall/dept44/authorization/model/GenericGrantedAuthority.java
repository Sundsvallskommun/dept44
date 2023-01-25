package se.sundsvall.dept44.authorization.model;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.springframework.util.Assert.hasText;

import java.util.Objects;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.SpringSecurityCoreVersion;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.InvalidPathException;
import com.jayway.jsonpath.JsonPath;

import net.minidev.json.JSONArray;

public class GenericGrantedAuthority implements GrantedAuthority {

	private static final long serialVersionUID = SpringSecurityCoreVersion.SERIAL_VERSION_UID;

	private static final String AUTH_ROLE_REQUIRED = "An authority role is required";
	private static final String REQUESTED_ACCESS_REQUIRED = "A path to requested access is required";
	private static final String INVALID_JSON_PATH = "Provided jsonpath '%s' is invalid";
	private final String role;
	private final DocumentContext accesses; // NOSONAR

	public static GenericGrantedAuthority create(String role) {
		return new GenericGrantedAuthority(role, null);
	}

	public static GenericGrantedAuthority create(String role, String accesses) {
		return new GenericGrantedAuthority(role, accesses);
	}

	private GenericGrantedAuthority(String role, String accesses) {
		hasText(role, AUTH_ROLE_REQUIRED);
		this.role = role;
		this.accesses = isNull(accesses) ? null : JsonPath.parse(accesses);
	}

	@Override
	public String getAuthority() {
		return this.role;
	}

	public DocumentContext getAccesses() {
		return this.accesses;
	}

	public boolean hasAuthority(String role) {
		return this.role.equalsIgnoreCase(role);
	}

	public boolean hasAuthority(String role, String jsonPath) {
		hasText(role, AUTH_ROLE_REQUIRED);
		hasText(jsonPath, REQUESTED_ACCESS_REQUIRED);
		isValid(jsonPath);

		JSONArray matches = isNull(accesses) ? null : accesses.read(jsonPath);
		return hasAuthority(role) && nonNull(matches) && !matches.isEmpty();
	}

	private static void isValid(String accessPath) {
		try {
			JsonPath.compile(accessPath);
		} catch (InvalidPathException e) {
			throw new IllegalArgumentException(String.format(INVALID_JSON_PATH, accessPath));
		}
	}

	@Override
	public int hashCode() {
		return Objects.hash(accesses, role);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		GenericGrantedAuthority other = (GenericGrantedAuthority) obj;
		return Objects.equals(accesses, other.accesses) && Objects.equals(role, other.role);
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("GenericGrantedAuthority [role=").append(role).append(", accesses=")
				.append(isNull(accesses) ? null : accesses.jsonString()).append("]");
		return builder.toString();
	}
}
