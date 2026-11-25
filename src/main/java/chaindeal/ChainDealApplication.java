package chaindeal;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;


@EnableAspectJAutoProxy(exposeProxy = true)
@MapperScan("chaindeal.mapper")
@SpringBootApplication
public class ChainDealApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChainDealApplication.class, args);
    }

}
