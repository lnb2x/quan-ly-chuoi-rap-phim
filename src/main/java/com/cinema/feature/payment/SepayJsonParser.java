package com.cinema.feature.payment;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Bộ phân tích cú pháp đối tượng JSON phẳng theo chuẩn RFC 8259 dành cho SePay Webhook.
 * Chặn khóa trùng lặp (key-smuggling), cấu trúc lồng nhau, ký tự điều khiển không thoát và dữ liệu thừa.
 */
public final class SepayJsonParser {

    public static final int MAX_PAYLOAD_CHARS = 16_384;
    private static final int MAX_KEYS = 32;
    private static final int MAX_STRING_LENGTH = 2_048;
    private static final int MAX_NUMBER_TOKEN_LENGTH = 32;
    private static final BigInteger MAX_VND_AMOUNT = BigInteger.valueOf(999_999_999_999L);
    private static final BigDecimal MAX_VND_DECIMAL = new BigDecimal(MAX_VND_AMOUNT);

    private enum ValueKind { STRING, NUMBER, BOOLEAN, NULL }

    private record JsonScalar(ValueKind kind, String value) {
    }

    private SepayJsonParser() {
    }

    public static SepayWebhookEvent parseWebhookEvent(String rawJson) {
        Map<String, JsonScalar> fields = parseFlatObject(rawJson);
        long id = requirePositiveLong(fields, "id");
        String gateway = requireNonBlankString(fields, "gateway");
        String transactionDate = requireNonBlankString(fields, "transactionDate");
        String accountNumber = requireNonBlankString(fields, "accountNumber");
        String subAccount = optionalNullableString(fields, "subAccount");
        String code = optionalNullableString(fields, "code");
        String content = optionalNullableString(fields, "content");
        String transferType = requireNonBlankString(fields, "transferType");
        if (!Set.of("in", "out").contains(transferType)) {
            throw new IllegalArgumentException("transferType chỉ chấp nhận 'in' hoặc 'out'.");
        }
        BigDecimal transferAmount = requirePositiveVndInteger(fields, "transferAmount");
        BigDecimal accumulated = optionalDecimal(fields, "accumulated");
        String referenceCode = optionalNullableString(fields, "referenceCode");
        String description = optionalNullableString(fields, "description");

        return new SepayWebhookEvent(
                id, gateway, transactionDate, accountNumber, subAccount,
                code, content, transferType, transferAmount, accumulated, referenceCode, description
        );
    }

    static Map<String, JsonScalar> parseFlatObject(String rawJson) {
        if (rawJson == null || rawJson.isBlank() || rawJson.length() > MAX_PAYLOAD_CHARS) {
            throw new IllegalArgumentException("Payload JSON rỗng hoặc vượt giới hạn kích thước.");
        }
        Cursor cursor = new Cursor(rawJson);
        cursor.skipWhitespace();
        cursor.expect('{');
        cursor.skipWhitespace();

        Map<String, JsonScalar> result = new LinkedHashMap<>();
        if (cursor.peekIs('}')) {
            cursor.advance();
            cursor.ensureEnd();
            return Map.copyOf(result);
        }

        while (true) {
            if (result.size() >= MAX_KEYS) {
                throw new IllegalArgumentException("Payload JSON vượt quá số trường tối đa.");
            }
            cursor.skipWhitespace();
            String key = cursor.readString();
            if (key.isEmpty() || key.length() > 64) {
                throw new IllegalArgumentException("Tên trường JSON không hợp lệ.");
            }
            cursor.skipWhitespace();
            cursor.expect(':');
            cursor.skipWhitespace();
            JsonScalar val = cursor.readScalar();
            if (result.putIfAbsent(key, val) != null) {
                throw new IllegalArgumentException("Phát hiện khóa JSON bị lặp: " + key);
            }
            cursor.skipWhitespace();
            if (cursor.peekIs(',')) {
                cursor.advance();
                continue;
            }
            cursor.expect('}');
            break;
        }
        cursor.ensureEnd();
        return Map.copyOf(result);
    }

    private static long requirePositiveLong(Map<String, JsonScalar> fields, String key) {
        JsonScalar scalar = fields.get(key);
        if (scalar == null || scalar.kind() != ValueKind.NUMBER || !scalar.value().matches("[1-9][0-9]{0,17}")) {
            throw new IllegalArgumentException("Trường số nguyên dương không hợp lệ: " + key);
        }
        return Long.parseLong(scalar.value());
    }

    private static String requireNonBlankString(Map<String, JsonScalar> fields, String key) {
        JsonScalar scalar = fields.get(key);
        if (scalar == null || scalar.kind() != ValueKind.STRING) {
            throw new IllegalArgumentException("Thiếu trường chuỗi bắt buộc: " + key);
        }
        String trimmed = scalar.value().trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Trường chuỗi bắt buộc không được rỗng: " + key);
        }
        return trimmed;
    }

    private static String optionalNullableString(Map<String, JsonScalar> fields, String key) {
        JsonScalar scalar = fields.get(key);
        if (scalar == null || scalar.kind() == ValueKind.NULL) {
            return null;
        }
        if (scalar.kind() != ValueKind.STRING) {
            throw new IllegalArgumentException("Trường " + key + " phải là chuỗi hoặc null.");
        }
        return scalar.value();
    }

    private static BigDecimal requirePositiveVndInteger(Map<String, JsonScalar> fields, String key) {
        JsonScalar scalar = fields.get(key);
        if (scalar == null || scalar.kind() != ValueKind.NUMBER) {
            throw new IllegalArgumentException("Trường tiền tệ bắt buộc là số: " + key);
        }
        try {
            BigDecimal amount = new BigDecimal(scalar.value());
            if (amount.signum() <= 0 || amount.scale() < -12 || amount.compareTo(MAX_VND_DECIMAL) > 0) {
                throw new IllegalArgumentException("Số tiền VND nằm ngoài phạm vi hợp lệ.");
            }
            BigInteger exactVnd = amount.toBigIntegerExact();
            return new BigDecimal(exactVnd);
        } catch (NumberFormatException | ArithmeticException ex) {
            throw new IllegalArgumentException("Số tiền VND phải là số nguyên dương chính xác.", ex);
        }
    }

    private static BigDecimal optionalDecimal(Map<String, JsonScalar> fields, String key) {
        JsonScalar scalar = fields.get(key);
        if (scalar == null || scalar.kind() == ValueKind.NULL) {
            return null;
        }
        if (scalar.kind() != ValueKind.NUMBER) {
            throw new IllegalArgumentException("Trường " + key + " phải là số hoặc null.");
        }
        try {
            BigDecimal val = new BigDecimal(scalar.value());
            if (Math.abs(val.scale()) > 12 || val.precision() > 24) {
                throw new IllegalArgumentException("Độ chính xác số vượt giới hạn ở trường " + key);
            }
            return val;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Giá trị số không hợp lệ ở trường " + key, ex);
        }
    }

    private static final class Cursor {
        private final String input;
        private int pos;

        Cursor(String input) {
            this.input = input;
        }

        void skipWhitespace() {
            while (pos < input.length()) {
                char c = input.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        boolean peekIs(char expected) {
            return pos < input.length() && input.charAt(pos) == expected;
        }

        void advance() {
            pos++;
        }

        void expect(char expected) {
            if (pos >= input.length() || input.charAt(pos) != expected) {
                throw new IllegalArgumentException("Cú pháp JSON không hợp lệ tại vị trí " + pos);
            }
            pos++;
        }

        void ensureEnd() {
            skipWhitespace();
            if (pos != input.length()) {
                throw new IllegalArgumentException("Phát hiện dữ liệu thừa sau đối tượng JSON.");
            }
        }

        JsonScalar readScalar() {
            if (pos >= input.length()) {
                throw new IllegalArgumentException("Kết thúc JSON bất ngờ.");
            }
            char c = input.charAt(pos);
            if (c == '"') {
                return new JsonScalar(ValueKind.STRING, readString());
            }
            if (c == '{' || c == '[') {
                throw new IllegalArgumentException("Không cho phép đối tượng hoặc mảng lồng nhau trong Webhook.");
            }
            if (input.startsWith("null", pos)) {
                pos += 4;
                return new JsonScalar(ValueKind.NULL, null);
            }
            if (input.startsWith("true", pos)) {
                pos += 4;
                return new JsonScalar(ValueKind.BOOLEAN, "true");
            }
            if (input.startsWith("false", pos)) {
                pos += 5;
                return new JsonScalar(ValueKind.BOOLEAN, "false");
            }
            return new JsonScalar(ValueKind.NUMBER, readNumber());
        }

        String readString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (pos < input.length()) {
                char c = input.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c < 0x20) {
                    throw new IllegalArgumentException("Chuỗi JSON chứa ký tự điều khiển chưa thoát.");
                }
                char decoded = (c == '\\') ? readEscapedChar() : c;
                if (isDisallowedDecodedChar(decoded)) {
                    throw new IllegalArgumentException("Chuỗi JSON chứa ký tự điều khiển hoặc surrogate không hợp lệ.");
                }
                if (sb.length() >= MAX_STRING_LENGTH) {
                    throw new IllegalArgumentException("Giá trị chuỗi JSON vượt quá độ dài tối đa.");
                }
                sb.append(decoded);
            }
            throw new IllegalArgumentException("Chuỗi JSON chưa được đóng dấu ngoặc kép.");
        }

        private static boolean isDisallowedDecodedChar(char ch) {
            if (ch == '\0' || ch == 0x7F || Character.isSurrogate(ch)) {
                return true;
            }
            return ch < 0x20 && ch != '\t' && ch != '\n' && ch != '\r';
        }

        private char readEscapedChar() {
            if (pos >= input.length()) {
                throw new IllegalArgumentException("Chuỗi thoát JSON bị cắt cụt.");
            }
            char esc = input.charAt(pos++);
            return switch (esc) {
                case '"', '\\', '/' -> esc;
                case 'b' -> '\b';
                case 'f' -> '\f';
                case 'n' -> '\n';
                case 'r' -> '\r';
                case 't' -> '\t';
                case 'u' -> readUnicodeEscape();
                default -> throw new IllegalArgumentException("Ký tự thoát JSON không hợp lệ: \\" + esc);
            };
        }

        private char readUnicodeEscape() {
            if (pos + 4 > input.length()) {
                throw new IllegalArgumentException("Mã thoát Unicode JSON không đủ 4 ký tự hex.");
            }
            String hex = input.substring(pos, pos + 4);
            pos += 4;
            try {
                return (char) Integer.parseInt(hex, 16);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Mã thoát Unicode JSON không hợp lệ: " + hex, ex);
            }
        }

        private String readNumber() {
            int start = pos;
            while (pos < input.length() && (pos - start) <= MAX_NUMBER_TOKEN_LENGTH) {
                char c = input.charAt(pos);
                if ((c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E') {
                    pos++;
                } else {
                    break;
                }
            }
            String token = input.substring(start, pos);
            if (token.length() > MAX_NUMBER_TOKEN_LENGTH
                    || !token.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")) {
                throw new IllegalArgumentException("Định dạng số JSON không hợp lệ hoặc quá dài.");
            }
            return token;
        }
    }
}
