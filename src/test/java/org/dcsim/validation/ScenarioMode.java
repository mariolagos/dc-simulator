package org.dcsim.validation;

public enum ScenarioMode {
    APP_CONF_GENERATES_NETWORK, // application.conf exists -> generate 2..6
    CSV_NETWORK                // no application.conf -> use 2..6 directly
}