package se.sundsvall.dept44.authorization.model;

import java.util.Collection;
import java.util.Objects;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import static java.util.Collections.emptyList;
import static org.springframework.util.Assert.isTrue;

/**
 * An {@link org.springframework.security.core.Authentication} implementation that is designed for simple presentation
 * of a username.
 * <p>
 * The <code>principal</code> and <code>credentials</code> should be set with an
 * <code>Object</code> that provides the respective property via its
 * <code>Object.toString()</code> method. The simplest such <code>Object</code>
 * to use is <code>String</code>.
 */
public class UsernameAuthenticationToken extends AbstractAuthenticationToken {
	private static final long serialVersionUID = 8821494764693936578L;

	private final transient Object principal;

	private transient Object credentials;

	/**
	 * This constructor can be safely used by any code that wishes to create a
	 * <code>UsernameAuthenticationToken</code>, as the {@link #isAuthenticated()}
	 * will return <code>false</code>.
	 *
	 * @param principal   the principal to set.
	 * @param credentials the credentials to set.
	 */
	public UsernameAuthenticationToken(final Object principal, final Object credentials) {
		super(emptyList());
		this.principal = principal;
		this.credentials = credentials;
		setAuthenticated(false);
	}

	/**
	 * This constructor should only be used by <code>AuthenticationManager</code> or
	 * <code>AuthenticationProvider</code> implementations that are satisfied with
	 * producing a trusted (i.e. {@link #isAuthenticated()} = <code>true</code>) authentication token.
	 *
	 * @param principal   the principal to set.
	 * @param credentials the credentials to set.
	 * @param authorities the authorities to set.
	 */
	public UsernameAuthenticationToken(final Object principal, final Object credentials, final Collection<? extends GrantedAuthority> authorities) {
		super(authorities);
		this.principal = principal;
		this.credentials = credentials;
		super.setAuthenticated(true); // must use super, as we override
	}

	/**
	 * This factory method can be safely used by any code that wishes to create a unauthenticated
	 * <code>UsernameAuthenticationToken</code>.
	 *
	 * @param  principal   the principal to set.
	 * @param  credentials the credentials to set.
	 * @return             UsernameAuthenticationToken with false isAuthenticated() result
	 */
	public static UsernameAuthenticationToken unauthenticated(final Object principal, final Object credentials) {
		return new UsernameAuthenticationToken(principal, credentials);
	}

	/**
	 * This factory method can be safely used by any code that wishes to create a authenticated
	 * <code>UsernameAuthenticationToken</code>.
	 *
	 * @param  principal   the principal to set.
	 * @param  authorities the authorities to set.
	 * @return             UsernameAuthenticationToken with true isAuthenticated() result
	 */
	public static UsernameAuthenticationToken authenticated(final Object principal, final Collection<? extends GrantedAuthority> authorities) {
		return new UsernameAuthenticationToken(principal, null, authorities);
	}

	@Override
	public Object getCredentials() {
		return this.credentials;
	}

	@Override
	public Object getPrincipal() {
		return this.principal;
	}

	@Override
	public void setAuthenticated(final boolean isAuthenticated) throws IllegalArgumentException {
		isTrue(!isAuthenticated, "Cannot set this token to trusted - use constructor which takes a GrantedAuthority list instead");
		super.setAuthenticated(false);
	}

	@Override
	public void eraseCredentials() {
		super.eraseCredentials();
		this.credentials = null;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = (prime * result) + Objects.hash(credentials, principal);
		return result;
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj) {
			return true;
		}
		if (!super.equals(obj)) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		final UsernameAuthenticationToken other = (UsernameAuthenticationToken) obj;
		return Objects.equals(credentials, other.credentials) && Objects.equals(principal, other.principal);
	}
}
