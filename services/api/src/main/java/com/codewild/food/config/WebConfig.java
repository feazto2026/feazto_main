package com.codewild.food.config;

import com.codewild.food.shared.idempotency.IdempotencyFilter;
import com.codewild.food.shared.observability.RequestIdFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/api/**")
        .allowedOriginPatterns("*")
        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        .allowedHeaders("*")
        .exposedHeaders("Idempotency-Key", "X-Request-Id")
        .allowCredentials(false).maxAge(3600);
  }

  @Bean
  public FilterRegistrationBean<RequestIdFilter> requestIdFilter() {
    FilterRegistrationBean<RequestIdFilter> b = new FilterRegistrationBean<>(new RequestIdFilter());
    b.setOrder(Ordered.HIGHEST_PRECEDENCE);
    b.addUrlPatterns("/api/*");
    return b;
  }

  @Bean
  public FilterRegistrationBean<IdempotencyFilter> idempotencyFilter(IdempotencyFilter f) {
    FilterRegistrationBean<IdempotencyFilter> b = new FilterRegistrationBean<>(f);
    b.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
    b.addUrlPatterns("/api/*");
    return b;
  }
}
