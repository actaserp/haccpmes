package mes.config;


import mes.app.interceptor.SmartFactoryInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;


@Configuration
public class WebMvcConfig implements WebMvcConfigurer{

    @Autowired
    private SmartFactoryInterceptor smartFactoryInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(smartFactoryInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/",                // 루트 경로 제외
                        "/assets/**",
                        "/css/**",
                        "/js/**",
                        "/images/**",
                        "/resource/**",
                        "/font/**",
                        "/img/**",
                        "/api/common/**",
                        "/user-auth/getspjangcd",
                        "/weather/**",
                        "/*.ico",
                        "/*.html",
                        "/gui/**",
                        "/api/system/**",
                        "/api/popup/**"
                );
    }


    @Override
    public void addCorsMappings(CorsRegistry registry){
        registry.addMapping("/**")
                .allowedOrigins(
                        "http://localhost:8030", "http://actascld.co.kr:8030/", "http://mes.actascld.co.kr", "https://mes.actascld.co.kr", // 모든 오리진 허용
                        "http://localhost:8031", "http://actascld.co.kr:8031/", "http://dy.actascld.co.kr", "https://dy.actascld.co.kr")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "HEAD")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
    

}
