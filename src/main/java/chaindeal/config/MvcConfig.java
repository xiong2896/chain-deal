package chaindeal.config;

//配置拦截器

import chaindeal.utils.LoginInterceptor;
import chaindeal.utils.RefreshToKenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

@Configuration
public class MvcConfig implements WebMvcConfigurer {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    //这个拦截器拦部分请求
    // 方法名必须是 addInterceptors（WebMvcConfigurer 的方法），拼错的话 Spring 不会调用，拦截器就失效了
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoginInterceptor())
                //不拦截的//登录拦截器
                .excludePathPatterns(
                        "/shop/**",
                        "/voucher/**",
                        "/shop-type/**",
                        "/upload/**",
                        "/blog/hot",
                        "/user/code",
                        "/user/login"
                ).order(1);
        //这个拦截器拦所以请求/////小的现执行//token刷新拦截器
        registry.addInterceptor(new RefreshToKenInterceptor(stringRedisTemplate)).order(0);
    }
}
