package com.bbh.itss.beadle.internal.config

import org.yaml.snakeyaml.Yaml
import spock.lang.Specification

import static groovy.io.FileType.FILES

class OpenShiftManifestsSpec extends Specification {

    static final File MANIFESTS = new File('deploy/openshift/beadle-internal')

    def "nothing of Beadle-Internal is published outside the cluster, as only Beadle-GUI calls it: #file"() {
        expect:
        documents(file).every { !(it.kind in ['Route', 'Ingress']) }

        where:
        file << manifests()
    }

    def "the base applies the Deployment and the Service only, and no overlay adds a resource or a component"() {
        expect:
        kustomization('base').resources == ['deployment.yaml', 'service.yaml']
        ['rd', 'qc', 'prod'].every { environment ->
            def overlay = kustomization("overlays/$environment")
            overlay.resources == ['../../base'] && !overlay.containsKey('components')
        }
    }

    def "Beadle-GUI reaches the API inside the cluster at http://beadle-internal:8082 through the Service"() {
        given:
        Map service = documents(new File(MANIFESTS, 'base/service.yaml')).first()
        Map container = documents(new File(MANIFESTS, 'base/deployment.yaml')).first().spec.template.spec.containers.first()

        expect:
        service.kind == 'Service'
        service.metadata.name == 'beadle-internal'
        service.spec.type in [null, 'ClusterIP']
        service.spec.ports == [[name: 'http', port: 8082, targetPort: 'http', protocol: 'TCP']]
        container.ports.find { it.name == 'http' }.containerPort == 8082
    }

    private static Map kustomization(String directory) {
        documents(new File(MANIFESTS, "$directory/kustomization.yaml")).first()
    }

    private static List<Map> documents(File file) {
        new Yaml().loadAll(file.text).findAll { it != null }.collect { it as Map }
    }

    private static List<File> manifests() {
        List<File> files = []
        MANIFESTS.eachFileRecurse(FILES) { files << it }
        assert files.size() >= 6
        files.sort()
    }
}
