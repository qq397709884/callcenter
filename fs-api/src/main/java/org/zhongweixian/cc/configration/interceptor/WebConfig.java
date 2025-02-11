package org.zhongweixian.cc.configration.interceptor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.zhongweixian.cc.cache.CacheService;
import org.zhongweixian.cc.service.AgentService;

/**
 * Create by caoliang on 2020/9/20
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private AgentService agentService;

    @Autowired
    private CacheService cacheService;

    @Autowired
    private ApplicationContext applicationContext;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/v1/cti/**")
                .allowCredentials(true)
                .allowedMethods("GET", "POST", "DELETE", "PUT", "PATCH")
                .maxAge(3600);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HttpRequestInteceptor(agentService, cacheService, applicationContext))
                .addPathPatterns("/v1/cti/call/**")
                .addPathPatterns("/v1/cti/agent/**")
                .addPathPatterns("/v1/cti/admin/**")
                .excludePathPatterns("/v1/cti/agent/login", "/**/*.js", "/**/*.css", "/**/*.png");
    }


}
