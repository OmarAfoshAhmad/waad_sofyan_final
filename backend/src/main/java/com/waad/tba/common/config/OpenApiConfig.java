package com.waad.tba.common.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "TBA-WAAD API Documentation",
                version = "1.0.0",
                description = "Third Party Administrator - Health Insurance Platform API",
                contact = @Contact(
                        name = "TBA-WAAD Support",
                        email = "support@alwahacare.com"
                ),
                license = @License(
                        name = "Proprietary",
                        url = "https://alwahacare.com"
                )
        ),
        servers = {
                @Server(
                        description = "Local Development Server",
                        url = "http://localhost:8080"
                )
        },
        security = {
                @SecurityRequirement(name = "SessionCookie")
        }
)
@SecurityScheme(
        name = "SessionCookie",
        description = "Authenticated web session cookie",
        type = SecuritySchemeType.APIKEY,
        paramName = "SESSION",
        in = SecuritySchemeIn.COOKIE
)
public class OpenApiConfig {
}
