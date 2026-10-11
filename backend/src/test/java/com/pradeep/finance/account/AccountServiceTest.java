package com.pradeep.finance.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.pradeep.finance.security.FinanceOwnerContext;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    private static final String OWNER = "test-owner";

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private AccountService accountService;

    @org.junit.jupiter.api.BeforeEach
    void establishOwner() {
        FinanceOwnerContext.set(OWNER);
    }

    @org.junit.jupiter.api.AfterEach
    void clearOwner() {
        FinanceOwnerContext.clear();
    }

    @Test
    void asksForConfirmationWhenLastFourDigitsAreUnavailable() {
        var request = new AccountIdentificationRequest("Example Bank", AccountType.CHECKING, null, null, null);

        var response = accountService.identifyOrCreate(request);

        assertThat(response.status()).isEqualTo(AccountIdentificationResponse.AccountMatchStatus.CONFIRMATION_REQUIRED);
        assertThat(response.account()).isNull();
    }

    @Test
    void createsAccountWhenNoIdentityMatchExists() {
        var request = new AccountIdentificationRequest("Example Bank", AccountType.CHECKING, "1234", null, "usd");
        when(accountRepository.findByOwnerUserIdAndIdentityKey(OWNER, "test-owner|example bank|1234")).thenReturn(Optional.empty());
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
            Account account = invocation.getArgument(0);
            account.initializePersistenceFields();
            return account;
        });

        var response = accountService.identifyOrCreate(request);

        assertThat(response.status()).isEqualTo(AccountIdentificationResponse.AccountMatchStatus.CREATED);
        assertThat(response.account().institution()).isEqualTo("example bank");
        assertThat(response.account().lastFour()).isEqualTo("1234");
        assertThat(response.account().currency()).isEqualTo("USD");
        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        org.mockito.Mockito.verify(accountRepository).save(accountCaptor.capture());
        assertThat(accountCaptor.getValue().getOwnerUserId()).isEqualTo(OWNER);
    }

    @Test
    void matchesSoleInstitutionAndAccountTypeWhenStatementOmitsLastFour() {
        Account checking = new Account("American Express Checking •3185", "american express", AccountType.CHECKING,
                "3185", "american express|3185", "USD", OWNER);
        checking.initializePersistenceFields();
        when(accountRepository.findAllByOwnerUserIdOrderByInstitutionAscNameAsc(OWNER)).thenReturn(List.of(checking));

        var response = accountService.identifySoleAccount("American Express", AccountType.CHECKING);

        assertThat(response.status()).isEqualTo(AccountIdentificationResponse.AccountMatchStatus.MATCHED);
        assertThat(response.account().lastFour()).isEqualTo("3185");
    }

    @Test
    void requestsConfirmationWhenMoreThanOneCompatibleAccountExists() {
        Account first = new Account("American Express Checking •3185", "american express", AccountType.CHECKING,
                "3185", "american express|3185", "USD", OWNER);
        Account second = new Account("American Express Checking •1234", "american express", AccountType.CHECKING,
                "1234", "american express|1234", "USD", OWNER);
        when(accountRepository.findAllByOwnerUserIdOrderByInstitutionAscNameAsc(OWNER)).thenReturn(List.of(first, second));

        var response = accountService.identifySoleAccount("American Express", AccountType.CHECKING);

        assertThat(response.status()).isEqualTo(AccountIdentificationResponse.AccountMatchStatus.CONFIRMATION_REQUIRED);
    }

    @Test
    void archivesLegacyWellsAutographInsteadOfPresentingItAsAnActiveWellsCard() {
        Account legacy = new Account("Wells Fargo Autograph •9484", "wells fargo", AccountType.CREDIT_CARD,
                "9484", "test-owner|wells fargo|9484", "USD", OWNER);
        legacy.initializePersistenceFields();
        when(accountRepository.findByOwnerUserIdAndIdentityKey(OWNER, "test-owner|bilt|9484")).thenReturn(Optional.empty());
        when(accountRepository.findByOwnerUserIdAndIdentityKey(OWNER, "test-owner|wells fargo|9484")).thenReturn(Optional.of(legacy));

        var response = accountService.preserveLegacyBiltHistory("9484");

        assertThat(response.status()).isEqualTo(AccountIdentificationResponse.AccountMatchStatus.MATCHED);
        assertThat(legacy.getName()).isEqualTo("Legacy Bilt •9484");
        assertThat(legacy.getInstitution()).isEqualTo("bilt");
        assertThat(legacy.isArchived()).isTrue();
        assertThat(response.message()).contains("excluded from active accounts");
    }
}
