package com.bbh.itss.dso.portal.domain.catalog

import spock.lang.Specification

class ProductCodeSpec extends Specification {

    def "the code of #name is #code"() {
        expect:
        ProductCode.suggest(name, directory([])) == code

        where:
        name                         || code
        'CertScanner'                || 'CERTSCANNER'
        'Payments Hub'               || 'PAYMENTSHUB'
        '  Złoty Łabędź-Ćma 2  '      || 'ZLOTYLABEDZCMA2'
        '3D Viewer'                  || 'P3DVIEWER'
        'X'                          || 'PRODUCTX'
        '!!!'                        || 'PRODUCT'
        null                         || 'PRODUCT'
        'A' * 80                     || 'A' * 50
    }

    def "a taken code gets the first free number, whatever its case"() {
        expect:
        ProductCode.suggest('CertScanner', directory(['certscanner', 'CERTSCANNER2'])) == 'CERTSCANNER3'
    }

    def "a numbered code keeps within the code length"() {
        expect:
        ProductCode.suggest('B' * 60, directory(['B' * 50])) == 'B' * 49 + '2'
    }

    def "every suggestion is a valid product code"() {
        expect:
        ProductCode.suggest(name, directory([])) ==~ /[A-Z][A-Z0-9_-]{1,49}/

        where:
        name << ['CertScanner', 'ą', '9', '12 Monkeys', 'Ölçer', 'Straße', '---']
    }

    private static ProductDirectory directory(List<String> taken) {
        new ProductDirectory() {
            Optional<ProductDirectory.ProductIdentity> findProductByCode(String code) {
                taken.any { it.equalsIgnoreCase(code) } ? Optional.of(new ProductDirectory.ProductIdentity(1, code)) : Optional.empty()
            }

            Optional<ProductDirectory.ProductIdentity> findProductByName(String name) {
                Optional.empty()
            }

            boolean departmentExists(long id) {
                true
            }
        }
    }
}
