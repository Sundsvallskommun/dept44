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
		GenericGrantedAuthority bean_1 = GenericGrantedAuthority.create("ROLE_1");
		GenericGrantedAuthority bean_2 = GenericGrantedAuthority.create("ROLE_2");

		assertThat(bean_1).hasSameHashCodeAs(bean_1).doesNotHaveSameHashCodeAs(bean_2);
		assertThat(bean_2).hasSameHashCodeAs(bean_2).doesNotHaveSameHashCodeAs(bean_1);
	}

	@Test
	void testHashCodeWithRoleAndAccesses() {
		GenericGrantedAuthority bean_1 = GenericGrantedAuthority.create("ROLE_1", ACCESS_JSON);
		GenericGrantedAuthority bean_2 = GenericGrantedAuthority.create("ROLE_2", ACCESS_JSON);

		assertThat(bean_1).hasSameHashCodeAs(bean_1).doesNotHaveSameHashCodeAs(bean_2);
		assertThat(bean_2).hasSameHashCodeAs(bean_2).doesNotHaveSameHashCodeAs(bean_1);
	}

	@Test
	void testEquals() {
		GenericGrantedAuthority original_bean = GenericGrantedAuthority.create(ROLE, ACCESS_JSON);
		GenericGrantedAuthority equal_content_bean = GenericGrantedAuthority.create(ROLE, ACCESS_JSON);
		GenericGrantedAuthority not_equal_bean = GenericGrantedAuthority.create(ROLE, ACCESS_JSON);

		assertThat(original_bean.equals(original_bean)).isTrue();
		assertThat(original_bean.equals(null)).isFalse();
		assertThat(original_bean.equals(new Object())).isFalse();
		assertThat(original_bean.equals(equal_content_bean)).isFalse();
		assertThat(original_bean.equals(not_equal_bean)).isFalse();
	}
}
