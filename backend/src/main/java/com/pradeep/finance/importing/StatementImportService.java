package com.pradeep.finance.importing;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.pradeep.finance.account.AccountIdentificationRequest;
import com.pradeep.finance.account.AccountIdentificationResponse;
import com.pradeep.finance.account.AccountType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class StatementImportService {
    private static final Pattern INSTITUTION = Pattern.compile("(?im)^(?:institution|bank|financial institution)\\s*:\\s*([^\\r\\n,]+)");
    private static final Pattern ACCOUNT_LAST_FOUR = Pattern.compile("(?i)(?:account|card)(?:\\s*(?:number|#|ending))?\\s*[:#-]?\\s*(?:x+|\\*+)?(\\d{4})");
    private static final Pattern BOFA_ACCOUNT_NUMBER = Pattern.compile("(?i)account(?:\\s+number)?\\s*[:#]?\\s*(?:\\d{4}\\s+){2}(\\d{4})");
    private static final Pattern BOFA_CARD_NUMBER = Pattern.compile("(?i)account\\s*#?\\s*[:#]?\\s*(?:\\d{4}\\s+){3}(\\d{4})");
    private static final Pattern AMEX_ACCOUNT_ENDING = Pattern.compile("(?i)account ending\\s+\\d-\\d*(\\d{4})");
    private static final Pattern AMEX_BANKING_ACCOUNT_ENDING = Pattern.compile("(?i)(?:rewards checking|high yield savings account)\\s*:?\\s*x+(\\d{4})");
    private static final Pattern AMEX_CHECKING_ACCOUNT_ENDING = Pattern.compile("(?i)account ending:\\s*\\*(\\d{4})");
    private static final Pattern DISCOVER_ACCOUNT_ENDING = Pattern.compile("(?i)account number ending in\\s+(\\d{4})");
    private static final Pattern WELLS_ACCOUNT_ENDING = Pattern.compile("(?i)account ending in\\s+(\\d{4})");
    private static final Pattern WELLS_CHECKING_ACCOUNT = Pattern.compile("(?i)account number:\\s*\\d*(\\d{4})");

    private final StatementTextExtractor textExtractor;
    private final List<StatementTransactionParser> transactionParsers;
    private final List<CardTermsExtractor> cardTermsExtractors;
    private final List<StatementSummaryExtractor> statementSummaryExtractors;
    private final com.pradeep.finance.transaction.TransactionCategorizer transactionCategorizer;
    private final com.pradeep.finance.transaction.TransferDetectionService transferDetectionService;
    private final com.pradeep.finance.account.AccountService accountService;
    private final JdbcTemplate jdbcTemplate;
    private final Path statementsDirectory;

    public StatementImportService(StatementTextExtractor textExtractor, List<StatementTransactionParser> transactionParsers, List<CardTermsExtractor> cardTermsExtractors, List<StatementSummaryExtractor> statementSummaryExtractors, com.pradeep.finance.transaction.TransactionCategorizer transactionCategorizer, com.pradeep.finance.transaction.TransferDetectionService transferDetectionService,
                                  com.pradeep.finance.account.AccountService accountService, JdbcTemplate jdbcTemplate,
                                  @Value("${finance.storage.statements-directory}") String statementsDirectory) {
        this.textExtractor = textExtractor;
        this.transactionParsers = transactionParsers;
        this.cardTermsExtractors = cardTermsExtractors;
        this.statementSummaryExtractors = statementSummaryExtractors;
        this.transactionCategorizer = transactionCategorizer;
        this.transferDetectionService = transferDetectionService;
        this.accountService = accountService;
        this.jdbcTemplate = jdbcTemplate;
        this.statementsDirectory = Path.of(statementsDirectory);
    }

    @Transactional
    public StatementImportResponse importStatement(MultipartFile statement) throws IOException {
        return importStatement(statement, ImportSource.MANUAL);
    }

    @Transactional
    public StatementImportResponse importStatement(MultipartFile statement, ImportSource importSource) throws IOException {
        validateFile(statement);
        String sourceHash = sha256(statement.getBytes());
        String text = textExtractor.extract(statement);
        List<ParsedTransaction> transactions = transactionParsers.stream()
                .filter(parser -> parser.supports(text))
                .findFirst()
                .map(parser -> parser.parse(text))
                .orElse(List.of());
        AccountIdentificationResponse accountIdentification = identifyAccount(text);
        captureCardTerms(text, accountIdentification);
        StatementSummary statementSummary = extractStatementSummary(text);
        String existingImportId = jdbcTemplate.query("SELECT id FROM statement_imports WHERE source_hash = ? LIMIT 1",
                resultSet -> resultSet.next() ? resultSet.getString(1) : null, sourceHash);
        if (existingImportId != null) {
            refreshStatementSummary(existingImportId, statementSummary);
            backfillPreviouslyEmptyImport(existingImportId, accountIdentification.account() == null ? null : accountIdentification.account().id(), transactions);
            return new StatementImportResponse(existingImportId, sanitizeFilename(statement.getOriginalFilename()), ImportStatus.REVIEW_REQUIRED,
                    accountIdentification, transactions, List.of("This exact statement was already imported. No duplicate transactions were added."));
        }
        String importId = UUID.randomUUID().toString();
        String originalFilename = sanitizeFilename(statement.getOriginalFilename());
        String storedFilename = importId + "-" + originalFilename;
        Files.createDirectories(statementsDirectory);
        Files.write(statementsDirectory.resolve(storedFilename), statement.getBytes());

        String accountId = accountIdentification.account() == null ? null : accountIdentification.account().id();
        Instant importedAt = Instant.now();
        jdbcTemplate.update("""
                INSERT INTO statement_imports (id, account_id, original_filename, stored_filename, source_type, import_source, status, imported_at, source_hash,
                statement_balance, credit_limit, available_credit, fees_charged, interest_charged, minimum_payment, payment_due_date, cycle_start_date, cycle_end_date,
                beginning_balance, total_credits, total_debits, interest_earned, annual_interest_rate, annual_percentage_yield)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, importId, accountId, originalFilename, storedFilename, sourceType(originalFilename), importSource.name(), ImportStatus.REVIEW_REQUIRED.name(), importedAt.toString(), sourceHash,
                statementSummary.statementBalance(), statementSummary.creditLimit(), statementSummary.availableCredit(), statementSummary.feesCharged(), statementSummary.interestCharged(), statementSummary.minimumPayment(),
                dateString(statementSummary.paymentDueDate()), dateString(statementSummary.cycleStartDate()), dateString(statementSummary.cycleEndDate()),
                statementSummary.beginningBalance(), statementSummary.totalCredits(), statementSummary.totalDebits(), statementSummary.interestEarned(), statementSummary.annualInterestRate(), statementSummary.annualPercentageYield());
        int duplicatesSkipped = 0;
        for (ParsedTransaction transaction : transactions) {
            if (accountId != null && transactionAlreadyExists(accountId, transaction)) {
                duplicatesSkipped++;
                continue;
            }
            jdbcTemplate.update("INSERT INTO transactions (id, import_id, account_id, transaction_date, description, amount, balance, category, status, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID().toString(), importId, accountId, transaction.date().toString(), transaction.description(), transaction.amount(), transaction.balance(), transactionCategorizer.categorize(transaction.description()), ImportStatus.REVIEW_REQUIRED.name(), importedAt.toString());
        }
        transferDetectionService.detectTransfers();

        List<String> warnings = new ArrayList<>();
        if (transactions.isEmpty()) warnings.add("No transactions could be read from this statement format yet. You can use a CSV export, or add a parser for this institution.");
        if (duplicatesSkipped > 0) warnings.add(duplicatesSkipped + " duplicate transaction" + (duplicatesSkipped == 1 ? " was" : "s were") + " skipped.");
        if (accountIdentification.status() == AccountIdentificationResponse.AccountMatchStatus.CONFIRMATION_REQUIRED) warnings.add("Account confirmation will be added to the review step before transactions can be finalized.");
        return new StatementImportResponse(importId, originalFilename, ImportStatus.REVIEW_REQUIRED, accountIdentification, transactions, warnings);
    }

    @Transactional(readOnly = true)
    public List<ImportHistoryItem> getHistory() {
        return jdbcTemplate.query("""
                SELECT si.id, si.original_filename, si.import_source, si.status, a.name,
                       COUNT(t.id) AS transaction_count, si.imported_at
                FROM statement_imports si
                LEFT JOIN accounts a ON a.id = si.account_id
                LEFT JOIN transactions t ON t.import_id = si.id
                GROUP BY si.id, si.original_filename, si.import_source, si.status, a.name, si.imported_at
                ORDER BY si.imported_at DESC
                LIMIT 50
                """, (rs, row) -> new ImportHistoryItem(
                rs.getString("id"), rs.getString("original_filename"),
                ImportSource.valueOf(rs.getString("import_source")), ImportStatus.valueOf(rs.getString("status")),
                rs.getString("name"), rs.getInt("transaction_count"), Instant.parse(rs.getString("imported_at"))));
    }

    private AccountIdentificationResponse identifyAccount(String text) {
        Matcher bankOfAmericaCardMatch = BOFA_CARD_NUMBER.matcher(text);
        if ((text.contains("Visa Signature") || text.contains("Total Credit Line")) && bankOfAmericaCardMatch.find()) {
            String lastFour = bankOfAmericaCardMatch.group(1);
            return accountService.identifyOrCreate(new AccountIdentificationRequest("Bank of America", AccountType.CREDIT_CARD, lastFour, "Bank of America •" + lastFour, "USD"));
        }
        Matcher bankOfAmericaAccountMatch = BOFA_ACCOUNT_NUMBER.matcher(text);
        if ((text.contains("Bank of America") || text.contains("BofA") || text.contains("Adv Plus Banking")) && bankOfAmericaAccountMatch.find()) {
            return accountService.identifyOrCreate(new AccountIdentificationRequest(
                    "Bank of America", AccountType.CHECKING, bankOfAmericaAccountMatch.group(1),
                    "Bank of America Checking •" + bankOfAmericaAccountMatch.group(1), "USD"));
        }
        Matcher wellsAccountMatch = WELLS_ACCOUNT_ENDING.matcher(text);
        Matcher wellsCheckingAccountMatch = WELLS_CHECKING_ACCOUNT.matcher(text);
        boolean wellsCardFound = wellsAccountMatch.find();
        boolean wellsCheckingFound = wellsCheckingAccountMatch.find();
        if (text.toUpperCase(Locale.ROOT).contains("WELLS FARGO") && (wellsCardFound || wellsCheckingFound)) {
            String lastFour = wellsCardFound ? wellsAccountMatch.group(1) : wellsCheckingAccountMatch.group(1);
            return accountService.identifyOrCreate(new AccountIdentificationRequest(
                    "Wells Fargo", text.contains("Everyday Checking") ? AccountType.CHECKING : AccountType.CREDIT_CARD,
                    lastFour, "Wells Fargo •" + lastFour, "USD"));
        }
        Matcher discoverAccountMatch = DISCOVER_ACCOUNT_ENDING.matcher(text);
        if (text.contains("DISCOVER IT CARD") && discoverAccountMatch.find()) {
            String lastFour = discoverAccountMatch.group(1);
            return accountService.identifyOrCreate(new AccountIdentificationRequest(
                    "Discover", AccountType.CREDIT_CARD, lastFour, "Discover •" + lastFour, "USD"));
        }
        Matcher amexBankingAccountMatch = AMEX_BANKING_ACCOUNT_ENDING.matcher(text);
        Matcher amexCheckingAccountMatch = AMEX_CHECKING_ACCOUNT_ENDING.matcher(text);
        boolean amexBankingFound = amexBankingAccountMatch.find();
        boolean amexCheckingFound = amexCheckingAccountMatch.find();
        if (text.contains("American Express National Bank") && (amexBankingFound || amexCheckingFound)) {
            String lastFour = amexBankingFound ? amexBankingAccountMatch.group(1) : amexCheckingAccountMatch.group(1);
            boolean savings = text.contains("High Yield Savings Account");
            return accountService.identifyOrCreate(new AccountIdentificationRequest(
                    "American Express", savings ? AccountType.SAVINGS : AccountType.CHECKING, lastFour,
                    "American Express " + (savings ? "Savings" : "Checking") + " •" + lastFour, "USD"));
        }
        Matcher amexAccountMatch = AMEX_ACCOUNT_ENDING.matcher(text);
        if (text.contains("American Express") && amexAccountMatch.find()) {
            String lastFour = amexAccountMatch.group(1);
            return accountService.identifyOrCreate(new AccountIdentificationRequest(
                    "American Express", AccountType.CREDIT_CARD, lastFour, "American Express •" + lastFour, "USD"));
        }
        Matcher institutionMatch = INSTITUTION.matcher(text);
        Matcher lastFourMatch = ACCOUNT_LAST_FOUR.matcher(text);
        if (!institutionMatch.find() || !lastFourMatch.find()) {
            return new AccountIdentificationResponse(AccountIdentificationResponse.AccountMatchStatus.CONFIRMATION_REQUIRED, null,
                    "The statement does not contain enough account information to match or create an account automatically.");
        }
        String institution = institutionMatch.group(1).trim();
        String lastFour = lastFourMatch.group(1);
        AccountType type = text.toLowerCase(Locale.ROOT).contains("credit card") ? AccountType.CREDIT_CARD : AccountType.CHECKING;
        return accountService.identifyOrCreate(new AccountIdentificationRequest(institution, type, lastFour, null, "USD"));
    }

    private void validateFile(MultipartFile statement) {
        if (statement == null || statement.isEmpty()) throw new IllegalArgumentException("Choose a statement file to upload.");
        String filename = statement.getOriginalFilename() == null ? "" : statement.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!filename.endsWith(".csv") && !filename.endsWith(".pdf")) throw new IllegalArgumentException("Only CSV and text-based PDF statements are supported.");
        if (statement.getSize() > 10 * 1024 * 1024) throw new IllegalArgumentException("Statements must be 10 MB or smaller.");
    }

    private String sanitizeFilename(String filename) { return (filename == null || filename.isBlank() ? "statement" : Path.of(filename).getFileName().toString()).replaceAll("[^a-zA-Z0-9._-]", "_"); }
    private String sourceType(String filename) { return filename.toLowerCase(Locale.ROOT).endsWith(".pdf") ? "PDF" : "CSV"; }

    @Transactional(readOnly = true)
    public ImportReview getReview(String importId) {
        String[] header = jdbcTemplate.query("SELECT original_filename, status FROM statement_imports WHERE id = ?", resultSet -> resultSet.next() ? new String[]{resultSet.getString(1), resultSet.getString(2)} : null, importId);
        if (header == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Import not found.");
        List<ReviewTransaction> rows = jdbcTemplate.query("SELECT id, transaction_date, description, amount, balance, status FROM transactions WHERE import_id = ? ORDER BY transaction_date, id", (rs, row) -> new ReviewTransaction(rs.getString(1), java.time.LocalDate.parse(rs.getString(2)), rs.getString(3), rs.getBigDecimal(4), rs.getBigDecimal(5), ImportStatus.valueOf(rs.getString(6))), importId);
        return new ImportReview(importId, header[0], ImportStatus.valueOf(header[1]), rows);
    }

    @Transactional
    public ReviewTransaction updateReviewTransaction(String importId, String transactionId, ReviewTransactionUpdate update) {
        ensureReviewable(importId);
        int changed = jdbcTemplate.update("UPDATE transactions SET transaction_date = ?, description = ?, amount = ?, balance = ? WHERE id = ? AND import_id = ?", update.date().toString(), update.description().trim(), update.amount(), update.balance(), transactionId, importId);
        if (changed == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found.");
        return jdbcTemplate.queryForObject("SELECT id, transaction_date, description, amount, balance, status FROM transactions WHERE id = ?", (rs, row) -> new ReviewTransaction(rs.getString(1), java.time.LocalDate.parse(rs.getString(2)), rs.getString(3), rs.getBigDecimal(4), rs.getBigDecimal(5), ImportStatus.valueOf(rs.getString(6))), transactionId);
    }

    @Transactional
    public void deleteReviewTransaction(String importId, String transactionId) {
        ensureReviewable(importId);
        if (jdbcTemplate.update("DELETE FROM transactions WHERE id = ? AND import_id = ?", transactionId, importId) == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found.");
    }

    @Transactional
    public ImportReview confirmImport(String importId) {
        ensureReviewable(importId);
        jdbcTemplate.update("UPDATE transactions SET status = ? WHERE import_id = ?", ImportStatus.CONFIRMED.name(), importId);
        jdbcTemplate.update("UPDATE statement_imports SET status = ? WHERE id = ?", ImportStatus.CONFIRMED.name(), importId);
        return getReview(importId);
    }

    /** Used only for a trusted, locally watched statement with a known account and parsed rows. */
    @Transactional
    public void confirmAutomatically(String importId) {
        jdbcTemplate.update("UPDATE transactions SET status = ? WHERE import_id = ? AND status = ?",
                ImportStatus.CONFIRMED.name(), importId, ImportStatus.REVIEW_REQUIRED.name());
        jdbcTemplate.update("UPDATE statement_imports SET status = ? WHERE id = ? AND status = ?",
                ImportStatus.CONFIRMED.name(), importId, ImportStatus.REVIEW_REQUIRED.name());
    }

    /** Reconciles older imports created before watched-folder imports were auto-confirmed. */
    @Transactional
    public int confirmTrustedImports() {
        jdbcTemplate.update("""
                UPDATE transactions SET status = ?
                WHERE status = ? AND import_id IN (
                    SELECT si.id FROM statement_imports si
                    WHERE si.status = ? AND si.account_id IS NOT NULL
                    AND EXISTS (SELECT 1 FROM transactions t WHERE t.import_id = si.id)
                )
                """, ImportStatus.CONFIRMED.name(), ImportStatus.REVIEW_REQUIRED.name(), ImportStatus.REVIEW_REQUIRED.name());
        return jdbcTemplate.update("""
                UPDATE statement_imports SET status = ?
                WHERE status = ? AND account_id IS NOT NULL
                AND EXISTS (SELECT 1 FROM transactions t WHERE t.import_id = statement_imports.id)
                """, ImportStatus.CONFIRMED.name(), ImportStatus.REVIEW_REQUIRED.name());
    }

    private void ensureReviewable(String importId) {
        String status = jdbcTemplate.query("SELECT status FROM statement_imports WHERE id = ?", rs -> rs.next() ? rs.getString(1) : null, importId);
        if (status == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Import not found.");
        if (ImportStatus.CONFIRMED.name().equals(status)) throw new ResponseStatusException(HttpStatus.CONFLICT, "This import has already been confirmed.");
    }

    private boolean transactionAlreadyExists(String accountId, ParsedTransaction transaction) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM transactions
                WHERE account_id = ? AND transaction_date = ? AND description = ? AND amount = ?
                """, Integer.class, accountId, transaction.date().toString(), transaction.description(), transaction.amount());
        return count != null && count > 0;
    }

    private StatementSummary extractStatementSummary(String text) {
        return statementSummaryExtractors.stream()
                .filter(extractor -> extractor.supports(text))
                .findFirst()
                .map(extractor -> extractor.extract(text))
                .orElseGet(StatementSummary::empty);
    }

    private String dateString(java.time.LocalDate date) { return date == null ? null : date.toString(); }

    private void refreshStatementSummary(String importId, StatementSummary summary) {
        jdbcTemplate.update("""
                UPDATE statement_imports SET statement_balance = ?, credit_limit = ?, available_credit = ?, fees_charged = ?, interest_charged = ?,
                minimum_payment = ?, payment_due_date = ?, cycle_start_date = ?, cycle_end_date = ?, beginning_balance = ?, total_credits = ?, total_debits = ?,
                interest_earned = ?, annual_interest_rate = ?, annual_percentage_yield = ? WHERE id = ?
                """, summary.statementBalance(), summary.creditLimit(), summary.availableCredit(), summary.feesCharged(), summary.interestCharged(),
                summary.minimumPayment(), dateString(summary.paymentDueDate()), dateString(summary.cycleStartDate()), dateString(summary.cycleEndDate()),
                summary.beginningBalance(), summary.totalCredits(), summary.totalDebits(), summary.interestEarned(), summary.annualInterestRate(), summary.annualPercentageYield(), importId);
    }

    private void backfillPreviouslyEmptyImport(String importId, String accountId, List<ParsedTransaction> transactions) {
        if (accountId == null || transactions.isEmpty()) return;
        Integer existingRows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM transactions WHERE import_id = ?", Integer.class, importId);
        if (existingRows == null || existingRows > 0) return;
        Instant importedAt = Instant.now();
        for (ParsedTransaction transaction : transactions) {
            jdbcTemplate.update("INSERT INTO transactions (id, import_id, account_id, transaction_date, description, amount, balance, category, status, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID().toString(), importId, accountId, transaction.date().toString(), transaction.description(), transaction.amount(), transaction.balance(), transactionCategorizer.categorize(transaction.description()), ImportStatus.REVIEW_REQUIRED.name(), importedAt.toString());
        }
        transferDetectionService.detectTransfers();
    }

    private void captureCardTerms(String text, AccountIdentificationResponse accountIdentification) {
        if (accountIdentification.account() == null || accountIdentification.account().accountType() != AccountType.CREDIT_CARD) return;
        cardTermsExtractors.stream()
                .filter(extractor -> extractor.supports(text))
                .findFirst()
                .ifPresent(extractor -> accountService.captureCardTerms(accountIdentification.account().id(), extractor.extract(text)));
    }

    private String sha256(byte[] content) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(content);
            StringBuilder result = new StringBuilder(hash.length * 2);
            for (byte value : hash) result.append(String.format("%02x", value));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }
}
