package com.bbh.itss.beadle.internal.config

import org.yaml.snakeyaml.Yaml
import spock.lang.Shared
import spock.lang.Specification

import static groovy.io.FileType.FILES

/**
 * The OpenShift manifests: two pods of Beadle-Internal in the one project of the Beadle services, reached by Beadle-GUI
 * through the Service only, configured by ConfigMaps and Secrets named after the service.
 */
class OpenShiftManifestsSpec extends Specification {

    static final File MANIFESTS = new File('deploy/openshift/beadle-internal')

    @Shared
    Map service = documents('base/service.yaml').first()

    @Shared
    Map deployment = documents('base/deployment.yaml').first()

    @Shared
    Map container = deployment.spec.template.spec.containers.first()

    def "nothing of Beadle-Internal is published outside the cluster, and no overlay adds a resource: #file.name"() {
        given:
        List<Map> manifest = new Yaml().loadAll(file.text).findAll()

        expect:
        manifest.every { !(it.kind in ['Route', 'Ingress']) && !it.namespace && !it.metadata?.namespace }
        !file.path.contains('overlays') || manifest.first().subMap(['resources', 'components']) == [resources: ['../../base']]

        where:
        file << manifests()
    }

    def "two pods run behind the Service that Beadle-GUI calls at http://beadle-internal:8082"() {
        expect:
        deployment.spec.replicas == 2
        service.metadata.name == 'beadle-internal'
        service.spec.type in [null, 'ClusterIP']
        service.spec.ports == [[name: 'http', port: 8082, targetPort: 'http', protocol: 'TCP']]
        container.ports == [[name: 'http', containerPort: 8082, protocol: 'TCP']]
    }

    def "the pod is configured by ConfigMaps and Secrets of its own and probed on its health groups"() {
        expect:
        container.envFrom.collect { (it.configMapRef ?: it.secretRef).name }.every { it.startsWith('beadle-internal-') }
        documents('base/kustomization.yaml').first().configMapGenerator*.name == ['beadle-internal-config']
        [container.startupProbe, container.livenessProbe, container.readinessProbe]*.httpGet ==
                [[path: '/actuator/health/liveness', port: 'http'], [path: '/actuator/health/liveness', port: 'http'],
                 [path: '/actuator/health/readiness', port: 'http']]
    }

    private static List<Map> documents(String path) {
        new Yaml().loadAll(new File(MANIFESTS, path).text).findAll().collect { it as Map }
    }

    private static List<File> manifests() {
        List<File> files = []
        MANIFESTS.eachFileRecurse(FILES) { files << it }
        files.sort()
    }
}
