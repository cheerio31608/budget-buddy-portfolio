package com.finance.budget_buddy.service.csv;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.budget_buddy.dto.TransactionCreateRequest;
import com.finance.budget_buddy.dto.csv.CsvColumnMapping;
import com.finance.budget_buddy.entity.Category;
import com.finance.budget_buddy.exception.BusinessException;
import com.finance.budget_buddy.exception.ErrorCode;
import com.finance.budget_buddy.repository.CategoryRepository;
import com.finance.budget_buddy.repository.TransactionRepository;
import com.finance.budget_buddy.repository.UserRepository;
import com.finance.budget_buddy.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;

/** Imports the validated file in row order, sharing the balance lock and domain service. */
@Service
@RequiredArgsConstructor
public class CsvImportService {
    private final CsvParserService parser;
    private final CsvMappingService mapper;
    private final TransactionService service;
    private final UserRepository users;
    private final CategoryRepository categories;
    private final TransactionRepository transactions;
    private final ObjectMapper json;

    public record ImportResult(int created, int alreadyImported) {}

    @Transactional
    public ImportResult importFile(Long userId, MultipartFile file, CsvColumnMapping mapping) {
        var mapped = mapper.map(parser.parse(file), mapping);
        if (mapped.validation().errorRows() > 0 || mapped.transactions().isEmpty()) {
            String detail = mapped.validation().errors().isEmpty() ? "저장할 거래가 없습니다."
                    : mapped.validation().errors().get(0).rowNumber() + "행: " + mapped.validation().errors().get(0).message();
            throw new BusinessException(ErrorCode.CSV_INVALID_FORMAT, detail + " 전체 파일을 수정한 뒤 다시 업로드해 주세요.");
        }
        if (mapped.transactions().size() > 500) throw new BusinessException(ErrorCode.CSV_INVALID_FORMAT, "한 번에 500건까지 저장할 수 있습니다.");
        String digest;
        try {
            digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(mapped.transactions())));
        } catch (NoSuchAlgorithmException | JsonProcessingException exception) { throw new IllegalStateException("Could not fingerprint import"); }

        users.findByIdForUpdate(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        var categoryMap = new HashMap<String, Category>();
        for (var category : categories.findByUserIdOrderByNameAsc(userId)) {
            categoryMap.put(category.getType() + ":" + category.getName(), category);
        }
        int created = 0;
        int reused = 0;
        for (int i = 0; i < mapped.transactions().size(); i++) {
            var row = mapped.transactions().get(i);
            String key = "csv:" + digest + ":" + i;
            if (transactions.findByUserIdAndIdempotencyKey(userId, key).isPresent()) { reused++; continue; }
            String categoryKey = row.transactionType() + ":" + row.categoryName();
            Category category = categoryMap.computeIfAbsent(categoryKey, ignored -> categories.save(
                    Category.builder().userId(userId).name(row.categoryName()).type(row.transactionType()).build()));
            try {
                service.createTransaction(new TransactionCreateRequest(userId, category.getCategoryId(), row.amount(),
                        row.transactionType(), row.description(), row.transactionAt(), row.vendorName(), null, key));
            } catch (BusinessException exception) {
                throw new BusinessException(exception.getErrorCode(), (i + 2) + "행 저장 실패. 전체 가져오기를 취소했습니다. " + exception.getMessage());
            }
            created++;
        }
        return new ImportResult(created, reused);
    }
}
