package com.bbh.itss.dso.portal.support

import groovy.json.JsonOutput
import groovy.json.JsonSlurper

final class Json {

    private Json() {
    }

    static String toJson(Object body) {
        JsonOutput.toJson(body)
    }

    static Object parse(String json) {
        json ? new JsonSlurper().parseText(json) : null
    }
}
