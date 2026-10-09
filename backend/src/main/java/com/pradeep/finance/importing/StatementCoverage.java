package com.pradeep.finance.importing;

import java.util.List;

public record StatementCoverage(int year, List<StatementCoverageAccount> accounts) {}
