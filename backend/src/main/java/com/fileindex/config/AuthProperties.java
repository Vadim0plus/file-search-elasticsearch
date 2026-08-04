package com.fileindex.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

    /** Single demo admin account. Not a real user management system - see SecurityConfig. */
    private String username = "admin";
    private String password = "admin";
}
