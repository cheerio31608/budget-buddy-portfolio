package com.finance.budget_buddy.service.csv;

import com.finance.budget_buddy.dto.csv.CsvColumnMapping;
import com.finance.budget_buddy.entity.TransactionType;
import com.finance.budget_buddy.exception.BusinessException;
import com.finance.budget_buddy.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvMappingServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-05T03:00:00Z"), ZoneId.of("Asia/Seoul"));
    private final CsvMappingService mappingService = new CsvMappingService(clock);

    @Test
    @DisplayName("Common Korean and English column names are mapped automatically")
    void suggestMapping_recognizesAliases() {
        var mapping = mappingService.suggestMapping(List.of("거래일자", "사용처", "분류", "결제금액", "입출금구분"));

        assertThat(mapping).containsEntry("date", "거래일자")
                .containsEntry("description", "사용처")
                .containsEntry("category", "분류")
                .containsEntry("amount", "결제금액")
                .containsEntry("type", "입출금구분");
    }

    @Test
    @DisplayName("Korean category-specific headers are recognized without manual mapping")
    void suggestMapping_recognizesCategoryHeader() {
        var mapping = mappingService.suggestMapping(List.of("카테고리명"));

        assertThat(mapping).containsEntry("category", "카테고리명");
    }

    @Test
    @DisplayName("Unknown date and amount headers are inferred only when their values are unambiguous")
    void suggestMapping_usesUnambiguousSampleValues() {
        CsvTable table = new CsvTable(
                List.of("발생시각", "사용처", "분류", "사용액", "구분"),
                List.of(
                        List.of("2026-08-01", "마트", "식비", "12000", "지출"),
                        List.of("2026-08-02", "회사", "급여", "3000000", "수입")));

        var mapping = mappingService.suggestMapping(table);

        assertThat(mapping).containsEntry("date", "발생시각")
                .containsEntry("amount", "사용액")
                .containsEntry("type", "구분")
                .containsEntry("category", "분류");
    }

    @Test
    @DisplayName("Ambiguous numeric columns are not guessed as the transaction amount")
    void suggestMapping_leavesAmbiguousAmountForUser() {
        CsvTable table = new CsvTable(
                List.of("거래일", "사용처", "분류", "컬럼A", "컬럼B", "구분"),
                List.of(
                        List.of("2026-08-01", "마트", "식비", "12000", "90000", "지출"),
                        List.of("2026-08-02", "회사", "급여", "3000000", "120000", "수입")));

        var mapping = mappingService.suggestMapping(table);

        assertThat(mapping).doesNotContainKey("amount");
    }

    @Test
    @DisplayName("An explicit amount header is not replaced by a different numeric column")
    void suggestMapping_keepsExplicitAmountWhenOtherNumbersExist() {
        CsvTable table = new CsvTable(
                List.of("date", "description", "category", "amount", "balance", "type"),
                List.of(
                        List.of("2026-08-01", "마트", "식비", "12000", "90000", "EXPENSE"),
                        List.of("2026-08-02", "회사", "급여", "3000000", "3090000", "INCOME")));

        var mapping = mappingService.suggestMapping(table);

        assertThat(mapping).containsEntry("amount", "amount");
    }

    @Test
    void suggestMapping_recognizesPlusMinusTypeValues() {
        CsvTable table = new CsvTable(List.of("date", "description", "category", "amount", "흐름"),
                List.of(List.of("2026-08-01", "회사", "급여", "10000", "+"),
                        List.of("2026-08-02", "카페", "카페", "2500", "-")));
        assertThat(mappingService.suggestMapping(table)).containsEntry("type", "흐름");
    }

    @Test
    void suggestMapping_doesNotGuessMissingCategoryOrConflictingDates() {
        CsvTable table = new CsvTable(List.of("거래일", "승인일", "내용", "금액", "구분"),
                List.of(List.of("2026-08-01", "2026-08-02", "마트", "12000", "지출")));
        assertThat(mappingService.suggestMapping(table)).doesNotContainKeys("date", "category");
    }

    @Test
    @DisplayName("Invalid date, amount, type, missing values and blank rows return row-level errors")
    void map_collectsValidationErrorsWithoutFailingWholeFile() {
        CsvTable table = new CsvTable(
                List.of("date", "description", "category", "amount", "type"),
                List.of(
                        List.of("2026-08-01", "마트", "식비", "12,000", "지출"),
                        List.of("wrong", "택시", "교통", "abc", "unknown"),
                        List.of("", "", "", "", "")
                ));

        CsvMappedRows result = mappingService.map(table,
                new CsvColumnMapping("date", "description", "category", "amount", "type"));

        assertThat(result.transactions()).hasSize(1);
        assertThat(result.transactions().get(0).transactionType()).isEqualTo(TransactionType.EXPENSE);
        assertThat(result.transactions().get(0).amount()).isEqualByComparingTo("12000.00");
        assertThat(result.validation().validRows()).isEqualTo(1);
        assertThat(result.validation().errorRows()).isEqualTo(2);
        assertThat(result.validation().errors()).extracting(error -> error.field())
                .contains("date", "amount", "type", "row");
    }

    @Test
    @DisplayName("Exact repeated rows are counted as suspected duplicates but remain analyzable")
    void map_flagsDuplicates() {
        CsvTable table = new CsvTable(
                List.of("date", "description", "category", "amount", "type"),
                List.of(
                        List.of("2026-08-01", "마트", "식비", "10000", "EXPENSE"),
                        List.of("2026-08-01", "마트", "식비", "10000", "EXPENSE")
                ));

        CsvMappedRows result = mappingService.map(table,
                new CsvColumnMapping("date", "description", "category", "amount", "type"));

        assertThat(result.validation().validRows()).isEqualTo(2);
        assertThat(result.validation().suspectedDuplicates()).isEqualTo(1);
    }

    @Test
    @DisplayName("An unmapped required field returns CSV_REQUIRED_COLUMN_MISSING instead of an internal error")
    void map_rejectsMissingRequiredMapping() {
        CsvTable table = new CsvTable(
                List.of("date", "category", "amount", "type"),
                List.of(List.of("2026-08-01", "Food", "10000", "EXPENSE")));

        assertThatThrownBy(() -> mappingService.map(table,
                new CsvColumnMapping(null, null, "category", "amount", "type")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CSV_REQUIRED_COLUMN_MISSING);
    }
}
