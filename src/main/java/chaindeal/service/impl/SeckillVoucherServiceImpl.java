package chaindeal.service.impl;

import chaindeal.entity.SeckillVoucher;
import chaindeal.mapper.SeckillVoucherMapper;
import chaindeal.service.ISeckillVoucherService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 秒杀优惠券表，与优惠券是一对一关系 服务实现类
 * </p>
 *
 * @author ChainDeal
 * @since 2022-01-04
 */
@Service
public class SeckillVoucherServiceImpl extends ServiceImpl<SeckillVoucherMapper, SeckillVoucher> implements ISeckillVoucherService {
    // 秒杀券服务实现：秒杀时通过 createVoucherOrder 用 CAS 扣减其库存（stock=stock-1 where stock>0）
}
