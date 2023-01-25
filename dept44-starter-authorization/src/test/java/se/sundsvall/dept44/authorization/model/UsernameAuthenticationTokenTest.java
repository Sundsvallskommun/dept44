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
		assertThat(exception.getMessage()).isEqualTo("Cannot set this token to trusted - use constructor which takes a GrantedAuthority list instead");
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

		UsernameAuthenticationToken bean_1 = authenticated(User.create().withUsername("USER"), authorities);
		UsernameAuthenticationToken bean_2 = authenticated(User.create().withUsername("USER"), authorities);

		assertThat(bean_1).hasSameHashCodeAs(bean_1).doesNotHaveSameHashCodeAs(bean_2);
		assertThat(bean_2).hasSameHashCodeAs(bean_2).doesNotHaveSameHashCodeAs(bean_1);
	}

	@Test
	void testEquals() {
		final var principal = User.create().withUsername("USER");
		final var authorities = List.of(GenericGrantedAuthority.create("ROLE"));

		UsernameAuthenticationToken original_bean = authenticated(principal, authorities);
		UsernameAuthenticationToken equal_bean = authenticated(principal, authorities);
		UsernameAuthenticationToken equal_content_bean = authenticated(User.create().withUsername("USER"), authorities);
		UsernameAuthenticationToken not_equal_bean = authenticated(User.create().withUsername("OTHER_USER"), authorities);

		assertThat(original_bean.equals(original_bean)).isTrue();
		assertThat(original_bean.equals(equal_bean)).isTrue();
		assertThat(original_bean.equals(null)).isFalse();
		assertThat(original_bean.equals(new Object())).isFalse();
		assertThat(original_bean.equals(equal_content_bean)).isFalse();
		assertThat(original_bean.equals(not_equal_bean)).isFalse();
	}
}
