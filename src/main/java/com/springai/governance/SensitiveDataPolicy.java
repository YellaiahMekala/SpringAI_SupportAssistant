package com.springai.governance;

import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pattern-based sensitive-data check: reject credential values, redact Luhn-valid card numbers, else allow.
 * Decisions do not carry matched secret text.
 */
@Component
public class SensitiveDataPolicy {

    public enum Action { ALLOW, REDACT, REJECT }

    public enum Category { PASSWORD, ONE_TIME_CODE, CARD_SECURITY_CODE, SECRET_TOKEN, CARD_NUMBER }

    /** The outcome. {@code text} is the (possibly redacted) text, or {@code null} when rejected. */
    public record Decision(Action action, Set<Category> categories, String text) {
    }

    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

    private static final Pattern PASSWORD =
            Pattern.compile("\\b(password|passwd|pwd)\\b\\s*(is|:|=)\\s*\\S+", FLAGS);

    private static final Pattern ONE_TIME_CODE = Pattern.compile(
            "\\b(otp|one[- ]time (code|password|pin)|verification code|passcode)\\b[^\\n]{0,20}?\\b\\d{4,8}\\b", FLAGS);

    private static final Pattern CARD_SECURITY_CODE =
            Pattern.compile("\\b(cvv2?|cvc2?|security code)\\b[^\\n]{0,15}?\\b\\d{3,4}\\b", FLAGS);

    private static final Pattern SECRET_TOKEN = Pattern.compile(
            "\\bsk-[A-Za-z0-9_-]{16,}"                                   // API-key style
                    + "|\\bBearer\\s+[A-Za-z0-9._~+/=-]{20,}"             // bearer credential
                    + "|\\beyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{4,}" // JSON web token
                    + "|\\bAKIA[0-9A-Z]{16}\\b", FLAGS);                 // cloud access key id

    // 13 to 19 digits, optionally separated by single spaces or dashes. Starts and ends on a digit, so the
    // space after the number is not consumed (and therefore not lost when the number is replaced).
    private static final Pattern CARD_NUMBER = Pattern.compile("\\b\\d(?:[ -]?\\d){12,18}\\b");

    public Decision evaluate(String text) {
        if (text == null || text.isEmpty()) {
            return new Decision(Action.ALLOW, Set.of(), text);
        }

        Set<Category> rejected = EnumSet.noneOf(Category.class);
        if (PASSWORD.matcher(text).find()) {
            rejected.add(Category.PASSWORD);
        }
        if (ONE_TIME_CODE.matcher(text).find()) {
            rejected.add(Category.ONE_TIME_CODE);
        }
        if (CARD_SECURITY_CODE.matcher(text).find()) {
            rejected.add(Category.CARD_SECURITY_CODE);
        }
        if (SECRET_TOKEN.matcher(text).find()) {
            rejected.add(Category.SECRET_TOKEN);
        }
        if (!rejected.isEmpty()) {
            return new Decision(Action.REJECT, Set.copyOf(rejected), null);
        }

        String redacted = redactCardNumbers(text);
        if (!redacted.equals(text)) {
            return new Decision(Action.REDACT, Set.of(Category.CARD_NUMBER), redacted);
        }
        return new Decision(Action.ALLOW, Set.of(), text);
    }

    private static String redactCardNumbers(String text) {
        Matcher matcher = CARD_NUMBER.matcher(text);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String digits = matcher.group().replaceAll("[ -]", "");
            if (digits.length() >= 13 && digits.length() <= 19 && passesLuhn(digits)) {
                String last4 = digits.substring(digits.length() - 4);
                matcher.appendReplacement(out, Matcher.quoteReplacement("[card ending " + last4 + "]"));
            } else {
                matcher.appendReplacement(out, Matcher.quoteReplacement(matcher.group()));
            }
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /** Luhn checksum: separates real card numbers from other long digit strings such as order numbers. */
    static boolean passesLuhn(String digits) {
        int sum = 0;
        boolean doubleIt = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (doubleIt) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
            doubleIt = !doubleIt;
        }
        return sum % 10 == 0;
    }
}
