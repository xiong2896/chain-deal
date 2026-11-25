package chaindeal.service.impl;

import cn.hutool.json.JSONUtil;
import chaindeal.dto.Result;
import chaindeal.entity.ShopType;
import chaindeal.mapper.ShopTypeMapper;
import chaindeal.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author ChainDeal
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 查询商铺类型列表（面试重点：Redis 缓存，缓存穿透的基础应用）
     * 查缓存(key=shop-type-list)命中直接返回；未命中查库后写入缓存（无过期时间，数据改动少）
     * @return 商铺类型列表
     */
    @Override
    public Result queryTypeList() {
        String key = "shop-type-list";
        //1.查redis
        String json = stringRedisTemplate.opsForValue().get(key);
        if(json != null){
            //有就返回（JSON反序列化为列表）
            List<ShopType> list = JSONUtil.toList(json,ShopType.class);
            return Result.ok(list);
        }
        //2.没命中，查数据库（按sort排序）
        List<ShopType> typeList = this.query()
                .orderByAsc("sort")
                .list();

        //3.存入redis（序列化为JSON字符串）
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(typeList));

        //4.返回
        return Result.ok(typeList);


    }
}
