package com.finance.budget_buddy.service.csv;

import com.finance.budget_buddy.exception.BusinessException;
import com.finance.budget_buddy.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvParserServiceTest {

    private final CsvParserService parserService = new CsvParserService();

    @Test
    @DisplayName("Quoted commas and escaped quotes are parsed without splitting the value")
    void parse_supportsQuotedValues() {
        String csv = "date,description,category,amount,type\n"
                + "2026-08-01,\"Market, Seoul\",Food,\"12,000\",EXPENSE\n"
                + "2026-08-02,\"Coffee \"\"Bean\"\"\",Food,5000,EXPENSE";

        CsvTable table = parserService.parse(file(csv.getBytes(StandardCharsets.UTF_8)));

        assertThat(table.headers()).containsExactly("date", "description", "category", "amount", "type");
        assertThat(table.rows()).hasSize(2);
        assertThat(table.rows().get(0).get(1)).isEqualTo("Market, Seoul");
        assertThat(table.rows().get(0).get(3)).isEqualTo("12,000");
        assertThat(table.rows().get(1).get(1)).isEqualTo("Coffee \"Bean\"");
    }

    @Test
    @DisplayName("MS949 Korean CSV is decoded as a fallback")
    void parse_supportsMs949() {
        String csv = "거래일자,상호명,카테고리,금액,구분\n2026-08-01,동네마트,식비,10000,지출";

        CsvTable table = parserService.parse(file(csv.getBytes(Charset.forName("MS949"))));

        assertThat(table.headers()).contains("거래일자", "상호명");
        assertThat(table.rows().get(0).get(1)).isEqualTo("동네마트");
    }

    @Test
    @DisplayName("Duplicated headers fail with a user-facing CSV error")
    void parse_rejectsDuplicatedHeaders() {
        assertThatThrownBy(() -> parserService.parse(file("date,date\n1,2".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CSV_INVALID_FORMAT);
    }

    private MockMultipartFile file(byte[] bytes) {
        return new MockMultipartFile("file", "transactions.csv", "text/csv", bytes);
    }
}
