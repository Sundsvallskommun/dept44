package se.sundsvall.dept44.authorization.model;

import static org.springframework.util.CollectionUtils.isEmpty;

import java.util.ArrayList;
import java.util.Collection;

import org.springframework.security.core.userdetails.UserDetails;

public class User implements UserDetails {
	private static final long serialVersionUID = -305398618677826839L;

	private String username;
	private Collection<GenericGrantedAuthority> authorities = new ArrayList<>();

	public static User create() {
		return new User();
	}

	private User() {}

	public User withUsername(String username) {
		this.username = username;
		return this;
	}

	public User withAuthorities(Collection<GenericGrantedAuthority> authorities) {
		if (isEmpty(authorities)) {
			this.authorities.clear();
		} else {
			this.authorities = authorities;
		}
		return this;
	}

	@Override
	public Collection<GenericGrantedAuthority> getAuthorities() {
		return this.authorities;
	}

	@Override
	public String getPassword() {
		return null;
	}

	@Override
	public String getUsername() {
		return username;
	}

	@Override
	public boolean isAccountNonExpired() {
		return true;
	}

	@Override
	public boolean isAccountNonLocked() {
		return true;
	}

	@Override
	public boolean isCredentialsNonExpired() {
		return true;
	}

	@Override
	public boolean isEnabled() {
		return true;
	}
}
