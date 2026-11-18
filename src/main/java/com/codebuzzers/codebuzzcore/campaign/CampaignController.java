package com.codebuzzers.codebuzzcore.campaign;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/campaigns")
public class CampaignController {

    private final CampaignQueryService campaigns;

    public CampaignController(CampaignQueryService campaigns) {
        this.campaigns = campaigns;
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        return campaigns.list();
    }

    @GetMapping("/report")
    public Map<String, Object> report(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to) {
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.minusDays(7) : from;
        return campaigns.report(start, end);
    }

    /** Totals, lead funnel, daily series and ad sets of one campaign (id = Meta campaign id). */
    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable String id, @RequestParam(required = false) LocalDate from,
                                      @RequestParam(required = false) LocalDate to) {
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        return campaigns.detail(id, start, end);
    }
}
