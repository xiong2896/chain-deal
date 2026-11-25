package chaindeal.controller;


import chaindeal.dto.Result;
import chaindeal.service.IShopTypeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
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
@RequestMapping("/shop-type")
public class ShopTypeController {
    @Resource
    private IShopTypeService typeService;

//query()是My‑Plus自己的方法
    /**
     * 查询商铺类型列表（首页顶部分类导航）
     * 面试重点：服务层做了 Redis 缓存（key=shop:type:list），首次查库后写入缓存
     * @return 商铺类型列表
     */
    @GetMapping("list")
    public Result queryTypeList() {
        // 直接调用service写好的带缓存方法
        return typeService.queryTypeList();
    }
}
