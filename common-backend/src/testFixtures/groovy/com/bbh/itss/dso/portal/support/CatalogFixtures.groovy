package com.bbh.itss.dso.portal.support

import com.bbh.itss.dso.portal.domain.catalog.ProductDetails

class CatalogFixtures {

    static final long DEPARTMENT_ID = 3L

    private CatalogFixtures() {
    }

    static ProductDetails details(Map args = [:]) {
        new ProductDetails(args.code as String ?: 'CERT', args.name as String ?: 'CertScanner',
                args.description as String, args.ownerTeam as String, args.contactEmail as String,
                args.containsKey('departmentId') ? args.departmentId as Long : DEPARTMENT_ID)
    }

    static <T extends Record> T copy(Map changes, T record) {
        def components = record.class.recordComponents
        def args = components.collect { changes.containsKey(it.name) ? changes[it.name] : it.accessor.invoke(record) }
        record.class.declaredConstructors.find { it.parameterCount == components.length }
                .newInstance(args as Object[]) as T
    }
}
