package se.sundsvall.dept44.support;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static com.google.code.beanmatchers.BeanMatchers.hasValidBeanConstructor;
import static com.google.code.beanmatchers.BeanMatchers.hasValidBeanEquals;
import static com.google.code.beanmatchers.BeanMatchers.hasValidBeanHashCode;
import static com.google.code.beanmatchers.BeanMatchers.hasValidBeanToString;
import static com.google.code.beanmatchers.BeanMatchers.hasValidGettersAndSetters;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static se.sundsvall.dept44.support.Identifier.Type.AD_ACCOUNT;
import static se.sundsvall.dept44.support.Identifier.Type.CUSTOM;
import static se.sundsvall.dept44.support.Identifier.Type.PARTY_ID;

class IdentifierTest {

	@Test
	void testBean() {
		assertThat(Identifier.class, allOf(
			hasValidBeanConstructor(),
			hasValidGettersAndSetters(),
			hasValidBeanHashCode(),
			hasValidBeanEquals(),
			hasValidBeanToString()));
	}

	@ParameterizedTest
	@MethodSource("parseValidStreamArguments")
	void parseString(String input, Identifier expected) {
		assertThat(Identifier.parse(input))
			.isNotNull()
			.isEqualTo(expected);
	}

	@ParameterizedTest
	@MethodSource("parseInvalidStreamArguments")
	void parseStringInvalid(String input) {
		assertThat(Identifier.parse(input)).isNull();
	}

	@ParameterizedTest
	@MethodSource("toHeaderValueStreamArguments")
	void toHeaderValue(Identifier identifier, String expectedHeaderValue) {

		// Act
		final var result = identifier.toHeaderValue();

		// Assert
		assertThat(result).isEqualTo(expectedHeaderValue);
	}

	private static Stream<Arguments> toHeaderValueStreamArguments() {
		return Stream.of(
			Arguments.of(Identifier.create().withType(AD_ACCOUNT).withValue("joe01doe"), "joe01doe; type=adAccount"),
			Arguments.of(Identifier.create().withType(PARTY_ID).withValue("98c7b451-a14a-4f9f-91da-8834ba01eb81"), "98c7b451-a14a-4f9f-91da-8834ba01eb81; type=partyId"),
			Arguments.of(Identifier.create().withType(CUSTOM).withValue("joe01doe"), "joe01doe; type=custom"),
			Arguments.of(Identifier.parse("joe01doe; type=someCustomType"), "joe01doe; type=someCustomType"),
			Arguments.of(Identifier.create(), null),
			Arguments.of(Identifier.create().withType(null).withValue("joe01doe"), null),
			Arguments.of(Identifier.create().withType(AD_ACCOUNT).withValue(null), null));
	}

	private static Stream<Arguments> parseValidStreamArguments() {
		return Stream.of(
			Arguments.of("410152ff-0ede-4d4b-916b-d91436121ef5;type=partyId", Identifier.create().withType(PARTY_ID).withTypeString("partyId").withValue("410152ff-0ede-4d4b-916b-d91436121ef5")),
			Arguments.of("410152ff-0ede-4d4b-916b-d91436121ef5; type=partyId", Identifier.create().withType(PARTY_ID).withTypeString("partyId").withValue("410152ff-0ede-4d4b-916b-d91436121ef5")),
			Arguments.of(" 410152ff-0ede-4d4b-916b-d91436121ef5 ; type = partyId ", Identifier.create().withType(PARTY_ID).withTypeString("partyId").withValue("410152ff-0ede-4d4b-916b-d91436121ef5")),
			Arguments.of("type=partyId;410152ff-0ede-4d4b-916b-d91436121ef5", Identifier.create().withType(PARTY_ID).withTypeString("partyId").withValue("410152ff-0ede-4d4b-916b-d91436121ef5")),
			Arguments.of("TYPE=partyId; 410152ff-0ede-4d4b-916b-d91436121ef5", Identifier.create().withType(PARTY_ID).withTypeString("partyId").withValue("410152ff-0ede-4d4b-916b-d91436121ef5")),
			Arguments.of(" type = partyId ; 410152ff-0ede-4d4b-916b-d91436121ef5 ", Identifier.create().withType(PARTY_ID).withTypeString("partyId").withValue("410152ff-0ede-4d4b-916b-d91436121ef5")),
			Arguments.of("type=adAccount;joe01doe", Identifier.create().withType(AD_ACCOUNT).withTypeString("adAccount").withValue("joe01doe")),
			Arguments.of("type=adAccount; joe01doe", Identifier.create().withType(AD_ACCOUNT).withTypeString("adAccount").withValue("joe01doe")),
			Arguments.of("TYPE=adAccount; joe01doe", Identifier.create().withType(AD_ACCOUNT).withTypeString("adAccount").withValue("joe01doe")),
			Arguments.of(" type = adAccount ; joe01doe ", Identifier.create().withType(AD_ACCOUNT).withTypeString("adAccount").withValue("joe01doe")),
			Arguments.of("xyz123;type=someCustomType", Identifier.create().withType(CUSTOM).withTypeString("someCustomType").withValue("xyz123")),
			Arguments.of("xyz123; type=someCustomType", Identifier.create().withType(CUSTOM).withTypeString("someCustomType").withValue("xyz123")),
			Arguments.of(" xyz123 ; type = someCustomType ", Identifier.create().withType(CUSTOM).withTypeString("someCustomType").withValue("xyz123")),
			Arguments.of(" xyz123 ; TYPE = someCustomType ", Identifier.create().withType(CUSTOM).withTypeString("someCustomType").withValue("xyz123")),
			Arguments.of("type=someCustomType;xyz123", Identifier.create().withType(CUSTOM).withTypeString("someCustomType").withValue("xyz123")),
			Arguments.of(" type = someCustomType ; xyz123 ", Identifier.create().withType(CUSTOM).withTypeString("someCustomType").withValue("xyz123")),
			Arguments.of(" TYPE = someCustomType ; xyz123 ", Identifier.create().withType(CUSTOM).withTypeString("someCustomType").withValue("xyz123")),
			Arguments.of("type=someCustomType; xyz123", Identifier.create().withType(CUSTOM).withTypeString("someCustomType").withValue("xyz123")),
			Arguments.of("type=custom; xyz123", Identifier.create().withType(CUSTOM).withTypeString("custom").withValue("xyz123")));
	}

	private static Stream<String> parseInvalidStreamArguments() {
		return Stream.of(
			"",
			null,
			";",
			" ; ",
			";type=",
			"xyz;type=",
			" ;type=partyId",
			" ; type = ",
			"invalid",
			"invalid;",
			"invalid;theType=partyId");
	}
}
