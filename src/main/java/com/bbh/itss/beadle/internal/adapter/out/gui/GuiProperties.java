package com.bbh.itss.beadle.internal.adapter.out.gui;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Where Beadle-GUI answers the callbacks of Beadle-Internal ({@code BEADLE_GUI_URL}).
 */
@ConfigurationProperties("beadle.gui")
public record GuiProperties(@DefaultValue("http://localhost:8081") String url) {
}
