package com.pradeep.finance.transaction;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import com.pradeep.finance.account.AccountType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionService {
    private final JdbcTemplate jdbcTemplate;
    private final CategoryRuleService categoryRuleService;
    private final MerchantNormalizer merchantNormalizer;

    public TransactionService(JdbcTemplate jdbcTemplate, CategoryRuleService categoryRuleService, MerchantNormalizer merchantNormalizer) {
        this.jdbcTemplate = jdbcTemplate;
        this.categoryRuleService = categoryRuleService;
        this.merchantNormalizer = merchantNormalizer;
    }

    public List<TransactionResponse> list(String accountId, LocalDate from, LocalDate to) {
        StringBuilder sql = new StringBuilder("""
                SELECT t.id, t.account_id, a.name AS account_name, a.account_type, t.transaction_date,
                       t.description, t.amount, t.balance, t.category, t.transfer_group_id, t.status
                FROM transactions t
                LEFT JOIN accounts a ON a.id = t.account_id
                WHERE 1 = 1
                """);
        List<Object> parameters = new ArrayList<>();
        if (accountId != null && !accountId.isBlank()) {
            sql.append(" AND t.account_id = ?");
            parameters.add(accountId);
        }
        if (from != null) {
            sql.append(" AND t.transaction_date >= ?");
            parameters.add(from.toString());
        }
        if (to != null) {
            sql.append(" AND t.transaction_date <= ?");
            parameters.add(to.toString());
        }
        sql.append(" ORDER BY t.transaction_date DESC, t.created_at DESC, t.id DESC");
        return jdbcTemplate.query(sql.toString(), (resultSet, rowNum) -> new TransactionResponse(
                resultSet.getString("id"),
                resultSet.getString("account_id"),
                resultSet.getString("account_name"),
                resultSet.getString("account_type") == null ? null : AccountType.valueOf(resultSet.getString("account_type")),
                LocalDate.parse(resultSet.getString("transaction_date")),
                resultSet.getString("description"),
                merchantNormalizer.normalize(resultSet.getString("description")),
                resultSet.getBigDecimal("amount"),
                resultSet.getBigDecimal("balance"),
                resultSet.getString("category"),
                confidence(resultSet.getString("category"), resultSet.getString("transfer_group_id"), resultSet.getString("description")),
                resultSet.getString("transfer_group_id"),
                resultSet.getString("status")
        ), parameters.toArray());
    }

    @Transactional
    public TransactionResponse updateCategory(String transactionId, CategoryUpdateRequest request) {
        String description = jdbcTemplate.query("SELECT description FROM transactions WHERE id = ?", rs -> rs.next() ? rs.getString(1) : null, transactionId);
        if (description == null) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Transaction not found.");
        jdbcTemplate.update("UPDATE transactions SET category = ? WHERE id = ?", request.category().trim(), transactionId);
        if (request.rememberForFuture()) categoryRuleService.remember(description, request.category().trim());
        return transactionResponse(transactionId);
    }

    @Transactional
    public BulkCategoryUpdateResponse updateCategories(BulkCategoryUpdateRequest request) {
        List<String> ids = new ArrayList<>(new LinkedHashSet<>(request.transactionIds()));
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        List<String> eligibleIds = jdbcTemplate.query("""
                SELECT id FROM transactions
                WHERE id IN (""" + placeholders + ") AND transfer_group_id IS NULL AND category <> 'Transfer'", (rs, row) -> rs.getString(1), ids.toArray());
        String category = request.category().trim();
        for (String id : eligibleIds) {
            jdbcTemplate.update("UPDATE transactions SET category = ? WHERE id = ?", category, id);
            if (request.rememberForFuture()) {
                String description = jdbcTemplate.query("SELECT description FROM transactions WHERE id = ?", rs -> rs.next() ? rs.getString(1) : null, id);
                if (description != null) categoryRuleService.remember(description, category);
            }
        }
        return new BulkCategoryUpdateResponse(eligibleIds.stream().map(this::transactionResponse).toList(), ids.size() - eligibleIds.size());
    }

    private TransactionResponse transactionResponse(String transactionId) {
        return jdbcTemplate.queryForObject("""
                SELECT t.id, t.account_id, a.name AS account_name, a.account_type, t.transaction_date, t.description, t.amount, t.balance, t.category, t.transfer_group_id, t.status
                FROM transactions t LEFT JOIN accounts a ON a.id = t.account_id WHERE t.id = ?
                """, (rs, row) -> new TransactionResponse(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4) == null ? null : AccountType.valueOf(rs.getString(4)), LocalDate.parse(rs.getString(5)), rs.getString(6), merchantNormalizer.normalize(rs.getString(6)), rs.getBigDecimal(7), rs.getBigDecimal(8), rs.getString(9), confidence(rs.getString(9), rs.getString(10), rs.getString(6)), rs.getString(10), rs.getString(11)), transactionId);
    }

    private String confidence(String category, String transferGroupId, String description) {
        if (transferGroupId != null || "Transfer".equals(category) || categoryRuleService.hasMerchantRule(description)) return "HIGH";
        if ("Uncategorized".equals(category)) return "NEEDS_REVIEW";
        return "MEDIUM";
    }
}
