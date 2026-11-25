package chaindeal.service.impl;

import cn.hutool.core.bean.BeanUtil;
import chaindeal.dto.Result;
import chaindeal.entity.VoucherOrder;
import chaindeal.mapper.VoucherOrderMapper;
import chaindeal.service.ISeckillVoucherService;
import chaindeal.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import chaindeal.utils.RedisIdWorker;
import chaindeal.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author ChainDeal
 * @since 2021-12-22
 */
@Slf4j
@Service//--------------------------/--继承系统的ServiceImpl---要指定<mapper类型和实体类类型>--/--右边是实体类，对应着绑定的表
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource//继承 MyBatis‑Plus 的`IService`
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedissonClient redissonClient;
    //---------------TODO 脚本初始化
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckkill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }
    //创建单线程池，负责消费 Redis Stream 中的秒杀订单消息，异步创建订单
    private static final ExecutorService SECKILL_ORDER_EXECUTOR= Executors.newSingleThreadExecutor();
    /**
     * 初始化：确保 Stream 和消费者组存在，再启动订单消费线程
     */
    @PostConstruct
    private void init(){
        // 先创建 stream 和消费者组（已存在则忽略），否则消费者线程启动时读 stream 会报 NOGROUP
        try {
            stringRedisTemplate.opsForStream().createGroup("stream.orders", "g1");
        } catch (Exception e) {
            // stream 或 group 已存在时报错，忽略即可
        }
        //启动消费者线程，从 Stream 中读取订单消息异步处理
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
        }

    /**
     * 订单消费者（面试重点：Redis Stream 消息队列 + 消费者组）
     * 死循环：XREADGROUP 阻塞读取新消息 → 解析订单 → 创建订单 → ACK确认
     * 读取异常时转处理 pending-list（未确认的消息，保证不丢消息）
     */
    private class VoucherOrderHandler implements Runnable {
        String queueName="stream.orders";
        @Override
        public void run() {
            while (true) {
                try {
                    //1.获取消息队列中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS streams.order >
                    //> 表示只读"从未被读取过"的新消息；BLOCK 2秒阻塞等待
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
                    );
                    //2.判断消息获取是否成功
                    if(list==null||list.isEmpty()){
                        continue;
                    }
                    //3.解析消息中的订单信息（Stream 存的是字段Map，转成VoucherOrder对象）
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> values = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
                    //4.如果获取成功，可以下单（扣库存+保存订单，用分布式锁保证一人一单）
                    handleVoucherOrder(voucherOrder);
                    //5.ACK确认 SACK stream.order g1 id（消费成功后确认，消息不会进pending-list）
                    stringRedisTemplate.opsForStream().acknowledge(queueName,"g1",record.getId());
                } catch (Exception e) {
                    //读取/处理异常：转去处理pending-list（未ACK的消息），实现消息不丢失
                    log.error("处理订单异常", e);
                    handlePandingList();
                }
            }
        }

        /**
         * 处理 pending-list（消息丢失兜底：面试重点）
         * 从 Stream 的 pending-list（已读取未ACK的消息）中 XREADGROUP 读，处理成功再ACK
         * 没有异常消息时退出循环
         */
        private void handlePandingList() {
            while (true) {
                try {
                    //1.获取pending-list中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1  STREAMS streams.order 0
                    //ReadOffset.from("0") 表示读pending-list（已读取未确认的消息）
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create(queueName,ReadOffset.from("0"))
                    );
                    //2.判断消息获取是否成功
                    if(list==null||list.isEmpty()){
                        //如果获取失败，说明peading-list没有异常消息，结束循环
                        break;
                    }
                    //3.解析消息中的订单信息
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> values = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
                    //4.如果获取成功，可以下单
                    handleVoucherOrder(voucherOrder);
                    //5.ACK确认 SACK stream.order g1 id
                    stringRedisTemplate.opsForStream().acknowledge(queueName,"g1",record.getId());
                } catch (Exception e) {
                    log.error("处理pending-list订单异常", e);
                    try {
                        Thread.sleep(20);//休眠个20秒，以免频繁抛出异常
                    } catch (InterruptedException interruptedException) {
                        interruptedException.printStackTrace();
                    }
                }
            }
        }
    }
/*    private class VoucherOrderHandler implements Runnable {

        @Override
        public void run() {
            while (true) {
                try {
                    //获取队列中的队列信息
                    VoucherOrder voucherOrder = orderTasks.take();
                    //创建订单
                    handleVoucherOrder(voucherOrder);
                } catch (Exception e) {
                    log.error("处理订单异常", e);
                }
            }
        }
    }*/

    /**
     * 处理一条订单消息（消费者线程调用，面试重点：Redisson 分布式锁保证一人一单）
     * 注意：createVoucherOrder 是事务方法，必须通过代理对象调用，否则事务不生效
     * @param voucherOrder 订单数据（消息从 Stream 解析而来）
     */
    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        //获取用户
        Long userId = voucherOrder.getUserId();
        //创建锁对象（Redisson 分布式锁，key=lock:order:用户id，一人一单的互斥）
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        //获取锁
        boolean isLock = lock.tryLock();
        //判断是否获取锁成功
        if(!isLock){
            //获取锁失败（已有线程在处理该用户的订单），直接返回
            log.error("不允许重复下单");
            return;
        }
        try {
            //通过代理对象调用事务方法，创建订单（扣库存+保存订单）
             proxy.createVoucherOrder(voucherOrder);
        }finally {//-----------------try 中的代码，不管是正常执行完毕，还是中途抛出异常，finally 里面的代码【一定会执行】
            //释放锁
            lock.unlock();
        }
    }
    private IVoucherOrderService proxy;
    /**
     * 秒杀下单（面试重点：Redis Lua 脚本保证"库存校验+一人一单+扣库存"原子性 + 异步下单）
     * 流程：Lua脚本原子校验并扣减库存 → 生成订单写入 Redis Stream → 消费者线程异步真正建单
     * 这样请求入口只做内存/Redis操作，不碰数据库，高并发下单也扛得住
     * @param voucherId 秒杀券id
     * @return 成功返回订单id
     */
    @Override
    public Result seckillVoucher(Long voucherId) {
        //获取用户
        Long userId = UserHolder.getUser().getId();
        //获取订单id（Redis 全局唯一ID生成器，时间戳+序列号）
        long orderId = redisIdWorker.nextId("order");
        //执行lua脚本：参数为券id、用户id、订单id
        //脚本内部：校验库存充足 && 用户未买过（Redis Set判断） → 扣库存 → 写入Stream消息
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString(),String.valueOf(orderId)
        );
        //判断结果是否为0
        int r = result.intValue();
        if(r!=0){
            //不为0，代表没有购买资格
            return Result.fail(r==1?"库存不足":"不能重复下单");
        }
        //获取代理对象（事务），供消费者线程调用 createVoucherOrder
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        //返回订单id（下单成功，实际订单由消费者线程异步创建）
        return Result.ok(orderId);
    }
/*    @Override
    public Result seckillVoucher(Long voucherId) {
        //获取用户
        Long userId = UserHolder.getUser().getId();
        //TODO 执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString()
        );
        //判断结果是否为0
        int r = result.intValue();
        if(r!=0){
            //不为0，代表没有购买资格
            return Result.fail(r==1?"库存不足":"不能重复下单");
        }
        //TODO 保存阻塞队列
        VoucherOrder voucherOrder = new VoucherOrder();
        //订单id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        //用户id
        voucherOrder.setUserId(userId);
        //代金券id
        voucherOrder.setVoucherId(voucherId);
        //TODO 放入阻塞队列
        orderTasks.add(voucherOrder);
        //获取代理对象（事务）
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        //返回订单id
        return Result.ok(orderId);
    }*/

/*    @Override
    public Result seckillVoucher(Long voucherId) {
        //查询优惠卷
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        //判断秒杀是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀尚未开始");
        }
        //判断秒杀是否结束
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已经结束");
        }
        //判断库存是否充足
        if (voucher.getStock() < 1) {
            return Result.fail("库存不足");
        }

        //intern保证userid不因为属性转换而改变---给下面这个函数加锁，防止事务执行时，锁解除了，同时事务还没来得及提交，就有新的请求进来，从而绕过锁的情况发生
        Long userId = UserHolder.getUser().getId();
        //创建锁对象
        //SimpleRedisLock lock = new SimpleRedisLock("order" + userId, stringRedisTemplate);

        RLock lock = redissonClient.getLock("lock:order" + userId);//--------------用这个就不用自己创建锁了，也省去了分布锁等复杂流程

        //获取锁
        boolean isLock = lock.tryLock();
        //判断是否获取锁成功
        if(!isLock){
            //获取锁失败,返回错误或重试
            return Result.fail("不允许重复下单");//也可能已经有锁了，抢锁失败
        }
        try {
            //获取代理对象（事务）
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        }finally {//-----------------try 中的代码，不管是正常执行完毕，还是中途抛出异常，finally 里面的代码【一定会执行】
            //出现异常就释放锁
            lock.unlock();
        }
    }*/

    /**
     * 真正创建订单（事务方法，面试重点：一人一单 + CAS乐观锁防超卖）
     * 三步：校验用户是否已购买过该券 → 扣减库存（CAS，stock>0才扣）→ 保存订单
     * 注意：@Transactional 事务 + 通过代理对象调用，保证多表操作原子性
     * @param voucherOrder 订单数据
     * @return 创建结果
     */
    @Transactional//下面有两张表的操作，所以加个事务
    public  Result createVoucherOrder(VoucherOrder voucherOrder) {
        //一人一单（同一用户同一券只能买一次）
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();
        //查询订单 select count(*) from tb_voucher_order where user_id = ? and voucher_id = ?-----------count(*)是返回条数的意思
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();//--query()--获取当前 Service 对应的查询构造器
        //判断是否存在
        if (count > 0) {
            //用户已经购买过了
            log.error("用户已购买一次");
            return Result.fail("用户已购买一次");
        }
        //扣除库存（CAS乐观锁：where stock>0 才扣，防止超卖；失败率高但简单）
        boolean success = seckillVoucherService.update()
                .setSql("stock=stock-1")//set stock=stock-1，原子SQL更新库存减一
                .eq("voucher_id", voucherId).gt("stock", 0)//where id=? and stock>0，只有库存>0才扣减，防止卖成负数
                .update();
        if (!success) {
            //扣减失败（库存已为0）
            log.error("库存不足");
            return Result.fail("库存不足");
        }
        //创建订单（保存到 tb_voucher_order）
        save(voucherOrder);
        return Result.ok();
    }
}
