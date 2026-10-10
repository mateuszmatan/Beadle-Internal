package com.bbh.itss.dso.portal.adapter.in.web

import org.springframework.context.annotation.AnnotatedBeanDefinitionReader
import org.springframework.core.convert.support.DefaultConversionService
import org.springframework.core.env.MapPropertySource
import org.springframework.mock.web.MockServletContext
import org.springframework.test.web.servlet.MockMvc
import org.springframework.web.context.support.GenericWebApplicationContext
import org.springframework.web.servlet.config.annotation.DelegatingWebMvcConfiguration
import spock.lang.Shared
import spock.lang.Specification

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup

class SinglePageAppForwardingSpec extends Specification {

    @Shared
    MockMvc mvc = mvcWithPages('pipelines, admin')

    def "the app's page #path is served by the Angular index page"() {
        when:
        def response = mvc.perform(get(path)).andReturn().response

        then:
        response.forwardedUrl == '/index.html'

        where:
        path << ['/pipelines', '/pipelines/7', '/admin', '/admin/products/12/edit']
    }

    def "#path is left to the API, the static resources and the pages of the other application"() {
        expect:
        mvc.perform(get(path)).andReturn().response.status == 404

        where:
        path << ['/api/unknown', '/favicon.ico', '/index.htm', '/changes', '/new-change/product']
    }

    private static MockMvc mvcWithPages(String pages) {
        def context = new GenericWebApplicationContext(new MockServletContext())
        context.environment.propertySources.addFirst(new MapPropertySource('pages', ['dso.gui.pages': pages]))
        context.registerBean('conversionService', DefaultConversionService)
        new AnnotatedBeanDefinitionReader(context).register(DelegatingWebMvcConfiguration, SinglePageAppForwarding)
        context.refresh()
        webAppContextSetup(context).build()
    }
}
