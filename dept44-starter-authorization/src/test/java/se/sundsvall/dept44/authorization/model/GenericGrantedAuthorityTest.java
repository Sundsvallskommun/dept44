package se.sundsvall.dept44.authorization.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

class GenericGrantedAuthorityTest {
	private static final String ROLE = "ROLE";
	private static final String ACCESS_JSON = "[\"ACCESS_1\",\"ACCESS_2\"]";

	@Test
	void assignableFromGrantedAuthority() {
		assertThat(GrantedAuthority.class).isAssignableFrom(GenericGrantedAuthority.class);
	}

	@Test
	void createPatternWithRole() {
		GenericGrantedAuthority bean = GenericGrantedAuthority.create(ROLE);
		assertThat(bean.getAuthority()).isEqualTo(ROLE);
		assertThat(bean.getAccesses()).isNull();

		assertThat(bean).hasToString("GenericGrantedAuthority [role=ROLE, accesses=null]");
	}

	@Test
	void createPatternWithRoleAndAccesses() {
		GenericGrantedAuthority bean = GenericGrantedAuthority.create(ROLE, ACCESS_JSON);

		assertThat(bean.getAuthority()).isEqualTo(ROLE);
		assertThat(bean.getAccesses().jsonString()).isEqualTo(ACCESS_JSON);
		assertThat(bean).hasToString("GenericGrantedAuthority [role=ROLE, accesses=[\"ACCESS_1\",\"ACCESS_2\"]]");
	}

	@Test
	void hasRoleAuthority() {
		final var otherRole = "OTHER_ROLE";

		GenericGrantedAuthority bean = GenericGrantedAuthority.create(ROLE, ACCESS_JSON);

		assertThat(bean.hasAuthority(ROLE)).isTrue();
		assertThat(bean.hasAuthority(otherRole)).isFalse();
	}

	@Test
	void hasAccessAuthority() {
		final var otherRole = "OTHER_ROLE";

		GenericGrantedAuthority bean = GenericGrantedAuthority.create(ROLE, ACCESS_JSON);

		assertThat(bean.hasAuthority(ROLE, "$.[?(@ ==\"ACCESS_1\")]")).isTrue();
		assertThat(bean.hasAuthority(otherRole, "$.[?(@ ==\"ACCESS_1\")]")).isFalse();
		assertThat(bean.hasAuthority(ROLE, "$.[?(@ ==\"ACCESS_2\")]")).isTrue();
		assertThat(bean.hasAuthority(ROLE, "$.[?(@ ==\"ACCESS_3\")]")).isFalse();
		assertThat(bean.hasAuthority(ROLE, "$.[*]")).isTrue();
		assertThat(bean.hasAuthority(otherRole, "$.[*]")).isFalse();
	}

	@Test
	void hasAccessAuthorityOnNullAccesses() {
		GenericGrantedAuthority bean = GenericGrantedAuthority.create(ROLE);

		assertThat(bean.hasAuthority(ROLE, "$.[?(@ ==\"ACCESS_1\")]")).isFalse();
		assertThat(bean.hasAuthority(ROLE, "$.[*]")).isFalse();
	}

	@Test
	void invalidJsonPath() {
		final var invalidJsonPath = "$@\"";

		GenericGrantedAuthority bean = GenericGrantedAuthority.create(ROLE, ACCESS_JSON);

		final var exception = assertThrows(IllegalArgumentException.class, () -> bean.hasAuthority(ROLE, invalidJsonPath));
		assertThat(exception.getMessage()).isEqualTo("Provided jsonpath '$@\"' is invalid");
	}

	@Test
	void testHashCodeWithRole() {
		GenericGrantedAuthority bean1 = GenericGrantedAuthority.create("ROLE_1");
		GenericGrantedAuthority bean2 = GenericGrantedAuthority.create("ROLE_2");

		assertThat(bean1).hasSameHashCodeAs(bean1).doesNotHaveSameHashCodeAs(bean2);
		assertThat(bean2).hasSameHashCodeAs(bean2).doesNotHaveSameHashCodeAs(bean1);
	}

	@Test
	void testHashCodeWithRoleAndAccesses() {
		GenericGrantedAuthority bean1 = GenericGrantedAuthority.create("ROLE_1", ACCESS_JSON);
		GenericGrantedAuthority bean2 = GenericGrantedAuthority.create("ROLE_2", ACCESS_JSON);

		assertThat(bean1).hasSameHashCodeAs(bean1).doesNotHaveSameHashCodeAs(bean2);
		assertThat(bean2).hasSameHashCodeAs(bean2).doesNotHaveSameHashCodeAs(bean1);
	}

	@Test
	void testEquals() {
		GenericGrantedAuthority originalBean = GenericGrantedAuthority.create(ROLE, ACCESS_JSON);
		GenericGrantedAuthority equalContentBean = GenericGrantedAuthority.create(ROLE, ACCESS_JSON);
		GenericGrantedAuthority notEqualBean = GenericGrantedAuthority.create(ROLE, ACCESS_JSON);

		assertThat(originalBean.equals(originalBean)).isTrue();
		assertThat(originalBean.equals(null)).isFalse();
		assertThat(originalBean.equals(new Object())).isFalse();
		assertThat(originalBean.equals(equalContentBean)).isFalse();
		assertThat(originalBean.equals(notEqualBean)).isFalse();
	}
}
