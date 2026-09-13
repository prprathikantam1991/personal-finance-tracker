package com.pradeep.finance.account;

import java.util.List;

public record AccountHistoryResponse(AccountOverviewResponse account, List<StatementSnapshot> snapshots) { }
