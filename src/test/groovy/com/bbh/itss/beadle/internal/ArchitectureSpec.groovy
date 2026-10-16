package com.bbh.itss.beadle.internal

import com.bbh.itss.beadle.internal.support.ArchitectureSpecification

class ArchitectureSpec extends ArchitectureSpecification {

    @Override
    Class<?> application() {
        BeadleInternalApplication
    }

    @Override
    List<String> httpAdapters() {
        ['com.bbh.itss.beadle.internal.adapter.out.http..', 'com.bbh.itss.beadle.internal.adapter.out.jira..',
         'com.bbh.itss.beadle.internal.adapter.out.cybertrack..', 'com.bbh.itss.beadle.internal.adapter.out.confluence..',
         'com.bbh.itss.beadle.internal.adapter.out.qtest..', 'com.bbh.itss.beadle.internal.adapter.out.gui..']
    }
}
