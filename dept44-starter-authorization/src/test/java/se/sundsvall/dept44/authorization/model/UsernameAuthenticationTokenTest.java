package se.sundsvall.dept44.authorization.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static se.sundsvall.dept44.authorization.model.UsernameAuthenticationToken.authenticated;
import static se.sundsvall.dept44.authorization.model.UsernameAuthenticationToken.unauthenticated;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;

class UsernameAuthenticationTokenTest {

	@Test
	void assignableFrom() {
		assertThat(AbstractAuthenticationToken.class).isAssignableFrom(UsernameAuthenticationToken.class);
	}

	@Test
	void emptyUnauthenticatedBean() {
		UsernameAuthenticationToken bean = new UsernameAuthenticationToken(null, null);

		assertThat(bean.isAuthenticated()).isFalse();
		assertThat(bean.getAuthorities()).isEmpty();
		assertThat(bean.getCredentials()).isNull();
		assertThat(bean.getDetails()).isNull();
		assertThat(bean.getName()).isEmpty();
		assertThat(bean.getPrincipal()).isNull();
	}

	@Test
	void emptyAuthenticatedBean() {
		UsernameAuthenticationToken bean = new UsernameAuthenticationToken(null, null, null);

		assertThat(bean.isAuthenticated()).isTrue();
		assertThat(bean.getAuthorities()).isEmpty();
		assertThat(bean.getCredentials()).isNull();
		assertThat(bean.getDetails()).isNull();
		assertThat(bean.getName()).isEmpty();
		assertThat(bean.getPrincipal()).isNull();
	}

	@Test
	void unauthenticatedConstructor() {
		final var principal = new Object();
		final var credentials = new Object();

		UsernameAuthenticationToken bean = unauthenticated(principal, credentials);

		assertThat(bean.isAuthenticated()).isFalse();
		assertThat(bean.getAuthorities()).isEmpty();
		assertThat(bean.getCredentials()).isEqualTo(credentials);
		assertThat(bean.getDetails()).isNull();
		assertThat(bean.getName()).isEqualTo(principal.toString());
		assertThat(bean.getPrincipal()).isEqualTo(principal);
	}

	@Test
	void authenticatedConstructor() {
		final var role = "ROLE";
		final var principal = new Object();
		final var authorities = List.of(GenericGrantedAuthority.create(role));

		UsernameAuthenticationToken bean = authenticated(principal, authorities);

		assertThat(bean.isAuthenticated()).isTrue();
		assertThat(bean.getAuthorities()).isEqualTo(authorities);
		assertThat(bean.getCredentials()).isNull();
		assertThat(bean.getDetails()).isNull();
		assertThat(bean.getName()).isEqualTo(principal.toString());
		assertThat(bean.getPrincipal()).isEqualTo(principal);
	}

	@Test
	void setToAuthenticated() {
		UsernameAuthenticationToken bean = unauthenticated(null, null);

		final var exception = assertThrows(IllegalArgumentException.class, () -> bean.setAuthenticated(true));
		assertThat(exception.getMessage()).isEqualTo(
			"Cannot set this token to trusted - use constructor which takes a GrantedAuthority list instead");
	}

	@Test
	void eraseCredentials() {
		final var principal = new Object();
		final var credentials = new Object();

		UsernameAuthenticationToken bean = unauthenticated(principal, credentials);
		bean.eraseCredentials();

		assertThat(bean.getCredentials()).isNull();
	}

	@Test
	void testHashCode() {
		final var authorities = List.of(GenericGrantedAuthority.create("ROLE"));

		UsernameAuthenticationToken bean1 = authenticated(User.create().withUsername("USER"), authorities);
		UsernameAuthenticationToken bean2 = authenticated(User.create().withUsername("USER"), authorities);

		assertThat(bean1).hasSameHashCodeAs(bean1).doesNotHaveSameHashCodeAs(bean2);
		assertThat(bean2).hasSameHashCodeAs(bean2).doesNotHaveSameHashCodeAs(bean1);
	}

	@Test
	void testEquals() {
		final var principal = User.create().withUsername("USER");
		final var authorities = List.of(GenericGrantedAuthority.create("ROLE"));

		UsernameAuthenticationToken originalBean = authenticated(principal, authorities);
		UsernameAuthenticationToken equalBean = authenticated(principal, authorities);
		UsernameAuthenticationToken equalContentBean = authenticated(User.create().withUsername("USER"), authorities);
		UsernameAuthenticationToken notEqualBean = authenticated(User.create().withUsername("OTHER_USER"), authorities);

		assertThat(originalBean.equals(originalBean)).isTrue();
		assertThat(originalBean.equals(equalBean)).isTrue();
		assertThat(originalBean.equals(null)).isFalse();
		assertThat(originalBean.equals(new Object())).isFalse();
		assertThat(originalBean.equals(equalContentBean)).isFalse();
		assertThat(originalBean.equals(notEqualBean)).isFalse();
	}
}
