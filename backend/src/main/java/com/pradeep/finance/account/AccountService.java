package com.pradeep.finance.account;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> listAccounts() {
        return accountRepository.findAllByOrderByInstitutionAscNameAsc().stream()
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

        return accountRepository.findByIdentityKey(identityKey)
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
        accountRepository.findById(accountId).ifPresent(account -> {
            if (account.getAccountType() == AccountType.CREDIT_CARD) account.captureCardTerms(terms);
        });
    }

    /** Preserves one credit-line history when the legacy Bilt card became a Wells Fargo Autograph card. */
    @Transactional
    public AccountIdentificationResponse reconcileBiltToWellsAutograph(String lastFour) {
        String wellsIdentity = buildIdentityKey("wells fargo", normalizeLastFour(lastFour));
        var existingWells = accountRepository.findByIdentityKey(wellsIdentity);
        if (existingWells.isPresent()) return new AccountIdentificationResponse(AccountIdentificationResponse.AccountMatchStatus.MATCHED,
                AccountResponse.from(existingWells.get()), "Matched the converted Wells Fargo Autograph account.");
        String biltIdentity = buildIdentityKey("bilt", normalizeLastFour(lastFour));
        return accountRepository.findByIdentityKey(biltIdentity).map(account -> {
            account.rebrand("Wells Fargo Autograph •" + lastFour, "wells fargo", wellsIdentity);
            return new AccountIdentificationResponse(AccountIdentificationResponse.AccountMatchStatus.MATCHED,
                    AccountResponse.from(account), "Preserved legacy Bilt history on the converted Wells Fargo Autograph account.");
        }).orElseGet(() -> createAccount(new AccountIdentificationRequest("Wells Fargo", AccountType.CREDIT_CARD,
                lastFour, "Wells Fargo Autograph •" + lastFour, "USD"), "wells fargo", lastFour, wellsIdentity));
    }

    @Transactional(readOnly = true)
    public AccountIdentificationResponse identifyExistingBiltCreditCard() {
        List<Account> biltCards = accountRepository.findAllByOrderByInstitutionAscNameAsc().stream()
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
        List<Account> matches = accountRepository.findAllByOrderByInstitutionAscNameAsc().stream()
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
        Account account = accountRepository.save(new Account(name, institution, request.accountType(), lastFour, identityKey, currency));

        return new AccountIdentificationResponse(
                AccountIdentificationResponse.AccountMatchStatus.CREATED,
                AccountResponse.from(account),
                "Created a new account using institution and last four digits."
        );
    }

    private String buildIdentityKey(String institution, String lastFour) {
        return institution == null || lastFour == null ? null : institution + "|" + lastFour;
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
}
