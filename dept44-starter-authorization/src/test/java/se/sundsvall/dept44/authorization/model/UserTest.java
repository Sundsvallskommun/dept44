package se.sundsvall.dept44.authorization.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;

class UserTest {

	@Test
	void assignableFromUserDetails() {
		assertThat(UserDetails.class).isAssignableFrom(User.class);
	}

	@Test
	void testEmptyBean() {
		User bean = User.create();

		assertThat(bean.getAuthorities()).isEmpty();
		assertThat(bean.getPassword()).isNull();
		assertThat(bean.getUsername()).isNull();
		assertThat(bean.isAccountNonExpired()).isTrue();
		assertThat(bean.isAccountNonLocked()).isTrue();
		assertThat(bean.isCredentialsNonExpired()).isTrue();
		assertThat(bean.isEnabled()).isTrue();
	}

	@Test
	void testBuildPatterns() {
		final var userName = "userName";
		final var authority1 = GenericGrantedAuthority.create("ROLE_1");
		final var authority2 = GenericGrantedAuthority.create("ROLE_2", "[\"ACCESS\"]");

		User bean = User.create().withAuthorities(List.of(authority1, authority2)).withUsername(userName);

		assertThat(bean.getAuthorities()).containsExactly(authority1, authority2);
		assertThat(bean.getUsername()).isEqualTo(userName);
		assertThat(bean.getPassword()).isNull();
		assertThat(bean.isAccountNonExpired()).isTrue();
		assertThat(bean.isAccountNonLocked()).isTrue();
		assertThat(bean.isCredentialsNonExpired()).isTrue();
		assertThat(bean.isEnabled()).isTrue();
	}

	@Test
	void testClearAuthoritiesList() {
		final var authority = GenericGrantedAuthority.create("ROLE");
		ArrayList<GenericGrantedAuthority> authorities = new ArrayList<>();
		authorities.add(authority);

		User bean = User.create().withAuthorities(authorities);
		assertThat(bean.getAuthorities()).containsExactly(authority);

		bean = bean.withAuthorities(null);
		assertThat(bean.getAuthorities()).isEmpty();
	}
}
