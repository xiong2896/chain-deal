package chaindeal.utils;


import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static chaindeal.utils.RedisConstants.*;

@Component
@Slf4j
public class CacheClient {
    @Resource//java官方注解，Autowired是spring的，都能用
    private StringRedisTemplate stringRedisTemplate;
    //写值
    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }
    //写值并设置逻辑过期时间
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        //设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        //写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    //---------------------解决缓存穿透
    public <R,ID> R queryWithPassThrough(String keyPrefix , ID id , Class<R> type, Function<ID,R> dbFallback, Long time, TimeUnit unit){//因为不知道返回值类型，用泛型
        String key=keyPrefix + id;
        //从redis查商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        //判断是否存在
        if(StrUtil.isNotBlank(json)){
            //存在,直接返回
            return JSONUtil.toBean(json, type);
        }
        //命中的是否为空值
        if(json!=null){
            //返回一个错误信息
            return null;
        }
        //不存在，根据id查数据库//继承了IService<Shop>，所以直接有这方法
        R r = dbFallback.apply(id);
        //不存在，返回错误
        if(r==null){
            //将空值写入redis//----------------------------------防止缓存穿透
            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
            //返回错误信息
            return null;
        }
        //存在，写入redis
        this.set(key,r,time,unit);
        //返回数据
        return r;
    }





    //----------------------------------//---------------------------逻辑过期解决缓存击穿
    //线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR= Executors.newFixedThreadPool(10);

    public <R,ID> R queryWithLogicalExpire(
            String keyPrefix,ID id,Class<R> type,Function<ID,R>dbFallback, Long time, TimeUnit unit){
        String key=keyPrefix + id;
        //从redis查商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        //判断是否存在----isBlank-为空
        if(StrUtil.isBlank(json)){
            //不存在,直接返回
            return null;
        }
        //命中，需要先把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(),type);
        LocalDateTime expireTime = redisData.getExpireTime();
        //判断是否过期
        if(expireTime.isAfter(LocalDateTime.now())){
            //未过期，直接返回店铺信息
            return r;
        }
        //已过期，需要缓存重建
        //缓存重建

        //获得互斥锁
        String lookKey=LOCK_SHOP_KEY+id;
        boolean isLock=tryLock(lookKey);
        //判断是否获取锁成功
        if(isLock){
            //成功，开启独立显存，实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                    //重建缓存
                    //查询数据库
                    R r1=dbFallback.apply(id);
                    //写入redis
                    this.setWithLogicalExpire(key,r1,time,unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }finally {
                    //释放锁
                    unlock(lookKey);
                }
            });
        }
        //返回过期的店铺信息
        return r;
    }

    //缓存击穿问题解决----互斥锁---------------------------一个进程拿到锁，其他进程等待解锁
    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }
}
