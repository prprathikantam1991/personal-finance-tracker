package com.pradeep.finance.importing;

import java.time.Instant;

public record IncomingStatementItem(String filename, long sizeBytes, Instant modifiedAt, String status, String message) {}
