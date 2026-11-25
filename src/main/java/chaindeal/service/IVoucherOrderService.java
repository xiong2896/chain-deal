package chaindeal.service;

import chaindeal.dto.Result;
import chaindeal.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author ChainDeal
 * @since 2021-12-22
 */
//--------------------------------------------/---继承系统的---------/
public interface IVoucherOrderService extends IService<VoucherOrder> {

    /**
     * 秒杀下单（面试重点：Redis Lua 脚本原子校验库存+一人一单+扣库存，然后写入 Stream 异步下单）
     * @param voucherId 秒杀券id
     * @return 成功返回订单id
     */
    Result seckillVoucher(Long voucherId);

    /**
     * 真正创建订单（事务方法，供 Stream 消费者线程调用）
     * 执行：校验一人一单 → CAS 扣减库存 → 保存订单，全程用 Redisson 分布式锁保证并发安全
     * @param voucherId 订单对象
     * @return 创建结果
     */
    Result createVoucherOrder(VoucherOrder voucherId);
}
