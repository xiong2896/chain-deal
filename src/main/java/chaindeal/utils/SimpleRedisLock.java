package chaindeal.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock{

    private String name;
    private StringRedisTemplate stringRedisTemplate;


    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }


    private static final String KEY_PREFIX="lock:";
    private static final String ID_PREFIX= UUID.randomUUID().toString(true)+"-";
    //---------------脚本初始化
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
        static {
            UNLOCK_SCRIPT = new DefaultRedisScript<>();
            UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
            UNLOCK_SCRIPT.setResultType(Long.class);
        }

    @Override
    public boolean tryLock(Long timeoutSec) {
        //获取线程标识-----用UUID拼线程ID-----使锁名不重复------是当前执行代码的线程，实时组装生成的标识，用来存入Redis作为锁 value
        String threadId =ID_PREFIX + Thread.currentThread().getId();//------------------------专门用来获取当前正在执行代码的线程对象
        //获取锁
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX+name, threadId, timeoutSec, TimeUnit.SECONDS);//threadId+""这样自动转成字符串
        return Boolean.TRUE.equals(success);
    }
    @Override
    public void unlock() {
        //调用lua脚本
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX+name),
                ID_PREFIX+Thread.currentThread().getId());
    }
    /*@Override
    public void unlock() {
        //获取线程标识
        String threadId =ID_PREFIX + Thread.currentThread().getId();
        //获取锁中的标识
        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
        //判断标识是否一致
        if(threadId.equals(id)){
            //释放锁
            //释放锁
            stringRedisTemplate.delete(KEY_PREFIX+name);
        }
    }*/
}
