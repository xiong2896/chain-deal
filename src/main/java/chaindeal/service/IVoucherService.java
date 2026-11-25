package chaindeal.service;

import chaindeal.dto.Result;
import chaindeal.entity.Voucher;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author ChainDeal
 * @since 2021-12-22
 */
public interface IVoucherService extends IService<Voucher> {

    /**
     * 查询店铺下的优惠券列表（普通券 + 秒杀券）
     * @param shopId 店铺id
     * @return 优惠券列表
     */
    Result queryVoucherOfShop(Long shopId);

    /**
     * 新增秒杀券（普通券表 + 秒杀券表同时保存，并把库存写入 Redis 预热供秒杀脚本使用）
     * @param voucher 含秒杀信息的优惠券
     */
    void addSeckillVoucher(Voucher voucher);
}
