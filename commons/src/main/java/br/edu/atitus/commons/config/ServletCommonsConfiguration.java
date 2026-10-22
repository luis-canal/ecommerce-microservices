package br.edu.atitus.commons.config;

import br.edu.atitus.commons.web.ApiExceptionHandler;
import br.edu.atitus.commons.web.GatewayHeadersFilter;
import br.edu.atitus.commons.web.RequireRoleInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@AutoConfiguration
@ConditionalOnWebApplication(type = Type.SERVLET)
@ConditionalOnClass(name = "org.springframework.web.servlet.config.annotation.WebMvcConfigurer")
public class ServletCommonsConfiguration implements WebMvcConfigurer {
    private final RequireRoleInterceptor requireRoleInterceptor;

    public ServletCommonsConfiguration(RequireRoleInterceptor requireRoleInterceptor) {
        this.requireRoleInterceptor = requireRoleInterceptor;
    }

    @Bean
    static ApiExceptionHandler apiExceptionHandler() {
        return new ApiExceptionHandler();
    }

    @Bean
    static RequireRoleInterceptor requireRoleInterceptor() {
        return new RequireRoleInterceptor();
    }

    @Bean
    @ConditionalOnProperty(name = "internal.api-key")
    FilterRegistrationBean<GatewayHeadersFilter> gatewayHeadersFilter(
            @Value("${internal.api-key}") String key) {
        FilterRegistrationBean<GatewayHeadersFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new GatewayHeadersFilter(key));
        registration.setOrder(-100);
        return registration;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(requireRoleInterceptor);
    }
}