package com.bbh.itss.beadle.internal.adapter.out.gui;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where Beadle-GUI answers the callbacks of Beadle-Internal ({@code BEADLE_GUI_URL}).
 */
@ConfigurationProperties("beadle.gui")
public record GuiProperties(String url) {
}
