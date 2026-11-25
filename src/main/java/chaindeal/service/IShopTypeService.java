package chaindeal.service;

import chaindeal.dto.Result;
import chaindeal.entity.ShopType;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author ChainDeal
 * @since 2021-12-22
 */
public interface IShopTypeService extends IService<ShopType> {
    /**
     * 查询商铺类型列表（首页分类导航，面试重点：Redis 缓存，首次查库后缓存）
     * @return 商铺类型列表
     */
    Result queryTypeList();
}
