package chaindeal.service;

import chaindeal.entity.SeckillVoucher;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 秒杀优惠券服务接口（tb_seckill_voucher，与优惠券表一对一）
 * 秒杀时用其库存字段做扣减（CAS 乐观锁），无自定义方法，使用 MyBatis-Plus 通用 CRUD
 *
 * @author ChainDeal
 * @since 2022-01-04
 */
public interface ISeckillVoucherService extends IService<SeckillVoucher> {

}
