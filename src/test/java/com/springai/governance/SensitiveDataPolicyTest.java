package com.springai.governance;

import com.springai.governance.SensitiveDataPolicy.Action;
import com.springai.governance.SensitiveDataPolicy.Category;
import com.springai.governance.SensitiveDataPolicy.Decision;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveDataPolicyTest {

    private final SensitiveDataPolicy policy = new SensitiveDataPolicy();

    @ParameterizedTest
    @ValueSource(strings = {
            "my password is hunter22",
            "Password: Tr0ub4dor",
            "pwd=letmein99",
            "my otp is 482913",
            "The one-time code 123456 just arrived",
            "verification code: 99887766",
            "cvv 123",
            "security code is 4821",
            "here is my key sk-abcdefghijklmnopqrstuvwxyz123456",
            "Authorization: Bearer abcdefghijklmnopqrstuvwxyz0123456789",
            "token eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.abcDEF123",
            "aws id AKIAIOSFODNN7EXAMPLE"
    })
    void credentialsAndSecretsWithAValueAreRejected(String text) {
        Decision decision = policy.evaluate(text);

        assertThat(decision.action()).isEqualTo(Action.REJECT);
        assertThat(decision.categories()).isNotEmpty();
        assertThat(decision.text()).isNull();   // the rejected text is never handed on
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "my password is hunter22      | PASSWORD",
            "my otp is 482913              | ONE_TIME_CODE",
            "cvv 123                        | CARD_SECURITY_CODE",
            "key sk-abcdefghijklmnopqrstuvwxyz123456 | SECRET_TOKEN"
    })
    void eachKindIsReportedByCategory(String text, Category expected) {
        assertThat(policy.evaluate(text).categories()).contains(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "I forgot my password, how do I reset it?",
            "Do you ask for an OTP when I log in?",
            "Where is my order ORD-1001?",
            "My order number is 1234567890123 and it is late",   // 13 digits but fails the Luhn check
            "Please call me back tomorrow",
            "The CVV is printed on the back of the card, right?"
    })
    void ordinaryQuestionsThatMerelyMentionTheWordsAreAllowed(String text) {
        Decision decision = policy.evaluate(text);

        assertThat(decision.action()).isEqualTo(Action.ALLOW);
        assertThat(decision.text()).isEqualTo(text);
    }

    @Test
    void aValidCardNumberIsRedactedToItsLastFourDigits() {
        Decision decision = policy.evaluate("Charge 4111 1111 1111 1111 please");

        assertThat(decision.action()).isEqualTo(Action.REDACT);
        assertThat(decision.categories()).containsExactly(Category.CARD_NUMBER);
        assertThat(decision.text()).isEqualTo("Charge [card ending 1111] please");
        assertThat(decision.text()).doesNotContain("4111");
    }

    @Test
    void cardNumbersWithDashesAreRedactedToo() {
        Decision decision = policy.evaluate("card 5555-5555-5555-4444 expires soon");

        assertThat(decision.action()).isEqualTo(Action.REDACT);
        assertThat(decision.text()).isEqualTo("card [card ending 4444] expires soon");
    }

    @Test
    void rejectionWinsOverRedaction() {
        Decision decision = policy.evaluate("card 4111 1111 1111 1111 and my otp is 482913");

        assertThat(decision.action()).isEqualTo(Action.REJECT);
        assertThat(decision.text()).isNull();
    }

    @Test
    void aDecisionNeverCarriesTheMatchedValue() {
        Decision decision = policy.evaluate("my otp is 482913");

        assertThat(decision.toString()).doesNotContain("482913");
    }

    @Test
    void emptyAndNullInputAreAllowed() {
        assertThat(policy.evaluate("").action()).isEqualTo(Action.ALLOW);
        assertThat(policy.evaluate(null).action()).isEqualTo(Action.ALLOW);
    }

    @Test
    void theLuhnCheckSeparatesCardNumbersFromOtherLongNumbers() {
        assertThat(SensitiveDataPolicy.passesLuhn("4111111111111111")).isTrue();
        assertThat(SensitiveDataPolicy.passesLuhn("4111111111111112")).isFalse();
    }
}
