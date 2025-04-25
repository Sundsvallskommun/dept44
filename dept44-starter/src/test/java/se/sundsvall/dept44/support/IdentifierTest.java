package se.sundsvall.dept44.support;

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

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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
	@MethodSource("provideValidIdentifierStrings")
	void parseString(String input, Identifier expected) {
		assertThat(Identifier.parse(input))
			.isNotNull()
			.isEqualTo(expected);
	}

	@ParameterizedTest
	@MethodSource("provideInvalidIdentifierStrings")
	void parseStringInvalid(String input) {
		assertThat(Identifier.parse(input)).isNull();
	}

	private static Stream<Arguments> provideValidIdentifierStrings() {
		return Stream.of(
			Arguments.of("410152ff-0ede-4d4b-916b-d91436121ef5;type=partyId", Identifier.create().withType(PARTY_ID).withTypeString("PARTY_ID").withValue("410152ff-0ede-4d4b-916b-d91436121ef5")),
			Arguments.of("410152ff-0ede-4d4b-916b-d91436121ef5; type=partyId", Identifier.create().withType(PARTY_ID).withTypeString("PARTY_ID").withValue("410152ff-0ede-4d4b-916b-d91436121ef5")),
			Arguments.of(" 410152ff-0ede-4d4b-916b-d91436121ef5 ; type = partyId ", Identifier.create().withType(PARTY_ID).withTypeString("PARTY_ID").withValue("410152ff-0ede-4d4b-916b-d91436121ef5")),
			Arguments.of("type=partyId;410152ff-0ede-4d4b-916b-d91436121ef5", Identifier.create().withType(PARTY_ID).withTypeString("PARTY_ID").withValue("410152ff-0ede-4d4b-916b-d91436121ef5")),
			Arguments.of("TYPE=partyId; 410152ff-0ede-4d4b-916b-d91436121ef5", Identifier.create().withType(PARTY_ID).withTypeString("PARTY_ID").withValue("410152ff-0ede-4d4b-916b-d91436121ef5")),
			Arguments.of(" type = partyId ; 410152ff-0ede-4d4b-916b-d91436121ef5 ", Identifier.create().withType(PARTY_ID).withTypeString("PARTY_ID").withValue("410152ff-0ede-4d4b-916b-d91436121ef5")),
			Arguments.of("type=adAccount;joe01doe", Identifier.create().withType(AD_ACCOUNT).withTypeString("AD_ACCOUNT").withValue("joe01doe")),
			Arguments.of("type=adAccount; joe01doe", Identifier.create().withType(AD_ACCOUNT).withTypeString("AD_ACCOUNT").withValue("joe01doe")),
			Arguments.of("TYPE=adAccount; joe01doe", Identifier.create().withType(AD_ACCOUNT).withTypeString("AD_ACCOUNT").withValue("joe01doe")),
			Arguments.of(" type = adAccount ; joe01doe ", Identifier.create().withType(AD_ACCOUNT).withTypeString("AD_ACCOUNT").withValue("joe01doe")),
			Arguments.of("xyz123;type=someCustomType", Identifier.create().withType(CUSTOM).withTypeString("someCustomType").withValue("xyz123")),
			Arguments.of("xyz123; type=someCustomType", Identifier.create().withType(CUSTOM).withTypeString("someCustomType").withValue("xyz123")),
			Arguments.of(" xyz123 ; type = someCustomType ", Identifier.create().withType(CUSTOM).withTypeString("someCustomType").withValue("xyz123")),
			Arguments.of(" xyz123 ; TYPE = someCustomType ", Identifier.create().withType(CUSTOM).withTypeString("someCustomType").withValue("xyz123")),
			Arguments.of("type=someCustomType;xyz123", Identifier.create().withType(CUSTOM).withTypeString("someCustomType").withValue("xyz123")),
			Arguments.of(" type = someCustomType ; xyz123 ", Identifier.create().withType(CUSTOM).withTypeString("someCustomType").withValue("xyz123")),
			Arguments.of(" TYPE = someCustomType ; xyz123 ", Identifier.create().withType(CUSTOM).withTypeString("someCustomType").withValue("xyz123")),
			Arguments.of("type=someCustomType; xyz123", Identifier.create().withType(CUSTOM).withTypeString("someCustomType").withValue("xyz123")),
			Arguments.of("type=custom; xyz123", Identifier.create().withType(CUSTOM).withTypeString("CUSTOM").withValue("xyz123")));
	}

	private static Stream<String> provideInvalidIdentifierStrings() {
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
