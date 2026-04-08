package se.sundsvall.dept44.support;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import se.sundsvall.dept44.support.Relation.ResourceIdentifier;

import static com.google.code.beanmatchers.BeanMatchers.hasValidBeanConstructor;
import static com.google.code.beanmatchers.BeanMatchers.hasValidBeanEquals;
import static com.google.code.beanmatchers.BeanMatchers.hasValidBeanHashCode;
import static com.google.code.beanmatchers.BeanMatchers.hasValidGettersAndSettersExcluding;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.MatcherAssert.assertThat;

class RelationTest {

	@Test
	void testBean() {
		assertThat(Relation.class, allOf(
			hasValidBeanConstructor(),
			hasValidGettersAndSettersExcluding("source", "target", "type"),
			hasValidBeanHashCode(),
			hasValidBeanEquals()));
	}

	@Test
	void testResourceIdentifierBean() {
		assertThat(ResourceIdentifier.class, hasValidBeanConstructor());
		assertThat(ResourceIdentifier.class, hasValidGettersAndSettersExcluding("type", "service"));
	}

	private static Stream<Arguments> createResourceIdentifierInvalidArguments() {
		return Stream.of(
			Arguments.of(null, "type", "service", "resourceId must not be null or blank"),
			Arguments.of("", "type", "service", "resourceId must not be null or blank"),
			Arguments.of("  ", "type", "service", "resourceId must not be null or blank"),
			Arguments.of("id", null, "service", "type must not be null or blank"),
			Arguments.of("id", "", "service", "type must not be null or blank"),
			Arguments.of("id", "  ", "service", "type must not be null or blank"),
			Arguments.of("id", "type", null, "service must not be null or blank"),
			Arguments.of("id", "type", "", "service must not be null or blank"),
			Arguments.of("id", "type", "  ", "service must not be null or blank"));
	}

	private static Stream<Arguments> formatValidStreamArguments() {
		return Stream.of(
			Arguments.of(Relation.create("LINK",
				ResourceIdentifier.create("src-id", "case", "myservice", "src-ns"),
				ResourceIdentifier.create("tgt-id", "asset", "otherservice", "tgt-ns")),
				"LINK|src-id;case;myservice;src-ns|tgt-id;asset;otherservice;tgt-ns"),
			Arguments.of(Relation.create("LINK",
				ResourceIdentifier.create("src-id", "case", "myservice", null),
				ResourceIdentifier.create("tgt-id", "asset", "otherservice", null)),
				"LINK|src-id;case;myservice;|tgt-id;asset;otherservice;"),
			Arguments.of(Relation.create("LINK",
				ResourceIdentifier.create("id1", "case", "svc", "ns"),
				ResourceIdentifier.create("id2", "asset", "svc2", "ns2")),
				"LINK|id1;case;svc;ns|id2;asset;svc2;ns2"),
			Arguments.of(Relation.create("LINK",
				ResourceIdentifier.create("id1", "case", "svc", "ns"),
				null),
				"LINK|id1;case;svc;ns|"),
			Arguments.of(Relation.create("LINK",
				null,
				ResourceIdentifier.create("id2", "asset", "svc2", "ns2")),
				"LINK||id2;asset;svc2;ns2"),
			Arguments.of(Relation.create(null, null, null), null),
			Arguments.of(Relation.create("LINK", null, null), null));
	}

	@Test
	void createWithArguments() {
		final var source = ResourceIdentifier.create("src-id", "Case", "My-Service", "src-ns");
		final var target = ResourceIdentifier.create("tgt-id", "Document", "Other-Service", "tgt-ns");

		final var relation = Relation.create("connected", source, target);

		assertThat(relation.getType()).isEqualTo("CONNECTED");
		assertThat(relation.getSource()).isEqualTo(source);
		assertThat(relation.getTarget()).isEqualTo(target);
	}

	@Test
	void createResourceIdentifier() {
		final var identifier = ResourceIdentifier.create("my-id", "Case", "My-Service", "my-ns");

		assertThat(identifier.getResourceId()).isEqualTo("my-id");
		assertThat(identifier.getType()).isEqualTo("case");
		assertThat(identifier.getService()).isEqualTo("myservice");
		assertThat(identifier.getNamespace()).isEqualTo("my-ns");
	}

	@ParameterizedTest
	@MethodSource("createResourceIdentifierInvalidArguments")
	void createResourceIdentifierWithInvalidArguments(String resourceId, String type, String service, String expectedMessage) {
		assertThatThrownBy(() -> ResourceIdentifier.create(resourceId, type, service, "namespace"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage(expectedMessage);
	}

	@Test
	void createResourceIdentifierWithNullNamespace() {
		final var identifier = ResourceIdentifier.create("my-id", "case", "my-service", null);

		assertThat(identifier.getNamespace()).isNull();
	}

	@Test
	void relationTypeIsUpperCased() {
		final var relation = Relation.create("connected", null, null);
		assertThat(relation.getType()).isEqualTo("CONNECTED");

		relation.setType("linked");
		assertThat(relation.getType()).isEqualTo("LINKED");

		relation.setType(null);
		assertThat(relation.getType()).isNull();
	}

	@Test
	void resourceIdentifierTypeIsLowerCased() {
		final var identifier = ResourceIdentifier.create("id", "CASE", "service", "namespace");
		assertThat(identifier.getType()).isEqualTo("case");

		identifier.setType("Document");
		assertThat(identifier.getType()).isEqualTo("document");

		identifier.setType(null);
		assertThat(identifier.getType()).isNull();
	}

	@Test
	void resourceIdentifierServiceIsLowerCased() {
		final var identifier = ResourceIdentifier.create("id", "type", "My-service", null);
		assertThat(identifier.getService()).isEqualTo("myservice");

		identifier.setService("OTHER-SERVICE");
		assertThat(identifier.getService()).isEqualTo("otherservice");

		identifier.setService(null);
		assertThat(identifier.getService()).isNull();
	}

	@Test
	void withMethods() {
		final var source = new ResourceIdentifier()
			.withResourceId("src-id")
			.withType("case")
			.withService("src-service")
			.withNamespace("src-namespace");

		final var target = new ResourceIdentifier()
			.withResourceId("tgt-id")
			.withType("document")
			.withService("tgt-service")
			.withNamespace("tgt-namespace");

		final var relation = new Relation()
			.withType("CONNECTED")
			.withSource(source)
			.withTarget(target);

		assertThat(relation.getType()).isEqualTo("CONNECTED");
		assertThat(relation.getSource()).isEqualTo(source);
		assertThat(relation.getTarget()).isEqualTo(target);
		assertThat(relation.getSource().getResourceId()).isEqualTo("src-id");
		assertThat(relation.getSource().getType()).isEqualTo("case");
		assertThat(relation.getSource().getService()).isEqualTo("srcservice");
		assertThat(relation.getSource().getNamespace()).isEqualTo("src-namespace");
		assertThat(relation.getTarget().getResourceId()).isEqualTo("tgt-id");
		assertThat(relation.getTarget().getType()).isEqualTo("document");
		assertThat(relation.getTarget().getService()).isEqualTo("tgtservice");
		assertThat(relation.getTarget().getNamespace()).isEqualTo("tgt-namespace");
	}

	@Test
	void relationEqual() {
		final var relation1 = Relation.create("CONNECTED",
			ResourceIdentifier.create("id", "case", "svc", "ns"),
			ResourceIdentifier.create("id", "case", "svc", "ns"));

		final var relation2 = Relation.create("CONNECTED",
			ResourceIdentifier.create("id", "case", "svc", "ns"),
			ResourceIdentifier.create("id", "case", "svc", "ns"));

		assertThat(relation1).isEqualTo(relation2);
		assertThat(relation1.hashCode()).hasSameHashCodeAs(relation2.hashCode());
	}

	@Test
	void relationNotEqual() {
		final var relation1 = Relation.create("CONNECTED",
			ResourceIdentifier.create("id1", "case", "svc", null),
			ResourceIdentifier.create("id2", "doc", "svc", null));

		final var relation2 = Relation.create("LINK",
			ResourceIdentifier.create("id1", "case", "svc", null),
			ResourceIdentifier.create("id2", "doc", "svc", null));

		assertThat(relation1).isNotEqualTo(relation2);
	}

	@Test
	void resourceIdentifierEqual() {
		final var id1 = ResourceIdentifier.create("id", "case", "svc", "ns");
		final var id2 = ResourceIdentifier.create("id", "case", "svc", "ns");

		assertThat(id1).isEqualTo(id2);
		assertThat(id1.hashCode()).hasSameHashCodeAs(id2.hashCode());
	}

	@ParameterizedTest
	@MethodSource("formatValidStreamArguments")
	void formatRelation(Relation input, String expected) {
		assertThat(input.formatRelation()).isEqualTo(expected);
	}

	@ParameterizedTest
	@MethodSource("parseValidStreamArguments")
	void parseValidFormatString(String input, Relation expected) {
		assertThat(Relation.parseRelation(input)).isEqualTo(expected);
	}

	@ParameterizedTest
	@MethodSource("parseInvalidStreamArguments")
	void parseInvalidFormatString(String input) {
		assertThatThrownBy(() -> Relation.parseRelation(input))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void resourceIdentifierNotEqual() {
		final var id1 = ResourceIdentifier.create("id1", "case", "svc", null);
		final var id2 = ResourceIdentifier.create("id2", "case", "svc", null);

		assertThat(id1).isNotEqualTo(id2);
	}

	private static Stream<Arguments> parseValidStreamArguments() {
		return Stream.of(
			Arguments.of("LINK|src-id;case;MY-SERVICE;src-ns|tgt-id;asset;OTHER_SERVICE;tgt-ns",
				Relation.create("LINK",
					ResourceIdentifier.create("src-id", "case", "myservice", "src-ns"),
					ResourceIdentifier.create("tgt-id", "asset", "otherservice", "tgt-ns"))),
			Arguments.of("LINK|src-id;case;myservice;|tgt-id;asset;otherservice;",
				Relation.create("LINK",
					ResourceIdentifier.create("src-id", "case", "myservice", null),
					ResourceIdentifier.create("tgt-id", "asset", "otherservice", null))),
			Arguments.of("TYPE|src-id;case;myservice;src-ns|tgt-id;asset;otherservice;tgt-ns",
				Relation.create("TYPE",
					ResourceIdentifier.create("src-id", "case", "myservice", "src-ns"),
					ResourceIdentifier.create("tgt-id", "asset", "otherservice", "tgt-ns"))),
			Arguments.of("TYPE|id1;case;svc;ns|id2;asset;svc2;ns2",
				Relation.create("TYPE",
					ResourceIdentifier.create("id1", "case", "svc", "ns"),
					ResourceIdentifier.create("id2", "asset", "svc2", "ns2"))));
	}

	private static Stream<String> parseInvalidStreamArguments() {
		return Stream.of(
			null,
			"",
			"  ",
			"LINK|only-one-section",
			"LINK|src-id;case|tgt-id;asset;svc;ns",
			"no-separators-at-all");
	}

	@Test
	void testToString() {
		final var relation = Relation.create("LINK",
			ResourceIdentifier.create("src-id", "case", "svc", "ns"),
			ResourceIdentifier.create("tgt-id", "asset", "svc2", null));

		assertThat(relation.toString()).contains("Relation", "type=LINK", "source=", "target=");
		assertThat(relation.getSource().toString()).contains("ResourceIdentifier", "resourceId=src-id", "type=case", "service=svc", "namespace=ns");
		assertThat(relation.getTarget().toString()).contains("ResourceIdentifier", "resourceId=tgt-id", "type=asset", "service=svc2");
	}
}
