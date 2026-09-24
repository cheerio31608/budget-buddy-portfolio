package com.finance.budget_buddy.service.csv;

import com.finance.budget_buddy.exception.BusinessException;
import com.finance.budget_buddy.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Small RFC-4180-style parser with UTF-8 and Korean MS949 decoding support. */
@Service
public class CsvParserService {

    private static final long MAX_FILE_BYTES = 5L * 1024 * 1024;

    CsvTable parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.CSV_EMPTY_FILE);
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new BusinessException(ErrorCode.CSV_FILE_TOO_LARGE);
        }
        if (file.getOriginalFilename() == null || !file.getOriginalFilename().toLowerCase(java.util.Locale.ROOT).endsWith(".csv")) {
            throw new BusinessException(ErrorCode.CSV_INVALID_FORMAT, ".csv 파일만 업로드할 수 있습니다.");
        }

        try {
            String content = decode(file.getBytes());
            if (content.startsWith("\uFEFF")) {
                content = content.substring(1);
            }
            char delimiter = detectDelimiter(content);
            List<List<String>> parsedRows = parseRows(content, delimiter);
            if (parsedRows.isEmpty()) {
                throw new BusinessException(ErrorCode.CSV_EMPTY_FILE);
            }

            List<String> headers = parsedRows.get(0).stream().map(String::trim).toList();
            validateHeaders(headers);
            return new CsvTable(headers, List.copyOf(parsedRows.subList(1, parsedRows.size())));
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.CSV_INVALID_FORMAT, "CSV file could not be read.");
        }
    }

    private String decode(byte[] bytes) {
        try {
            return decodeStrict(bytes, StandardCharsets.UTF_8);
        } catch (CharacterCodingException ignored) {
            try {
                return decodeStrict(bytes, Charset.forName("MS949"));
            } catch (CharacterCodingException exception) {
                throw new BusinessException(ErrorCode.CSV_INVALID_FORMAT, "CSV character encoding is not supported.");
            }
        }
    }

    private String decodeStrict(byte[] bytes, Charset charset) throws CharacterCodingException {
        CharBuffer decoded = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes));
        return decoded.toString();
    }

    private char detectDelimiter(String content) {
        String header = content.lines().findFirst().orElse("");
        char[] candidates = {',', '\t', ';'};
        char best = ',';
        int bestCount = -1;
        for (char candidate : candidates) {
            int count = countOutsideQuotes(header, candidate);
            if (count > bestCount) {
                bestCount = count;
                best = candidate;
            }
        }
        return best;
    }

    private int countOutsideQuotes(String line, char candidate) {
        boolean quoted = false;
        int count = 0;
        for (int index = 0; index < line.length(); index++) {
            char current = line.charAt(index);
            if (current == '"') {
                if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (!quoted && current == candidate) {
                count++;
            }
        }
        return count;
    }

    private List<List<String>> parseRows(String content, char delimiter) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;

        for (int index = 0; index < content.length(); index++) {
            if (rows.size() > 5000 || row.size() > 50 || cell.length() > 2000) {
                throw new BusinessException(ErrorCode.CSV_INVALID_FORMAT, "CSV는 5,000행, 50열, 셀당 2,000자 이하여야 합니다.");
            }
            char current = content.charAt(index);
            if (current == '"') {
                if (quoted && index + 1 < content.length() && content.charAt(index + 1) == '"') {
                    cell.append('"');
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (current == delimiter && !quoted) {
                row.add(cell.toString().trim());
                cell.setLength(0);
            } else if ((current == '\n' || current == '\r') && !quoted) {
                if (current == '\r' && index + 1 < content.length() && content.charAt(index + 1) == '\n') {
                    index++;
                }
                row.add(cell.toString().trim());
                cell.setLength(0);
                rows.add(List.copyOf(row));
                row.clear();
            } else {
                cell.append(current);
            }
        }

        if (quoted) {
            throw new BusinessException(ErrorCode.CSV_INVALID_FORMAT, "CSV contains an unclosed quoted value.");
        }
        if (!row.isEmpty() || cell.length() > 0) {
            row.add(cell.toString().trim());
            rows.add(List.copyOf(row));
        }
        if (rows.size() > 5001 || rows.stream().anyMatch(values -> values.size() > 50
                || values.stream().anyMatch(value -> value.length() > 2000))) {
            throw new BusinessException(ErrorCode.CSV_INVALID_FORMAT, "CSV는 5,000행, 50열, 셀당 2,000자 이하여야 합니다.");
        }
        return rows;
    }

    private void validateHeaders(List<String> headers) {
        if (headers.isEmpty() || headers.stream().allMatch(String::isBlank)) {
            throw new BusinessException(ErrorCode.CSV_INVALID_FORMAT, "CSV header row is missing.");
        }
        Set<String> unique = new HashSet<>();
        for (String header : headers) {
            if (header.isBlank()) {
                throw new BusinessException(ErrorCode.CSV_INVALID_FORMAT, "CSV contains an empty header.");
            }
            if (!unique.add(header)) {
                throw new BusinessException(ErrorCode.CSV_INVALID_FORMAT, "CSV contains a duplicated header: " + header);
            }
        }
    }
}
