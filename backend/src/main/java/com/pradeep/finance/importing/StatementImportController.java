package com.pradeep.finance.importing;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/imports")
public class StatementImportController {
    private final StatementImportService statementImportService;

    public StatementImportController(StatementImportService statementImportService) { this.statementImportService = statementImportService; }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public StatementImportResponse upload(@RequestPart("statement") MultipartFile statement) throws IOException {
        return statementImportService.importStatement(statement);
    }

    @GetMapping
    public List<ImportHistoryItem> history() { return statementImportService.getHistory(); }

    @GetMapping("/{importId}") public ImportReview getReview(@PathVariable String importId) { return statementImportService.getReview(importId); }
    @PatchMapping("/{importId}/transactions/{transactionId}") public ReviewTransaction update(@PathVariable String importId, @PathVariable String transactionId, @Valid @RequestBody ReviewTransactionUpdate update) { return statementImportService.updateReviewTransaction(importId, transactionId, update); }
    @DeleteMapping("/{importId}/transactions/{transactionId}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable String importId, @PathVariable String transactionId) { statementImportService.deleteReviewTransaction(importId, transactionId); }
    @PostMapping("/{importId}/confirm") public ImportReview confirm(@PathVariable String importId) { return statementImportService.confirmImport(importId); }

    @PostMapping("/confirm-trusted")
    public int confirmTrusted() { return statementImportService.confirmTrustedImports(); }
}
