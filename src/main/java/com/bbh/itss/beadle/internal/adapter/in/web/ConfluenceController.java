package com.bbh.itss.beadle.internal.adapter.in.web;

import com.bbh.itss.beadle.internal.application.confluence.port.in.ConfluenceUseCase;
import com.bbh.itss.beadle.internal.domain.confluence.ConfluencePage;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.springframework.http.ResponseEntity.created;
import static org.springframework.web.servlet.support.ServletUriComponentsBuilder.fromCurrentRequest;

@RestController
@RequestMapping("/api/internal/confluence/pages")
@RequiredArgsConstructor
public class ConfluenceController {

    private final ConfluenceUseCase confluence;

    @GetMapping
    public List<ConfluencePageSummary> find(@RequestParam(defaultValue = "") String spaceKey,
                                            @RequestParam(defaultValue = "") String title) {
        return confluence.find(spaceKey, title).stream().map(ConfluencePageSummary::of).toList();
    }

    @GetMapping("/{id}")
    public ConfluencePage get(@PathVariable String id) {
        return confluence.get(id);
    }

    @PostMapping
    public ResponseEntity<ConfluencePage> create(@RequestBody ConfluencePageRequest request) {
        ConfluencePage page = confluence.create(request.toPage());
        return created(fromCurrentRequest().path("/{id}").buildAndExpand(page.id()).toUri()).body(page);
    }

    @PutMapping("/{id}")
    public ConfluencePage update(@PathVariable String id, @RequestBody ConfluencePageUpdateRequest request) {
        return confluence.update(id, request.toUpdate());
    }
}
