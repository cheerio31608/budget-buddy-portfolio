package com.finance.budget_buddy.service.csv;

import com.finance.budget_buddy.dto.analysis.AnalysisTransaction;
import com.finance.budget_buddy.dto.csv.CsvColumnMapping;
import com.finance.budget_buddy.dto.csv.CsvRowError;
import com.finance.budget_buddy.dto.csv.CsvValidationSummary;
import com.finance.budget_buddy.entity.TransactionType;
import com.finance.budget_buddy.exception.BusinessException;
import com.finance.budget_buddy.exception.ErrorCode;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Applies user-selected columns and reports row-level validation errors. */
@Service
public class CsvMappingService {

    private static final Map<String, List<String>> HEADER_ALIASES = Map.of(
            "date", List.of("date", "transactiondate", "transactionat", "거래일자", "거래일", "거래일시", "일자", "날짜", "승인일자", "승인일", "이용일", "결제일"),
            "description", List.of("description", "merchant", "vendor", "vendorname", "store", "memo", "상호명", "사용처", "가맹점명", "가맹점", "거래처", "거래내용", "적요", "내용", "내역"),
            "category", List.of("category", "classification", "분류", "분류명", "카테고리", "카테고리명", "소비항목", "지출항목", "지출분류", "대분류", "소분류", "항목", "업종", "업종명"),
            "amount", List.of("amount", "price", "payment", "금액", "결제금액", "승인금액", "이용금액", "거래금액", "출금액"),
            "type", List.of("type", "transactiontype", "kind", "구분", "입출금", "입출금구분", "거래구분", "수입지출", "수입지출구분")
    );

    private static final List<DateTimeFormatter> DATE_TIME_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("uuuu/MM/dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("uuuu/MM/dd HH:mm"),
            DateTimeFormatter.ofPattern("uuuu.MM.dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("uuuu.MM.dd HH:mm")
    );

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("uuuu/MM/dd"),
            DateTimeFormatter.ofPattern("uuuu.MM.dd"),
            DateTimeFormatter.ofPattern("uuuuMMdd")
    );

    private final Clock clock;

    public CsvMappingService(Clock clock) {
        this.clock = clock;
    }

    Map<String, String> suggestMapping(List<String> headers) {
        Map<String, String> suggestions = new LinkedHashMap<>();
        HEADER_ALIASES.forEach((field, aliases) -> {
            List<String> matches = headers.stream()
                    .filter(header -> aliases.contains(normalize(header)))
                    .toList();
            if (matches.size() == 1) {
                suggestions.put(field, matches.get(0));
            }
        });
        return suggestions;
    }

    Map<String, String> suggestMapping(CsvTable table) {
        Map<String, String> suggestions = suggestMapping(table.headers());
        Set<String> assignedHeaders = new HashSet<>(suggestions.values());

        if (!suggestions.containsKey("date")) {
            inferUniqueColumn(table, assignedHeaders, this::looksLikeDate)
                    .ifPresent(header -> { suggestions.put("date", header); assignedHeaders.add(header); });
        }
        if (!suggestions.containsKey("amount")) {
            inferUniqueColumn(table, assignedHeaders, this::looksLikeAmount)
                    .ifPresent(header -> { suggestions.put("amount", header); assignedHeaders.add(header); });
        }
        if (!suggestions.containsKey("type")) {
            inferUniqueColumn(table, assignedHeaders, this::looksLikeType)
                    .ifPresent(header -> suggestions.put("type", header));
        }
        return suggestions;
    }

    private java.util.Optional<String> inferUniqueColumn(CsvTable table, Set<String> assignedHeaders,
                                                           java.util.function.Predicate<String> valueMatcher) {
        List<List<String>> sampleRows = table.rows().stream().limit(20).toList();
        List<String> candidates = table.headers().stream()
                .filter(header -> !assignedHeaders.contains(header))
                .filter(header -> hasConsistentValues(header, table.headers(), sampleRows, valueMatcher))
                .toList();
        return candidates.size() == 1 ? java.util.Optional.of(candidates.get(0)) : java.util.Optional.empty();
    }

    private boolean hasConsistentValues(String header, List<String> headers, List<List<String>> rows,
                                        java.util.function.Predicate<String> valueMatcher) {
        int index = headers.indexOf(header);
        List<String> values = rows.stream()
                .filter(row -> row.stream().anyMatch(value -> !value.isBlank()))
                .map(row -> index < row.size() ? row.get(index).trim() : "")
                .filter(value -> !value.isBlank())
                .toList();
        int minimumValues = Math.min(2, rows.size());
        return !values.isEmpty() && values.size() >= minimumValues && values.stream().allMatch(valueMatcher);
    }

    private boolean looksLikeDate(String raw) {
        return DATE_TIME_FORMATS.stream().anyMatch(formatter -> canParse(raw, formatter))
                || DATE_FORMATS.stream().anyMatch(formatter -> canParse(raw, formatter));
    }

    private boolean canParse(String raw, DateTimeFormatter formatter) {
        try {
            if (DATE_TIME_FORMATS.contains(formatter)) {
                LocalDateTime.parse(raw, formatter.withResolverStyle(java.time.format.ResolverStyle.STRICT));
            } else {
                LocalDate.parse(raw, formatter.withResolverStyle(java.time.format.ResolverStyle.STRICT));
            }
            return true;
        } catch (DateTimeParseException ignored) {
            return false;
        }
    }

    private boolean looksLikeAmount(String raw) {
        String normalized = raw.trim().replace(",", "").replace("₩", "").replace("원", "").replace(" ", "");
        if (normalized.startsWith("(") && normalized.endsWith(")")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        return normalized.matches("[+-]?\\d{1,13}(\\.\\d{1,2})?");
    }

    private boolean looksLikeType(String raw) {
        if ("+".equals(raw.trim()) || "-".equals(raw.trim())) {
            return true;
        }
        String normalized = normalize(raw);
        return Set.of("income", "수입", "입금", "credit", "expense", "지출", "출금", "debit")
                .contains(normalized);
    }

    CsvMappedRows map(CsvTable table, CsvColumnMapping mapping) {
        validateRequiredMappings(table.headers(), mapping);
        Map<String, Integer> indexes = headerIndexes(table.headers());
        List<AnalysisTransaction> transactions = new ArrayList<>();
        List<CsvRowError> errors = new ArrayList<>();
        Set<Integer> errorRows = new HashSet<>();
        Set<String> duplicateKeys = new HashSet<>();
        int duplicates = 0;

        for (int rowIndex = 0; rowIndex < table.rows().size(); rowIndex++) {
            List<String> row = table.rows().get(rowIndex);
            int displayRow = rowIndex + 2;
            int errorsBeforeRow = errors.size();
            if (row.stream().allMatch(String::isBlank)) {
                errors.add(new CsvRowError(displayRow, "row", "빈 행입니다."));
                errorRows.add(displayRow);
                continue;
            }

            LocalDateTime date = parseDate(value(row, indexes, mapping.date()), displayRow, errors);
            BigDecimal amount = parseAmount(value(row, indexes, mapping.amount()), displayRow, errors);
            TransactionType type = parseType(value(row, indexes, mapping.type()), displayRow, errors);
            String category = requiredText(value(row, indexes, mapping.category()), "category", displayRow, errors);
            String description = optionalValue(row, indexes, mapping.description());
            if (row.size() != table.headers().size()) errors.add(new CsvRowError(displayRow, "row", "헤더와 열 수가 다릅니다."));
            if (category != null && category.length() > 100) errors.add(new CsvRowError(displayRow, "category", "카테고리는 100자 이하여야 합니다."));
            if (description.length() > 255) errors.add(new CsvRowError(displayRow, "description", "상호명은 255자 이하여야 합니다."));

            if (date != null && date.isAfter(LocalDateTime.now(clock))) {
                errors.add(new CsvRowError(displayRow, "date", "미래 거래는 분석할 수 없습니다."));
                date = null;
            }

            if (errors.size() > errorsBeforeRow) {
                errorRows.add(displayRow);
                continue;
            }

            AnalysisTransaction transaction = new AnalysisTransaction(
                    date,
                    description,
                    description,
                    category,
                    amount,
                    type);
            String duplicateKey = date + "|" + normalize(description) + "|" + normalize(category)
                    + "|" + amount.stripTrailingZeros().toPlainString() + "|" + type;
            if (!duplicateKeys.add(duplicateKey)) {
                duplicates++;
            }
            transactions.add(transaction);
        }

        CsvValidationSummary validation = new CsvValidationSummary(
                table.rows().size(),
                transactions.size(),
                errorRows.size(),
                duplicates,
                List.copyOf(errors));
        return new CsvMappedRows(List.copyOf(transactions), validation);
    }

    private void validateRequiredMappings(List<String> headers, CsvColumnMapping mapping) {
        if (mapping == null) {
            throw new BusinessException(ErrorCode.CSV_REQUIRED_COLUMN_MISSING);
        }
        validateRequiredHeader(headers, "date", mapping.date());
        validateRequiredHeader(headers, "category", mapping.category());
        validateRequiredHeader(headers, "amount", mapping.amount());
        validateRequiredHeader(headers, "type", mapping.type());
        if (mapping.description() != null && !mapping.description().isBlank() && !headers.contains(mapping.description())) {
            throw new BusinessException(ErrorCode.CSV_REQUIRED_COLUMN_MISSING,
                    "description 필드에 유효한 CSV 컬럼을 연결해 주세요.");
        }
    }

    private void validateRequiredHeader(List<String> headers, String field, String header) {
        if (header == null || header.isBlank() || !headers.contains(header)) {
            throw new BusinessException(ErrorCode.CSV_REQUIRED_COLUMN_MISSING,
                    field + " 필드에 유효한 CSV 컬럼을 연결해 주세요.");
        }
    }

    private Map<String, Integer> headerIndexes(List<String> headers) {
        Map<String, Integer> indexes = new HashMap<>();
        for (int index = 0; index < headers.size(); index++) {
            indexes.put(headers.get(index), index);
        }
        return indexes;
    }

    private String value(List<String> row, Map<String, Integer> indexes, String header) {
        Integer index = indexes.get(header);
        return index == null || index >= row.size() ? "" : row.get(index).trim();
    }

    private String optionalValue(List<String> row, Map<String, Integer> indexes, String header) {
        return header == null || header.isBlank() ? "" : value(row, indexes, header);
    }

    private String requiredText(String value, String field, int row, List<CsvRowError> errors) {
        if (value == null || value.isBlank()) {
            errors.add(new CsvRowError(row, field, "필수 값이 비어 있습니다."));
            return null;
        }
        return value.trim();
    }

    private LocalDateTime parseDate(String raw, int row, List<CsvRowError> errors) {
        if (raw == null || raw.isBlank()) {
            errors.add(new CsvRowError(row, "date", "날짜가 비어 있습니다."));
            return null;
        }
        for (DateTimeFormatter formatter : DATE_TIME_FORMATS) {
            try {
                return LocalDateTime.parse(raw.trim(), formatter.withResolverStyle(java.time.format.ResolverStyle.STRICT));
            } catch (DateTimeParseException ignored) {
                // Try the next supported pattern.
            }
        }
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                return LocalDateTime.of(LocalDate.parse(raw.trim(), formatter.withResolverStyle(java.time.format.ResolverStyle.STRICT)), LocalTime.MIN);
            } catch (DateTimeParseException ignored) {
                // Try the next supported pattern.
            }
        }
        errors.add(new CsvRowError(row, "date", "지원하지 않는 날짜 형식입니다: " + raw));
        return null;
    }

    private BigDecimal parseAmount(String raw, int row, List<CsvRowError> errors) {
        if (raw == null || raw.isBlank()) {
            errors.add(new CsvRowError(row, "amount", "금액이 비어 있습니다."));
            return null;
        }
        String normalized = raw.trim()
                .replace(",", "")
                .replace("₩", "")
                .replace("원", "")
                .replace(" ", "");
        boolean parentheses = normalized.startsWith("(") && normalized.endsWith(")");
        if (parentheses) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        try {
            if (!normalized.matches("[+-]?\\d{1,13}(\\.\\d{1,2})?")) throw new NumberFormatException();
            BigDecimal amount = new BigDecimal(normalized).abs().setScale(2, RoundingMode.UNNECESSARY);
            if (amount.signum() <= 0 || amount.precision() > 15) {
                throw new NumberFormatException();
            }
            return amount;
        } catch (NumberFormatException exception) {
            errors.add(new CsvRowError(row, "amount", "올바른 양수 금액이 아닙니다: " + raw));
            return null;
        }
    }

    private TransactionType parseType(String raw, int row, List<CsvRowError> errors) {
        String trimmed = raw == null ? "" : raw.trim();
        if ("+".equals(trimmed)) {
            return TransactionType.INCOME;
        }
        if ("-".equals(trimmed)) {
            return TransactionType.EXPENSE;
        }
        String normalized = normalize(raw);
        if (Set.of("income", "수입", "입금", "credit").contains(normalized)) {
            return TransactionType.INCOME;
        }
        if (Set.of("expense", "지출", "출금", "debit").contains(normalized)) {
            return TransactionType.EXPENSE;
        }
        errors.add(new CsvRowError(row, "type", "수입/지출 구분을 인식할 수 없습니다: " + raw));
        return null;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[\\s_\\-]", "");
    }
}
