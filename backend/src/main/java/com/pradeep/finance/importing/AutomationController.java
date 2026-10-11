package com.pradeep.finance.importing;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/automation")
@ConditionalOnProperty(name = "finance.automation.enabled", havingValue = "true", matchIfMissing = true)
public class AutomationController {
    private final WatchedFolderImportService watchedFolderImportService;

    public AutomationController(WatchedFolderImportService watchedFolderImportService) {
        this.watchedFolderImportService = watchedFolderImportService;
    }

    @GetMapping("/incoming")
    public List<IncomingStatementItem> incoming() { return watchedFolderImportService.incomingStatements(); }
}
