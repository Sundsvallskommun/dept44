package se.sundsvall.dept44.support;

import org.junit.jupiter.api.Test;
import se.sundsvall.dept44.support.Relation.ResourceIdentifier;

import static com.google.code.beanmatchers.BeanMatchers.hasValidBeanConstructor;
import static com.google.code.beanmatchers.BeanMatchers.hasValidBeanEquals;
import static com.google.code.beanmatchers.BeanMatchers.hasValidBeanHashCode;
import static com.google.code.beanmatchers.BeanMatchers.hasValidGettersAndSettersExcluding;
import static org.assertj.core.api.Assertions.assertThat;
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

	@Test
	void testCreate() {
		final var relation = Relation.create();

		assertThat(relation).isNotNull();
		assertThat(relation.getType()).isNull();
		assertThat(relation.getSource()).isNull();
		assertThat(relation.getTarget()).isNull();
	}

	@Test
	void testCreateWithArguments() {
		final var source = ResourceIdentifier.create("src-id", "Case", "My-Service", "src-ns");
		final var target = ResourceIdentifier.create("tgt-id", "Document", "Other-Service", "tgt-ns");

		final var relation = Relation.create("connected", source, target);

		assertThat(relation.getType()).isEqualTo("CONNECTED");
		assertThat(relation.getSource()).isEqualTo(source);
		assertThat(relation.getTarget()).isEqualTo(target);
	}

	@Test
	void testResourceIdentifierCreate() {
		final var identifier = ResourceIdentifier.create();

		assertThat(identifier).isNotNull();
		assertThat(identifier.getResourceId()).isNull();
		assertThat(identifier.getType()).isNull();
		assertThat(identifier.getService()).isNull();
		assertThat(identifier.getNamespace()).isNull();
	}

	@Test
	void testResourceIdentifierCreateWithArguments() {
		final var identifier = ResourceIdentifier.create("my-id", "Case", "My-Service", "my-ns");

		assertThat(identifier.getResourceId()).isEqualTo("my-id");
		assertThat(identifier.getType()).isEqualTo("case");
		assertThat(identifier.getService()).isEqualTo("myservice");
		assertThat(identifier.getNamespace()).isEqualTo("my-ns");
	}

	@Test
	void testResourceIdentifierCreateWithNullNamespace() {
		final var identifier = ResourceIdentifier.create("my-id", "case", "my-service", null);

		assertThat(identifier.getNamespace()).isNull();
	}

	@Test
	void testRelationTypeIsUpperCased() {
		final var relation = Relation.create().withType("connected");
		assertThat(relation.getType()).isEqualTo("CONNECTED");

		relation.setType("linked");
		assertThat(relation.getType()).isEqualTo("LINKED");

		relation.setType(null);
		assertThat(relation.getType()).isNull();
	}

	@Test
	void testResourceIdentifierTypeIsLowerCased() {
		final var identifier = ResourceIdentifier.create().withType("CASE");
		assertThat(identifier.getType()).isEqualTo("case");

		identifier.setType("Document");
		assertThat(identifier.getType()).isEqualTo("document");

		identifier.setType(null);
		assertThat(identifier.getType()).isNull();
	}

	@Test
	void testResourceIdentifierServiceIsLowerCased() {
		final var identifier = ResourceIdentifier.create().withService("My-Service");
		assertThat(identifier.getService()).isEqualTo("myservice");

		identifier.setService("OTHER-SERVICE");
		assertThat(identifier.getService()).isEqualTo("otherservice");

		identifier.setService(null);
		assertThat(identifier.getService()).isNull();
	}

	@Test
	void testWithMethods() {
		final var source = ResourceIdentifier.create()
			.withResourceId("src-id")
			.withType("case")
			.withService("src-service")
			.withNamespace("src-namespace");

		final var target = ResourceIdentifier.create()
			.withResourceId("tgt-id")
			.withType("document")
			.withService("tgt-service")
			.withNamespace("tgt-namespace");

		final var relation = Relation.create()
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
	void testWithMethodsWithoutNamespace() {
		final var source = ResourceIdentifier.create()
			.withResourceId("src-id")
			.withType("case")
			.withService("srcservice");

		final var target = ResourceIdentifier.create()
			.withResourceId("tgt-id")
			.withType("document")
			.withService("tgtservice");

		final var relation = Relation.create()
			.withType("LINKED")
			.withSource(source)
			.withTarget(target);

		assertThat(relation.getSource().getNamespace()).isNull();
		assertThat(relation.getTarget().getNamespace()).isNull();
	}

	@Test
	void testRelationEqual() {
		final var relation1 = Relation.create()
			.withType("CONNECTED")
			.withSource(ResourceIdentifier.create().withResourceId("id").withType("case").withService("svc").withNamespace("ns"))
			.withTarget(ResourceIdentifier.create().withResourceId("id2").withType("doc").withService("svc2").withNamespace("ns2"));

		final var relation2 = Relation.create()
			.withType("CONNECTED")
			.withSource(ResourceIdentifier.create().withResourceId("id").withType("case").withService("svc").withNamespace("ns"))
			.withTarget(ResourceIdentifier.create().withResourceId("id2").withType("doc").withService("svc2").withNamespace("ns2"));

		assertThat(relation1).isEqualTo(relation2);
		assertThat(relation1.hashCode()).hasSameHashCodeAs(relation2.hashCode());
	}

	@Test
	void testRelationNotEqual() {
		final var relation1 = Relation.create()
			.withType("CONNECTED")
			.withSource(ResourceIdentifier.create().withResourceId("id1").withType("case").withService("svc"))
			.withTarget(ResourceIdentifier.create().withResourceId("id2").withType("doc").withService("svc"));

		final var relation2 = Relation.create()
			.withType("LINKED")
			.withSource(ResourceIdentifier.create().withResourceId("id1").withType("case").withService("svc"))
			.withTarget(ResourceIdentifier.create().withResourceId("id2").withType("doc").withService("svc"));

		assertThat(relation1).isNotEqualTo(relation2);
	}

	@Test
	void testResourceIdentifierEqual() {
		final var id1 = ResourceIdentifier.create().withResourceId("id").withType("case").withService("svc").withNamespace("ns");
		final var id2 = ResourceIdentifier.create().withResourceId("id").withType("case").withService("svc").withNamespace("ns");

		assertThat(id1).isEqualTo(id2);
		assertThat(id1.hashCode()).hasSameHashCodeAs(id2.hashCode());
	}

	@Test
	void testResourceIdentifierNotEqual() {
		final var id1 = ResourceIdentifier.create().withResourceId("id1").withType("case").withService("svc");
		final var id2 = ResourceIdentifier.create().withResourceId("id2").withType("case").withService("svc");

		assertThat(id1).isNotEqualTo(id2);
	}

	@Test
	void testToString() {
		final var relation = Relation.create()
			.withType("CONNECTED")
			.withSource(ResourceIdentifier.create().withResourceId("src-id").withType("case").withService("svc").withNamespace("ns"))
			.withTarget(ResourceIdentifier.create().withResourceId("tgt-id").withType("doc").withService("svc2"));

		assertThat(relation.toString()).contains("Relation", "type=CONNECTED", "source=", "target=");
		assertThat(relation.getSource().toString()).contains("ResourceIdentifier", "resourceId=src-id", "type=case", "service=svc", "namespace=ns");
		assertThat(relation.getTarget().toString()).contains("ResourceIdentifier", "resourceId=tgt-id", "type=doc", "service=svc2");
	}
}
