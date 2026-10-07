package com.bbh.itss.dso.portal.adapter.out.jira;

import com.bbh.itss.dso.portal.application.change.port.out.JiraPort;
import com.bbh.itss.dso.portal.domain.change.JiraIssue;
import com.bbh.itss.dso.portal.domain.change.JiraVersion;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toSet;

@Component
class DemoJiraAdapter implements JiraPort {

    static final int EPICS_PER_PROJECT = 8;
    static final List<String> UPCOMING_STATUSES = List.of("Done", "In Review", "In Progress");
    static final List<String> LATER_STATUSES = List.of("To Do", "In Progress");

    private static final Map<String, List<String>> THEMES = Map.ofEntries(
            Map.entry("Single sign-on with BBH SSO", List.of("Redirect signed-out users to the SSO login",
                    "Map SSO groups to portal roles", "Sign out of every session at once", "Log every sign-in")),
            Map.entry("Audit trail of user actions", List.of("Record each change with its user and time",
                    "Show the audit trail on the details page", "Export the audit trail to CSV",
                    "Keep audit records for seven years")),
            Map.entry("Upgrade to Java 21 and Spring Boot 4", List.of("Build with the Java 21 toolchain",
                    "Replace the deprecated Spring APIs", "Run the regression suite on Java 21",
                    "Move to the UBI 9 base image")),
            Map.entry("Accessibility fixes for WCAG 2.2 AA", List.of("Keyboard navigation in every dialog",
                    "Contrast of the status colours", "A label for every form field",
                    "Screen reader names for the buttons")),
            Map.entry("Faster search", List.of("Index the search columns", "Page the results on the server",
                    "Cache the reference data", "Log the search timings")),
            Map.entry("Daily reconciliation report", List.of("Compare positions with the custodian file",
                    "E-mail the breaks to operations", "Mark a break as explained", "Keep the reports for audit")),
            Map.entry("Resilient calls to downstream systems", List.of("Retry time-outs with back-off",
                    "Circuit breaker for the pricing service", "Alert when a dependency is down",
                    "Dashboard of downstream latency")),
            Map.entry("Data retention policy", List.of("Delete personal data after its retention period",
                    "Archive closed records every night", "Report the archived volumes",
                    "Legal hold for chosen records")),
            Map.entry("Export to Excel", List.of("Export the main grid to XLSX", "Keep the column filters",
                    "Limit an export to 100 000 rows", "Record exports in the audit trail")),
            Map.entry("Notifications", List.of("Daily e-mail digest of open items", "In-app notification centre",
                    "Unsubscribe link in every e-mail", "Microsoft Teams alert on failures")),
            Map.entry("Vulnerability remediation", List.of("Upgrade Jackson to the patched release",
                    "Remove the unused Commons Collections", "Fix the SonarQube security hotspots",
                    "Pin the digest of the base image")),
            Map.entry("Client self-service onboarding", List.of("Onboarding wizard for new clients",
                    "Validate the client reference data", "Send the welcome pack",
                    "Track the onboarding status")),
            Map.entry("Month-end performance", List.of("Load test with month-end volumes",
                    "Tune the database connection pool", "Batch the ledger postings",
                    "Scale the API to four replicas")),
            Map.entry("Reference data refresh", List.of("Load the new ISIN master file",
                    "Check currencies against ISO 4217", "Show the time of the last refresh",
                    "Alert on a missing daily file")));

    private final Clock clock;

    DemoJiraAdapter(Clock clock) {
        this.clock = clock;
    }

    @Override
    public boolean connected() {
        return false;
    }

    @Override
    public List<JiraVersion> versions(String project) {
        return project(project).versions();
    }

    @Override
    public List<JiraIssue> epics(String project, String fixVersion) {
        List<DemoIssue> issues = project(project).issues();
        Set<String> epicKeys = issues.stream().filter(issue -> issue.in(fixVersion)).map(DemoIssue::epicKey)
                .collect(toSet());
        return issues.stream().map(DemoIssue::issue)
                .filter(issue -> issue.epicKey() == null && epicKeys.contains(issue.key())).toList();
    }

    @Override
    public List<JiraIssue> stories(String project, String fixVersion, Collection<String> epicKeys) {
        return project(project).issues().stream().filter(issue -> issue.in(fixVersion)).map(DemoIssue::issue)
                .filter(issue -> epicKeys.contains(issue.epicKey())).toList();
    }

    DemoProject project(String project) {
        Random random = new Random(project.hashCode());
        LocalDate today = LocalDate.now(clock);
        List<JiraVersion> versions = versionsOf(project, today, random);
        List<String> themes = new ArrayList<>(THEMES.keySet().stream().sorted().toList());
        Collections.shuffle(themes, random);
        List<DemoIssue> issues = new ArrayList<>();
        int number = 100 + random.nextInt(400);
        for (int index = 0; index < EPICS_PER_PROJECT; index++) {
            String theme = themes.get(index);
            int release = index * versions.size() / EPICS_PER_PROJECT;
            String epicKey = project + "-" + number++;
            issues.add(issue(epicKey, theme, null, versions.get(release), today, random));
            for (String story : THEMES.get(theme)) {
                if (random.nextInt(5) > 0) {
                    boolean slipped = release + 1 < versions.size() && random.nextInt(4) == 0;
                    issues.add(issue(project + "-" + number++, story, epicKey,
                            versions.get(slipped ? release + 1 : release), today, random));
                }
            }
        }
        issues.sort(comparing((DemoIssue issue) -> issue.issue().updated()).reversed()
                .thenComparing(issue -> issue.issue().key()));
        return new DemoProject(versions, issues);
    }

    private static List<JiraVersion> versionsOf(String project, LocalDate today, Random random) {
        String release = project + " " + (1 + random.nextInt(5)) + ".";
        int minor = random.nextInt(4);
        List<JiraVersion> versions = new ArrayList<>(List.of(
                new JiraVersion(release + minor, true, today.minusDays(70 + random.nextInt(30))),
                new JiraVersion(release + (minor + 1), true, today.minusDays(14 + random.nextInt(21))),
                new JiraVersion(release + (minor + 2), false, today.plusDays(7 + random.nextInt(14)))));
        if (random.nextBoolean()) {
            versions.add(new JiraVersion(release + (minor + 3), false, null));
        }
        return versions;
    }

    private static DemoIssue issue(String key, String summary, String epicKey, JiraVersion version,
                                   LocalDate today, Random random) {
        LocalDate updated = version.released() ? version.releaseDate().minusDays(random.nextInt(20))
                : today.minusDays(random.nextInt(10));
        String status = version.released() ? "Done" : version.releaseDate() != null
                ? UPCOMING_STATUSES.get(random.nextInt(UPCOMING_STATUSES.size()))
                : LATER_STATUSES.get(random.nextInt(LATER_STATUSES.size()));
        return new DemoIssue(new JiraIssue(key, summary, status, epicKey, updated), version.name());
    }

    record DemoProject(List<JiraVersion> versions, List<DemoIssue> issues) {
    }

    record DemoIssue(JiraIssue issue, String fixVersion) {

        boolean in(String version) {
            return fixVersion.equalsIgnoreCase(version.trim());
        }

        String epicKey() {
            return issue.epicKey() == null ? issue.key() : issue.epicKey();
        }
    }
}
