package com.bbh.itss.beadle.internal.adapter.in.web;

import com.bbh.itss.beadle.internal.application.jira.port.in.JiraUseCase;
import com.bbh.itss.beadle.internal.domain.jira.JiraIssue;
import com.bbh.itss.beadle.internal.domain.jira.JiraVersion;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/internal/jira/projects/{key}")
@RequiredArgsConstructor
public class JiraController {

    private final JiraUseCase jira;

    @GetMapping("/versions")
    public List<JiraVersion> versions(@PathVariable String key) {
        return jira.versions(key);
    }

    @GetMapping("/epics")
    public List<JiraIssue> epics(@PathVariable String key, @RequestParam(defaultValue = "") String fixVersion) {
        return jira.epics(key, fixVersion);
    }

    @GetMapping("/stories")
    public List<JiraIssue> stories(@PathVariable String key, @RequestParam(defaultValue = "") String fixVersion,
                                   @RequestParam(defaultValue = "") List<String> epicKeys) {
        return jira.stories(key, fixVersion, epicKeys);
    }
}
