package com.lotus.bixi.common.security.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordPolicyValidatorTest {

	@ParameterizedTest
	@NullAndEmptySource
	void nullOrEmptyPasswordCannotBeStrong(String password) {
		assertThat(PasswordPolicyValidator.isStrong(password)).isFalse();
	}

	@ParameterizedTest
	@ValueSource(strings = { "Ab1@", "Aa1!" })
	void shortPasswordsCannotBeStrong(String password) {
		assertThat(PasswordPolicyValidator.isStrong(password)).isFalse();
	}

	@Test
	void passwordWithoutUppercaseCannotBeStrong() {
		assertThat(PasswordPolicyValidator.isStrong("abcdef1@")).isFalse();
	}

	@Test
	void passwordWithoutLowercaseCannotBeStrong() {
		assertThat(PasswordPolicyValidator.isStrong("ABCDEF1@")).isFalse();
	}

	@Test
	void passwordWithoutDigitCannotBeStrong() {
		assertThat(PasswordPolicyValidator.isStrong("Abcdefg@")).isFalse();
	}

	@Test
	void passwordWithoutSpecialCharCannotBeStrong() {
		assertThat(PasswordPolicyValidator.isStrong("Abcdefg1")).isFalse();
	}

	@Test
	void passwordWithAllCategoriesAndMinLengthIsStrong() {
		assertThat(PasswordPolicyValidator.isStrong("Abcdef1@")).isTrue();
	}

	@Test
	void longerPasswordWithAllCategoriesIsStrong() {
		assertThat(PasswordPolicyValidator.isStrong("MyStr0ng!Pass#99")).isTrue();
	}

}
