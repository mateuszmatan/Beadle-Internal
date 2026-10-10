package com.bbh.itss.dso.portal.adapter;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Map;

import static org.apache.commons.lang3.StringUtils.trim;

final class Samples {

    private Samples() {
    }

    record Person(String name, int age) {

        Person {
            name = trim(name);
            if (age < 0) {
                throw new IllegalArgumentException("age must not be negative");
            }
        }
    }

    record PersonView(String name) {
    }

    record PersonRequest(String name, int age) implements Mirrors<Person> {
    }

    record Team(String name, Person lead, List<Person> members, Map<DayOfWeek, Person> rota) {
    }

    record TeamView(String name, PersonView lead, List<PersonView> members, Map<DayOfWeek, PersonView> rota) {
    }

    static final class Holder {

        private final String name;

        Holder(String name) {
            this.name = name;
        }
    }
}
