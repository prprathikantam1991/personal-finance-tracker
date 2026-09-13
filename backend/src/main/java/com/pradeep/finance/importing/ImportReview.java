package com.pradeep.finance.importing;

import java.util.List;

public record ImportReview(String importId, String originalFilename, ImportStatus status, List<ReviewTransaction> transactions) {}
