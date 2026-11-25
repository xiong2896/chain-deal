package chaindeal.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import chaindeal.dto.Result;
import chaindeal.entity.Voucher;
import chaindeal.mapper.VoucherMapper;
import chaindeal.entity.SeckillVoucher;
import chaindeal.service.ISeckillVoucherService;
import chaindeal.service.IVoucherService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;

import static chaindeal.utils.RedisConstants.SECKILL_STOCK_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author ChainDeal
 * @since 2021-12-22
 */
@Service
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher> implements IVoucherService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 查询店铺的优惠券列表（普通券 + 秒杀券，Mapper 自定义联表查询）
     * @param shopId 店铺id
     * @return 优惠券列表
     */
    @Override
    public Result queryVoucherOfShop(Long shopId) {
        // 查询优惠券信息（自定义SQL，联查秒杀券信息）
        List<Voucher> vouchers = getBaseMapper().queryVoucherOfShop(shopId);
        // 返回结果
        return Result.ok(vouchers);
    }

    /**
     * 新增秒杀券（面试重点：库存写入 Redis 预热，供秒杀 Lua 脚本原子扣减）
     * 事务保证：普通券表和秒杀券表同时成功/回滚；随后把库存同步到 Redis
     * @param voucher 含秒杀信息的优惠券
     */
    @Override
    @Transactional
    public void addSeckillVoucher(Voucher voucher) {
        // 保存优惠券（主表）
        save(voucher);
        // 保存秒杀信息（从表，一对一）
        SeckillVoucher seckillVoucher = new SeckillVoucher();
        seckillVoucher.setVoucherId(voucher.getId());
        seckillVoucher.setStock(voucher.getStock());
        seckillVoucher.setBeginTime(voucher.getBeginTime());
        seckillVoucher.setEndTime(voucher.getEndTime());
        seckillVoucherService.save(seckillVoucher);
        //保存秒杀库存到redis中（key=seckill:stock:券id，供Lua脚本扣减校验）
        stringRedisTemplate.opsForValue().set(SECKILL_STOCK_KEY+voucher.getId(),voucher.getStock().toString());
    }
}
