// org.dcsim.traffic/TemplateParser.java
package org.dcsim.traffic;

import com.typesafe.config.Config;

import java.util.*;

public final class TemplateParser {
    private TemplateParser() {}

    public static Map<String, TrainTemplate> parse(Config templatesConf) {
        // traffic.templates.T1.stops = [ { signature, arrival?, departure? }, ... ]
        return Map.of(); // TODO: implement
    }
}
