package chaindeal.controller;


import chaindeal.dto.Result;
import chaindeal.entity.Shop;
import chaindeal.service.IShopService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author ChainDeal
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/shop")
public class ShopController {

    @Resource
    public IShopService shopService;

    /**
     * 根据id查询商铺信息（面试重点：Redis 缓存 + 缓存穿透/击穿解决方案）
     * 当前实现使用缓存穿透方案（queryWithPassThrough）：缓存未命中则查库并写入缓存，空值也缓存防止穿透
     * @param id 商铺id
     * @return 商铺详情数据
     */
    @GetMapping("/{id}")
    public Result queryShopById(@PathVariable("id") Long id) {

        return shopService.queryById(id);
    }

    /**
     * 新增商铺信息
     * @param shop 商铺数据
     * @return 商铺id
     */
    @PostMapping
    public Result saveShop(@RequestBody Shop shop) {
        // 写入数据库
        shopService.save(shop);
        // 返回店铺id
        return Result.ok(shop.getId());
    }

    /**
     * 更新商铺信息
     * @param shop 商铺数据
     * @return 无
     */
    @PutMapping
    public Result updateShop(@RequestBody Shop shop) {
        // 写入数据库
        return shopService.update(shop);
    }

    /**
     * 根据商铺类型分页查询商铺信息（面试重点：附近店铺查询 = Redis GEO 按距离排序）
     * 传了经纬度(x,y)时走 GEO 查询（GEOSEARCH 按距离排序），否则普通数据库分页查询
     * @param typeId 商铺类型
     * @param current 页码
     * @param x 经度（可选）
     * @param y 纬度（可选）
     * @param sortBy 排序方式（可选）：sold-销量降序，priceAsc-价格升序，priceDesc-价格降序
     * @return 商铺列表（带距离）
     */
    @GetMapping("/of/type")
    public Result queryShopByType(
            @RequestParam("typeId") Integer typeId,
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "x",required = false) Double x,
            @RequestParam(value = "y",required = false) Double y,
            @RequestParam(value = "sortBy", required = false) String sortBy
    ) {
        return shopService.queryShopByType(typeId, current, x, y, sortBy);
    }

    /**
     * AI 自然语言找店（面试重点：大模型输出约束、缓存降本、超时降级）
     * 例：q=人均100以内的火锅，解析为 品类/关键词/价格区间 后查询
     * @param q 用户口语化需求
     * @param sortBy 前端手动选择的排序（可选，优先于模型解析结果）
     * @return 解析说明 + 店铺列表；模型失败时降级为关键词搜索
     */
    @GetMapping("/ai-search")
    public Result aiSearch(
            @RequestParam("q") String q,
            @RequestParam(value = "sortBy", required = false) String sortBy
    ) {
        return shopService.aiSearch(q, sortBy);
    }

    /**
     * 根据商铺名称关键字分页查询商铺信息
     * @param name 商铺名称关键字
     * @param current 页码
     * @param sortBy 排序方式（可选）：sold-销量降序，priceAsc-价格升序，priceDesc-价格降序
     * @return 商铺列表
     */
    @GetMapping("/of/name")
    public Result queryShopByName(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "sortBy", required = false) String sortBy
    ) {
        return shopService.queryShopByName(name, current, sortBy);
    }
}
