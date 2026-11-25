package chaindeal.service;

import chaindeal.dto.Result;
import chaindeal.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author ChainDeal
 * @since 2021-12-22
 */
public interface IShopService extends IService<Shop> {

    /**
     * 根据id查询店铺（面试重点：Redis 缓存穿透/击穿的解决方案）
     * 当前用缓存穿透方案：查缓存→未命中查库→写入缓存(空值也缓存)
     * @param id 店铺id
     * @return 店铺信息
     */
    Result queryById(Long id);

    /**
     * 更新店铺（面试重点：缓存更新策略 = 先更新数据库再删除缓存）
     * @param shop 店铺数据
     * @return 更新结果
     */
    Result update(Shop shop);

    /**
     * 按类型分页查店铺（面试重点：Redis GEO 附近店铺按距离排序）
     * @param typeId 类型id
     * @param current 页码
     * @param x 经度（可选，传了走GEO）
     * @param y 纬度（可选）
     * @param sortBy 排序方式（可选）：sold-销量降序，priceAsc-价格升序，priceDesc-价格降序
     * @return 店铺列表（带距离）
     */
    Result queryShopByType(Integer typeId, Integer current, Double x, Double y, String sortBy);

    /**
     * 按名称搜索店铺，支持人气/价格排序
     * @param name 名称关键字
     * @param current 页码
     * @param sortBy 排序方式（可选）：sold-销量降序，priceAsc-价格升序，priceDesc-价格降序
     * @return 店铺列表
     */
    Result queryShopByName(String name, Integer current, String sortBy);

    /**
     * AI 自然语言找店：口语化需求解析为结构化条件后查询
     * 模型超时/异常/输出非法时自动降级为关键词搜索
     * @param q 用户口语化需求
     * @param sortBy 前端手动排序（可选，优先于模型解析结果）
     * @return 解析说明与店铺列表
     */
    Result aiSearch(String q, String sortBy);
}
