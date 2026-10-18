package com.bbh.itss.beadle.internal.adapter.out.confluence;

import com.bbh.itss.beadle.internal.adapter.out.http.ConditionalOnSystemUrl;
import com.bbh.itss.beadle.internal.application.confluence.port.out.ConfluencePort;
import com.bbh.itss.beadle.internal.domain.confluence.ConfluencePage;
import com.bbh.itss.beadle.internal.domain.confluence.ConfluencePageUpdate;
import com.bbh.itss.beadle.internal.domain.confluence.NewConfluencePage;
import com.bbh.itss.beadle.internal.domain.shared.RefusedException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static com.bbh.itss.beadle.internal.domain.shared.Failures.notFound;
import static com.bbh.itss.beadle.internal.domain.shared.Failures.staleVersion;
import static java.util.Comparator.comparing;
import static org.apache.commons.lang3.Strings.CI;

@Component
@Profile("local")
@ConditionalOnSystemUrl(value = "confluence", set = false)
class DemoConfluenceAdapter implements ConfluencePort {

    static final String DUPLICATE = "Confluence already has a page titled ";

    private final AtomicLong ids = new AtomicLong(100_000);
    private final Map<String, ConfluencePage> pages = new LinkedHashMap<>();

    DemoConfluenceAdapter() {
        seed("DSO", "Beadle release notes", 3, "<h2>Beadle 1.0</h2><p>First release of the change tooling.</p>");
        seed("DSO", "Change evidence template", 1,
                "<h2>Change</h2><p>Number, FixVersion, epics and stories.</p><h2>Evidence</h2><p>Test cycle summary.</p>");
        seed("CERT", "CertScanner 1.4 release", 2, "<p>Release notes of CertScanner 1.4.</p>");
    }

    @Override
    public boolean connected() {
        return false;
    }

    @Override
    public List<ConfluencePage> find(String spaceKey, String title) {
        synchronized (pages) {
            return pages.values().stream()
                    .filter(page -> CI.equals(page.spaceKey(), spaceKey) && CI.equals(page.title(), title))
                    .map(ConfluencePage::withoutBody).sorted(comparing(ConfluencePage::title)).toList();
        }
    }

    @Override
    public ConfluencePage get(String id) {
        synchronized (pages) {
            return existing(id);
        }
    }

    @Override
    public ConfluencePage create(NewConfluencePage page) {
        synchronized (pages) {
            refuseDuplicate(page.spaceKey(), page.title(), null);
            ConfluencePage created = new ConfluencePage(String.valueOf(ids.incrementAndGet()),
                    page.spaceKey().toUpperCase(), page.title(), 1, null, page.body());
            pages.put(created.id(), created);
            return created;
        }
    }

    @Override
    public ConfluencePage update(String id, ConfluencePageUpdate update) {
        synchronized (pages) {
            ConfluencePage current = existing(id);
            if (!update.replaces(current)) {
                throw staleVersion();
            }
            refuseDuplicate(current.spaceKey(), update.title(), id);
            ConfluencePage replaced = current.replacedBy(update);
            pages.put(id, replaced);
            return replaced;
        }
    }

    private ConfluencePage existing(String id) {
        ConfluencePage page = pages.get(id);
        if (page == null) {
            throw notFound(ConfluencePage.NAME, id);
        }
        return page;
    }

    private void refuseDuplicate(String spaceKey, String title, String exceptId) {
        boolean taken = pages.values().stream().anyMatch(page -> !page.id().equals(exceptId)
                && CI.equals(page.spaceKey(), spaceKey) && CI.equals(page.title(), title));
        if (taken) {
            throw new RefusedException(DUPLICATE + title + " in space " + spaceKey.toUpperCase());
        }
    }

    private void seed(String spaceKey, String title, int version, String body) {
        String id = String.valueOf(ids.incrementAndGet());
        pages.put(id, new ConfluencePage(id, spaceKey, title, version, null, body));
    }
}
