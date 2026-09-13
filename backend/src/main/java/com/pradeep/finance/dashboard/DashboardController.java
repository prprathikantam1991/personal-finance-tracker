package com.pradeep.finance.dashboard;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
@RestController @RequestMapping("/api/dashboard")
public class DashboardController {
    private final DashboardService dashboardService;
    public DashboardController(DashboardService dashboardService) { this.dashboardService = dashboardService; }
    @GetMapping("/summary") public DashboardSummary summary(@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from, @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to) { return dashboardService.summary(from, to); }
    @GetMapping("/monthly-trends") public java.util.List<MonthlyTrend> monthlyTrends(@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to, @RequestParam(defaultValue="6") int months) { return dashboardService.monthlyTrends(to, months); }
    @GetMapping("/merchant-spending") public java.util.List<MerchantSpending> merchantSpending(@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from, @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to) { return dashboardService.merchantSpending(from, to); }
}
