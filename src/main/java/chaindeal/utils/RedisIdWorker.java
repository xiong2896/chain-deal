package chaindeal.utils;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {

/*
* 开始时间戳
* */
    private static final long BEGIN_TIMESTAMP=1787072880L;
/*
* 序列号的位数
* */
    private static final int COUNT_BITS=32;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    public long nextId(String keyPrefix){
        //生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;
        //生成序列号
        //获取当前日期,精确到天
        String date= now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        //自增长-------Redis 的 String 类型 key 做数字自增 +1，返回增加之后的数值
        long count = stringRedisTemplate.opsForValue().increment("icr" + keyPrefix + ":" + date);
        //拼接并返回
        return timestamp<<COUNT_BITS | count;
    }

    public static void main(String[] args){
        LocalDateTime time = LocalDateTime.of(2026, 8, 18, 17, 8, 0);
        //把时间转成 UTC 时区的时间戳（秒数)
        long second = time.toEpochSecond(ZoneOffset.UTC);
        System.out.println("second="+second);

    }

}
