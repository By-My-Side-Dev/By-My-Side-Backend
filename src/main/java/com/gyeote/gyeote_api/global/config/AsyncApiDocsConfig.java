package com.gyeote.gyeote_api.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AsyncApiDocsConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/asyncapi", "/asyncapi/index.html");
        registry.addRedirectViewController("/asyncapi/", "/asyncapi/index.html");
    }
}
