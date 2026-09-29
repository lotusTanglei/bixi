package com.lotus.bixi.common.security.component;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Credentials used by the Spring Boot Admin server to read protected Actuator data. */
@Getter
@Setter
@ConfigurationProperties(prefix = "bixi.sba.actuator")
public class BixiActuatorSecurityProperties {

    private String username;

    private String password;

}
