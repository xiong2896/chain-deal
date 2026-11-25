package chaindeal.controller;


import chaindeal.dto.Result;
import chaindeal.service.IVoucherOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author ChainDeal
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/voucher-order")
public class VoucherOrderController {
    @Autowired
    private IVoucherOrderService voucherOrderService;


    /**
     * 秒杀下单入口（面试重点：Redis Lua 脚本保证原子性 + Redis Stream 异步下单）
     * 流程：Lua 脚本校验库存/一人一单并扣减库存 → 订单信息写入 Stream 消息队列 → 消费者线程异步创建订单
     * @param voucherId 秒杀券id
     * @return 秒杀结果，成功返回订单id
     */
    @PostMapping("seckill/{id}")
    public Result seckillVoucher(@PathVariable("id") Long voucherId) {

        return voucherOrderService.seckillVoucher(voucherId);
    }
}
