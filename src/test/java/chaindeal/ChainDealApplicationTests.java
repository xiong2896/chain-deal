package chaindeal;

import chaindeal.service.impl.ShopServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.Set;
//--------------------------单元测试
@SpringBootTest
class ChainDealApplicationTests {

    @Resource
    private ShopServiceImpl shopService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void testSaveShou(){
        // 这行是逻辑过期方案预热缓存用的，当前用缓存穿透方案（没缓存自动查库），方法已注释，故停用
//        shopService.saveShop2Redis(1L,10L);//10L;因为第二个参数为Long类型，不写就默认为int类型
    }

    @Test
    void testSaveShopGeo(){
        // GEO 数据预热：把每个类型下的店铺坐标写入 redis（shop:geo:类型id），否则按距离查询会查不到
        shopService.saveShop2RedisForGeo();
    }

    @Test
    void testClearShopCache(){
        // 一次性清理工具：删除 cache:shop:* 旧缓存，让缓存穿透方案重新查库写入正确格式
        // 原因：shop:1 之前被逻辑过期方案预热过，存的是 RedisData 旧格式，穿透方案解析出来全是 null
        Set<String> keys = stringRedisTemplate.keys("cache:shop:*");
        if (keys != null) {
            stringRedisTemplate.delete(keys);
            System.out.println("已删除 " + keys.size() + " 个 cache:shop:* key");
        }
    }
}
