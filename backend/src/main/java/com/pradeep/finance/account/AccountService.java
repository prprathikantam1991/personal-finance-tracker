package com.pradeep.finance.account;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pradeep.finance.security.FinanceOwnerContext;

@Service
public class AccountService {

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> listAccounts() {
        return activeAccountsForCurrentOwner().stream()
                .map(AccountResponse::from)
                .toList();
    }

    @Transactional
    public AccountIdentificationResponse identifyOrCreate(AccountIdentificationRequest request) {
        String institution = normalizeInstitution(request.institution());
        String lastFour = normalizeLastFour(request.lastFour());
        String identityKey = buildIdentityKey(institution, lastFour);

        if (identityKey == null) {
            return new AccountIdentificationResponse(
                    AccountIdentificationResponse.AccountMatchStatus.CONFIRMATION_REQUIRED,
                    null,
                    "The statement does not contain enough account information to match or create an account automatically."
            );
        }

        return accountRepository.findByOwnerUserIdAndIdentityKey(currentOwnerUserId(), identityKey)
                .map(account -> new AccountIdentificationResponse(
                        AccountIdentificationResponse.AccountMatchStatus.MATCHED,
                        AccountResponse.from(account),
                        "Matched the account using institution and last four digits."
                ))
                .orElseGet(() -> createAccount(request, institution, lastFour, identityKey));
    }

    @Transactional
    public void captureCardTerms(String accountId, ExtractedCardTerms terms) {
        if (accountId == null || !terms.hasValues()) return;
        accountRepository.findById(accountId).filter(account -> currentOwnerUserId().equals(account.getOwnerUserId())).ifPresent(account -> {
            if (account.getAccountType() == AccountType.CREDIT_CARD) account.captureCardTerms(terms);
        });
    }

    /**
     * Preserves history from the old Bilt card without treating it as a current Wells Fargo card.
     * The old account remains available through its imported ledger, but is excluded from active
     * account cards, statement coverage, and credit-utilization calculations.
     */
    @Transactional
    public AccountIdentificationResponse preserveLegacyBiltHistory(String lastFour) {
        String biltIdentity = buildIdentityKey("bilt", normalizeLastFour(lastFour));
        return accountRepository.findByOwnerUserIdAndIdentityKey(currentOwnerUserId(), biltIdentity).map(account -> {
            account.archive();
            return new AccountIdentificationResponse(AccountIdentificationResponse.AccountMatchStatus.MATCHED,
                    AccountResponse.from(account), "Preserved legacy Bilt history; it is excluded from active accounts.");
        }).orElseGet(() -> {
            String wellsIdentity = buildIdentityKey("wells fargo", normalizeLastFour(lastFour));
            return accountRepository.findByOwnerUserIdAndIdentityKey(currentOwnerUserId(), wellsIdentity).map(account -> {
                account.rebrand("Legacy Bilt •" + lastFour, "bilt", biltIdentity);
                account.archive();
                return new AccountIdentificationResponse(AccountIdentificationResponse.AccountMatchStatus.MATCHED,
                        AccountResponse.from(account), "Preserved legacy Bilt history; it is excluded from active accounts.");
            }).orElseGet(() -> {
                AccountIdentificationResponse response = createAccount(new AccountIdentificationRequest("Bilt", AccountType.CREDIT_CARD,
                        lastFour, "Legacy Bilt •" + lastFour, "USD"), "bilt", lastFour, biltIdentity);
                accountRepository.findById(response.account().id()).ifPresent(Account::archive);
                return new AccountIdentificationResponse(response.status(), response.account(),
                        "Preserved legacy Bilt history; it is excluded from active accounts.");
            });
        });
    }

    @Transactional(readOnly = true)
    public AccountIdentificationResponse identifyExistingBiltCreditCard() {
        List<Account> biltCards = activeAccountsForCurrentOwner().stream()
                .filter(account -> "bilt".equalsIgnoreCase(account.getInstitution()))
                .filter(account -> account.getAccountType() == AccountType.CREDIT_CARD).toList();
        if (biltCards.size() == 1) return new AccountIdentificationResponse(AccountIdentificationResponse.AccountMatchStatus.MATCHED,
                AccountResponse.from(biltCards.getFirst()), "Matched the sole saved Bilt credit-card account.");
        return new AccountIdentificationResponse(AccountIdentificationResponse.AccountMatchStatus.CONFIRMATION_REQUIRED, null,
                "This Bilt statement does not show a card ending, so it cannot be matched safely when multiple Bilt cards exist.");
    }

    /**
     * Some banking statement layouts omit a visible account ending. A fallback is safe only when
     * exactly one saved account has the stated institution and type; otherwise import review is
     * required rather than guessing.
     */
    @Transactional(readOnly = true)
    public AccountIdentificationResponse identifySoleAccount(String institution, AccountType accountType) {
        String normalizedInstitution = normalizeInstitution(institution);
        List<Account> matches = activeAccountsForCurrentOwner().stream()
                .filter(account -> normalizedInstitution.equalsIgnoreCase(account.getInstitution()))
                .filter(account -> accountType == account.getAccountType()).toList();
        if (matches.size() == 1) return new AccountIdentificationResponse(AccountIdentificationResponse.AccountMatchStatus.MATCHED,
                AccountResponse.from(matches.getFirst()), "Matched the sole saved " + institution + " " + accountType.name().toLowerCase(Locale.ROOT).replace('_', ' ') + " account.");
        return new AccountIdentificationResponse(AccountIdentificationResponse.AccountMatchStatus.CONFIRMATION_REQUIRED, null,
                "The statement does not show an account ending, so it cannot be matched safely when multiple compatible accounts exist.");
    }

    private AccountIdentificationResponse createAccount(
            AccountIdentificationRequest request,
            String institution,
            String lastFour,
            String identityKey
    ) {
        String name = hasText(request.suggestedName())
                ? request.suggestedName().trim()
                : institution + " " + request.accountType().name().toLowerCase(Locale.ROOT).replace('_', ' ') + " •" + lastFour;
        String currency = hasText(request.currency()) ? request.currency().trim().toUpperCase(Locale.ROOT) : "USD";
        Account account = accountRepository.save(new Account(name, institution, request.accountType(), lastFour, identityKey, currency, currentOwnerUserId()));

        return new AccountIdentificationResponse(
                AccountIdentificationResponse.AccountMatchStatus.CREATED,
                AccountResponse.from(account),
                "Created a new account using institution and last four digits."
        );
    }

    private String buildIdentityKey(String institution, String lastFour) {
        // Keep the existing SQLite uniqueness constraint meaningful in a multi-user
        // ledger: the same institution/account ending may legitimately exist for two owners.
        return institution == null || lastFour == null ? null : currentOwnerUserId() + "|" + institution + "|" + lastFour;
    }

    private String normalizeInstitution(String value) {
        return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private String normalizeLastFour(String value) {
        return hasText(value) && value.matches("\\d{4}") ? value : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String currentOwnerUserId() {
        return FinanceOwnerContext.requireOwnerUserId();
    }

    private List<Account> accountsForCurrentOwner() {
        return accountRepository.findAllByOwnerUserIdOrderByInstitutionAscNameAsc(currentOwnerUserId());
    }

    private List<Account> activeAccountsForCurrentOwner() {
        return accountsForCurrentOwner().stream().filter(account -> !account.isArchived()).toList();
    }
}
