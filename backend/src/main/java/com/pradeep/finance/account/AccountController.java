package com.pradeep.finance.account;

import java.util.List;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;
    private final AccountOverviewService accountOverviewService;

    public AccountController(AccountService accountService, AccountOverviewService accountOverviewService) {
        this.accountService = accountService; this.accountOverviewService = accountOverviewService;
    }

    @GetMapping
    public List<AccountResponse> listAccounts() {
        return accountService.listAccounts();
    }

    @GetMapping("/overview") public List<AccountOverviewResponse> overview() { return accountOverviewService.list(); }
    @GetMapping("/{accountId}/history") public AccountHistoryResponse history(@PathVariable String accountId) { return accountOverviewService.history(accountId); }

    @PostMapping("/identify")
    @ResponseStatus(HttpStatus.OK)
    public AccountIdentificationResponse identifyOrCreate(@Valid @RequestBody AccountIdentificationRequest request) {
        return accountService.identifyOrCreate(request);
    }

}
