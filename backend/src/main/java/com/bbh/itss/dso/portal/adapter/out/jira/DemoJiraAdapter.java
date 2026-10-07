package com.bbh.itss.dso.portal.adapter.out.jira;

import com.bbh.itss.dso.portal.application.change.port.out.JiraPort;
import com.bbh.itss.dso.portal.domain.change.DateRange;
import com.bbh.itss.dso.portal.domain.change.JiraIssue;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static java.util.Collections.shuffle;
import static java.util.Comparator.comparing;
import static java.util.Map.entry;

@Component
@RequiredArgsConstructor
class DemoJiraAdapter implements JiraPort {

    static final int EPICS_PER_PROJECT = 8;
    static final int DAYS_BACK = 180;

    private static final Map<String, List<String>> THEMES = Map.ofEntries(
            entry("Single sign-on with BBH SSO", List.of("Redirect signed-out users to the SSO login",
                    "Map SSO groups to portal roles", "Sign out of every session at once", "Log every sign-in")),
            entry("Audit trail of user actions", List.of("Record each change with its user and time",
                    "Show the audit trail on the details page", "Export the audit trail to CSV",
                    "Keep audit records for seven years")),
            entry("Upgrade to Java 21 and Spring Boot 4", List.of("Build with the Java 21 toolchain",
                    "Replace the deprecated Spring APIs", "Run the regression suite on Java 21",
                    "Move to the UBI 9 base image")),
            entry("Accessibility fixes for WCAG 2.2 AA", List.of("Keyboard navigation in every dialog",
                    "Contrast of the status colours", "A label for every form field",
                    "Screen reader names for the buttons")),
            entry("Faster search", List.of("Index the search columns", "Page the results on the server",
                    "Cache the reference data", "Log the search timings")),
            entry("Daily reconciliation report", List.of("Compare positions with the custodian file",
                    "E-mail the breaks to operations", "Mark a break as explained", "Keep the reports for audit")),
            entry("Resilient calls to downstream systems", List.of("Retry time-outs with back-off",
                    "Circuit breaker for the pricing service", "Alert when a dependency is down",
                    "Dashboard of downstream latency")),
            entry("Data retention policy", List.of("Delete personal data after its retention period",
                    "Archive closed records every night", "Report the archived volumes",
                    "Legal hold for chosen records")),
            entry("Export to Excel", List.of("Export the main grid to XLSX", "Keep the column filters",
                    "Limit an export to 100 000 rows", "Record exports in the audit trail")),
            entry("Notifications", List.of("Daily e-mail digest of open items", "In-app notification centre",
                    "Unsubscribe link in every e-mail", "Microsoft Teams alert on failures")),
            entry("Vulnerability remediation", List.of("Upgrade Jackson to the patched release",
                    "Remove the unused Commons Collections", "Fix the SonarQube security hotspots",
                    "Pin the digest of the base image")),
            entry("Client self-service onboarding", List.of("Onboarding wizard for new clients",
                    "Validate the client reference data", "Send the welcome pack",
                    "Track the onboarding status")),
            entry("Month-end performance", List.of("Load test with month-end volumes",
                    "Tune the database connection pool", "Batch the ledger postings",
                    "Scale the API to four replicas")),
            entry("Reference data refresh", List.of("Load the new ISIN master file",
                    "Check currencies against ISO 4217", "Show the time of the last refresh",
                    "Alert on a missing daily file")));

    private final Clock clock;

    @Override
    public boolean connected() {
        return false;
    }

    @Override
    public List<JiraIssue> epics(String project, DateRange updated) {
        return project(project).stream()
                .filter(issue -> issue.epicKey() == null && updated.contains(issue.updated())).toList();
    }

    @Override
    public List<JiraIssue> stories(String project, Collection<String> epicKeys, DateRange updated) {
        return project(project).stream()
                .filter(issue -> epicKeys.contains(issue.epicKey()) && updated.contains(issue.updated())).toList();
    }

    @Override
    public List<JiraIssue> issues(String project, Collection<String> keys) {
        return project(project).stream().filter(issue -> keys.contains(issue.key())).toList();
    }

    List<JiraIssue> project(String project) {
        Random random = new Random(project.hashCode());
        LocalDate today = LocalDate.now(clock);
        List<String> themes = new ArrayList<>(THEMES.keySet().stream().sorted().toList());
        shuffle(themes, random);
        List<JiraIssue> issues = new ArrayList<>();
        int number = 100 + random.nextInt(400);
        for (String theme : themes.subList(0, EPICS_PER_PROJECT)) {
            LocalDate epicUpdated = today.minusDays(random.nextInt(DAYS_BACK));
            String epicKey = project + "-" + number++;
            issues.add(new JiraIssue(epicKey, theme, status(today, epicUpdated, random), null, epicUpdated));
            for (String story : THEMES.get(theme)) {
                if (random.nextInt(5) > 0) {
                    LocalDate storyUpdated = epicUpdated.minusDays(random.nextInt(30));
                    issues.add(new JiraIssue(project + "-" + number++, story, status(today, storyUpdated, random),
                            epicKey, storyUpdated));
                }
            }
        }
        issues.sort(comparing(JiraIssue::updated).reversed().thenComparing(JiraIssue::key));
        return issues;
    }

    private static String status(LocalDate today, LocalDate updated, Random random) {
        if (updated.isAfter(today.minusDays(10))) {
            return random.nextBoolean() ? "In Progress" : "In Review";
        }
        return "Done";
    }
}
