package chaindeal.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import chaindeal.config.AiProperties;
import chaindeal.dto.AiSearchCondition;
import chaindeal.dto.AiSearchResult;
import chaindeal.dto.Result;
import chaindeal.entity.Shop;
import chaindeal.entity.ShopType;
import chaindeal.mapper.ShopMapper;
import chaindeal.service.IShopService;
import chaindeal.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import chaindeal.utils.AiClient;
import chaindeal.utils.CacheClient;
import chaindeal.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static chaindeal.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author ChainDeal
 * @since 2021-12-22
 */
@Slf4j
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private CacheClient cacheClient;
    @Autowired
    private AiClient aiClient;
    @Autowired
    private AiProperties aiProperties;
    @Autowired
    private IShopTypeService shopTypeService;

    // 自然语言特征词，命中才调用大模型；普通店名直接走关键词搜索
    private static final Pattern AI_QUERY_PATTERN = Pattern.compile(
            "人均|以内|以上|以下|附近|周边|评分|分以上|包间|适合|推荐|便宜|好吃|环境|服务|口味|左右|预算|\\d+\\s*(元|块)");

    /**
     * 根据id查询店铺（面试重点：缓存穿透的解决方案）
     * 当前用缓存穿透方案（CacheClient.queryWithPassThrough）：
     *   查缓存命中返回 → 未命中查数据库 → 写入缓存（店铺不存在时缓存空值，防止穿透攻击）
     * 另有两种缓存击穿方案（互斥锁/逻辑过期）在注释中保留，可切换
     * @param id 店铺id
     * @return 店铺信息
     */
    @Override
    public Result queryById(Long id) {
        //缓存穿透方案（没缓存就查库写入，所有店铺都能查）
        Shop shop = cacheClient
                .queryWithPassThrough(CACHE_SHOP_KEY,id,Shop.class,this::getById,CACHE_SHOP_TTL,TimeUnit.MINUTES);
        //互斥锁解决缓存击穿（缓存失效时只让一个请求重建缓存，其余等待）
//        Shop shop = queryWithMutex(id);
        //逻辑过期解决缓存击穿（缓存永不过期，逻辑上标记过期时间，异步重建，需先预热数据否则返回null）
//        Shop shop = cacheClient.queryWithLogicalExpire(
//                CACHE_SHOP_KEY , id , Shop.class , this::getById , 20L , TimeUnit.SECONDS);
        if(shop==null){
            return Result.fail("店铺不存在");
        }
        //返回数据
        return Result.ok(shop);
    }
    //线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR= Executors.newFixedThreadPool(10);

//    public Shop queryWithLogicalExpire(Long id){
//        String key=CACHE_SHOP_KEY + id;
//        //从redis查商铺缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        //判断是否存在----isBlank-为空
//        if(StrUtil.isBlank(shopJson)){
//            //存在,直接返回
//            return null;
//        }
//        //命中，需要先把json反序列化为对象
//        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
    //------------------------------------------------拿到店铺数据
//        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
    //------------------------------------拿到逻辑过期时间
//        LocalDateTime expireTime = redisData.getExpireTime();
//        //判断是否过期
//        if(expireTime.isAfter(LocalDateTime.now())){
//            //未过期，直接返回店铺信息
//            return shop;
//        }
//        //已过期，需要缓存重建
//        //缓存重建
//        //获得互斥锁
//        String lookKey=LOCK_SHOP_KEY+id;
//        boolean isLock=tryLock(lookKey);
//        //判断是否获取锁成功
//        if(isLock){
//           成功，开启独立显存，实现缓存重建
//            CACHE_REBUILD_EXECUTOR.submit(()->{
//                try {
//                    //重建缓存
//                    this.saveShop2Redis(id,30L);
//                } catch (Exception e) {
//                    throw new RuntimeException(e);
//                }finally {
//                    //释放锁
//                    unlock(lookKey);
//                }
//            });
//        }
//        //返回过期的店铺信息
//        return shop;
//    }

    /*public Shop queryWithMutex(Long id){
        String key=CACHE_SHOP_KEY + id;
        //从redis查商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //判断是否存在
        if(StrUtil.isNotBlank(shopJson)){
            //存在,直接返回
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        //命中的是否为空值
        if(shopJson!=null){
            //返回一个错误信息
            return null;
        }
        //实现缓存重建
        //获取互斥锁
        String lockKey="lock:shop:"+id;
        try {
            Boolean isLock=tryLock(lockKey);
            //判断是否获取成功
            if(!isLock){
                //失败，则进入休眠并重试
                Thread.sleep(50);
                return queryWithMutex(id);
            }

            //成功，根据id查询数据库
            Shop shop = getById(id);
            //不存在，返回错误
            if(shop==null){
                //将空值写入redis//----------------------------------防止缓存穿透
                stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
                //返回错误信息
                return null;
            }
            //存在，写入redis
            stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }finally {
            //释放互斥锁
            unlock(lockKey);
        }

        //返回数据
        return null;
    }*/


/*   public Shop queryWithPassThrough(Long id){
        String key=CACHE_SHOP_KEY + id;
        //从redis查商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //判断是否存在
        if(StrUtil.isNotBlank(shopJson)){
            //存在,直接返回
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        //命中的是否为空值
        if(shopJson!=null){
            //返回一个错误信息
            return null;
        }
        //不存在，根据id查数据库//继承了IService<Shop>，所以直接有这方法
        Shop shop = getById(id);
        //不存在，返回错误
        if(shop==null){
            //将空值写入redis//----------------------------------防止缓存穿透
            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
            //返回错误信息
            return null;
        }
        //存在，写入redis
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //返回数据
        return shop;
    }*/

    //缓存击穿问题解决----互斥锁---------------------------一个进程拿到锁，其他进程等待解锁
//    private boolean tryLock(String key){
//        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
//        return BooleanUtil.isTrue(flag);
//    }
//
//    private void unlock(String key){
//        stringRedisTemplate.delete(key);
//    }
//
//    public void saveShop2Redis(Long id,Long expireSeconds){
//        //查询店铺数据
//        Shop shop = getById(id);
//        //封装逻辑过期时间
//        RedisData redisData = new RedisData();
//        redisData.setData(shop);
//        //---------------------plusSeconds(expireSeconds)---往后（多少）秒
//        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
//        //写入Redis-------------------逻辑过期，会一直存在，但在逻辑上是过期了
//        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,JSONUtil.toJsonStr(redisData));
//    }



    /**
     * 更新店铺（面试重点：缓存更新策略 = 先更新数据库，再删除缓存）
     * 缓存不直接更新而是删除，下次查询时重新写，避免缓存和数据库不一致的复杂同步
     * @param shop 店铺数据
     * @return 更新结果
     */
    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id= shop.getId();
        if(id==null){
            return Result.fail("店铺id不存在");
        }
        //更新数据库
        updateById(shop);
        //删除缓存（cache:shop:id），下次查询重新加载
        stringRedisTemplate.delete(CACHE_SHOP_KEY+id);
        return Result.ok();
    }

    /**
     * 按类型分页查询店铺（面试重点：Redis GEO 附近店铺按距离排序查询）
     * 传了经纬度(x,y)：走 GEO 查询 shop:geo:类型id，按距离排序后滚动分页，返回店铺带距离字段
     * 没传坐标：普通数据库分页，支持人气/价格排序
     * @param typeId 类型id
     * @param current 页码
     * @param x 经度（可选）
     * @param y 纬度（可选）
     * @param sortBy 排序方式（可选）：sold-销量降序，priceAsc-价格升序，priceDesc-价格降序
     * @return 店铺列表
     */
    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y, String sortBy) {
        //1.判断是否需要根据坐标查询
        if(x==null||y==null){
            // 不需要坐标查询,按数据库查询（支持人气/价格排序）
            QueryChainWrapper<Shop> wrapper = query().eq("type_id", typeId);
            applySort(wrapper, sortBy);
            Page<Shop> page = wrapper.page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            //返回数据
            return Result.ok(page.getRecords());
        }

        //2.计算分页参数（GEO 是滚动分页，用偏移量截取当前页）
        int from=(current-1)*SystemConstants.DEFAULT_PAGE_SIZE;
        int end=current*SystemConstants.DEFAULT_PAGE_SIZE;

        //3.查询redis GEO，按照距离排序，取前end个。结果：shopId,distance
        String key=SHOP_GEO_KEY+typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo()//geosearch key bylonlat x y byradius 5000 withdistance
               .search(
                        key,
                        GeoReference.fromCoordinate(x, y),
                        new Distance(5000),//半径5000米内
                        RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs().includeDistance().limit(end)
                );
        //4.解析出id
        if(results==null){
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        //4.1截取from~end的部分（当前页的店铺）
        List<Long>ids=new ArrayList<>(list.size());
        Map<String,Distance> distanceMap=new HashMap<>(list.size());
        list.stream().skip(from).forEach(result->{
            //4.2获取店铺id
            String stopIdStr = result.getContent().getName();
            ids.add(Long.valueOf(stopIdStr));
            //4.3获取距离
            Distance distance = result.getDistance();
            distanceMap.put(stopIdStr,distance);
        });
        //如果GEO没查到任何店铺，直接返回空，避免拼出 IN () 的非法SQL
        if(ids.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        //5.根据id查询Shop（ORDER BY FIELD 保持与GEO返回的距离排序一致）
        String idStr=StrUtil.join(",",ids);
        List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        for (Shop shop : shops) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        //返回
        return Result.ok(shops);
    }

    /**
     * 按名称搜索店铺（支持人气/价格排序）
     * @param name 名称关键字
     * @param current 页码
     * @param sortBy 排序方式（可选）：sold-销量降序，priceAsc-价格升序，priceDesc-价格降序
     * @return 店铺列表
     */
    @Override
    public Result queryShopByName(String name, Integer current, String sortBy) {
        QueryChainWrapper<Shop> wrapper = query()
                .like(StrUtil.isNotBlank(name), "name", name);
        applySort(wrapper, sortBy);
        Page<Shop> page = wrapper.page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        return Result.ok(page.getRecords());
    }

    /**
     * AI 自然语言找店
     * 流程：意图判断 → 查缓存/调模型 → 条件清洗 → 组装查询 → 过严条件放宽
     * 模型未配置/超时/异常/输出非法时一律降级为关键词搜索，保证搜索可用性
     * @param q 用户口语化需求
     * @param sortBy 前端手动排序（可选，优先于模型解析结果）
     * @return 解析说明与店铺列表
     */
    @Override
    public Result aiSearch(String q, String sortBy) {
        String query = StrUtil.trim(q);
        if (StrUtil.isBlank(query)) {
            return Result.fail("请输入找店需求");
        }
        if (query.length() > 100) {
            query = query.substring(0, 100);
        }

        // 1. 普通短关键词不调模型，直接查库（省 token、降延迟）
        if (!needAi(query)) {
            return Result.ok(new AiSearchResult(false, false, "按关键词搜索", searchByName(query, sortBy)));
        }

        // 2. 相同输入优先命中缓存，避免重复调用模型
        String cacheKey = AI_SEARCH_KEY + DigestUtil.md5Hex(query);
        AiSearchCondition condition = getCachedCondition(cacheKey);
        if (condition == null) {
            condition = aiClient.parseCondition(query, listTypeNames());
            // 3. 模型未配置/超时/报错/输出非法 → 降级关键词搜索
            if (condition == null) {
                return Result.ok(new AiSearchResult(false, true,
                        "AI 解析失败，已按关键词搜索", searchByName(query, sortBy)));
            }
            condition = sanitize(condition);
            cacheCondition(cacheKey, condition);
        }

        // 4. 清洗后没有有效条件，给出补充提示，不做无意义的空搜索
        if (isEmptyCondition(condition)) {
            return Result.ok(new AiSearchResult(true, false,
                    "没识别出具体条件，试试更完整的说法：人均100以内的火锅、评分4.5以上的KTV",
                    Collections.emptyList()));
        }

        // 5. 组装查询，前端手动排序优先
        String finalSort = StrUtil.isNotBlank(sortBy) ? sortBy : condition.getSortBy();
        QueryChainWrapper<Shop> wrapper = query();
        applyCondition(wrapper, condition, false);
        applySort(wrapper, finalSort);
        List<Shop> shops = wrapper.page(new Page<>(1, SystemConstants.DEFAULT_PAGE_SIZE)).getRecords();
        String summary = buildSummary(condition);

        // 6. 价格/评分过严导致无结果时，放宽后重查一次
        if (shops.isEmpty() && (condition.getMinPrice() != null
                || condition.getMaxPrice() != null || condition.getMinScore() != null)) {
            QueryChainWrapper<Shop> relaxed = query();
            applyCondition(relaxed, condition, true);
            applySort(relaxed, finalSort);
            shops = relaxed.page(new Page<>(1, SystemConstants.DEFAULT_PAGE_SIZE)).getRecords();
            if (!shops.isEmpty()) {
                summary += "（已放宽价格/评分条件）";
            }
        }

        return Result.ok(new AiSearchResult(true, false, summary, shops));
    }

    // 出现自然语言特征才值得调模型（如“人均100”这类不完整输入也交给模型理解）
    private boolean needAi(String query) {
        return query.length() >= 2 && AI_QUERY_PATTERN.matcher(query).find();
    }

    // 降级用：按店名关键词查询
    private List<Shop> searchByName(String name, String sortBy) {
        QueryChainWrapper<Shop> wrapper = query().like(StrUtil.isNotBlank(name), "name", name);
        applySort(wrapper, sortBy);
        return wrapper.page(new Page<>(1, SystemConstants.MAX_PAGE_SIZE)).getRecords();
    }

    // 品类名列表放进 Prompt，限制模型只能从真实分类中选择
    private List<String> listTypeNames() {
        try {
            return shopTypeService.list().stream().map(ShopType::getName).collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("查询品类列表失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    // 通过品类名匹配数据库真实分类，匹配不到返回 null
    private Long matchTypeId(String typeName) {
        if (StrUtil.isBlank(typeName)) {
            return null;
        }
        try {
            return shopTypeService.list().stream()
                    .filter(type -> typeName.equals(type.getName())
                            || typeName.contains(type.getName())
                            || type.getName().contains(typeName))
                    .map(ShopType::getId)
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    // 校验模型输出：非法值直接丢弃，品类必须真实存在
    private AiSearchCondition sanitize(AiSearchCondition condition) {
        condition.setTypeName(cleanText(condition.getTypeName()));
        condition.setKeyword(cleanText(condition.getKeyword()));
        condition.setArea(cleanText(condition.getArea()));
        condition.setSortBy(cleanText(condition.getSortBy()));

        if (condition.getMinPrice() != null && condition.getMinPrice() < 0) {
            condition.setMinPrice(null);
        }
        if (condition.getMaxPrice() != null && (condition.getMaxPrice() <= 0 || condition.getMaxPrice() > 10000)) {
            condition.setMaxPrice(null);
        }
        if (condition.getMinPrice() != null && condition.getMaxPrice() != null
                && condition.getMinPrice() > condition.getMaxPrice()) {
            Long temp = condition.getMinPrice();
            condition.setMinPrice(condition.getMaxPrice());
            condition.setMaxPrice(temp);
        }
        if (condition.getMinScore() != null && (condition.getMinScore() < 0 || condition.getMinScore() > 5)) {
            condition.setMinScore(null);
        }
        if (condition.getSortBy() != null
                && !Arrays.asList("sold", "priceAsc", "priceDesc", "score").contains(condition.getSortBy())) {
            condition.setSortBy(null);
        }
        if (condition.getKeyword() != null && condition.getKeyword().length() > 30) {
            condition.setKeyword(null);
        }
        if (condition.getArea() != null && condition.getArea().length() > 30) {
            condition.setArea(null);
        }
        if (condition.getTypeName() != null && matchTypeId(condition.getTypeName()) == null) {
            condition.setTypeName(null);
        }
        return condition;
    }

    // 模型常用字符串 "null" 表示空值，这里统一清掉
    private String cleanText(String text) {
        if (StrUtil.isBlank(text) || "null".equalsIgnoreCase(text.trim())) {
            return null;
        }
        return text.trim();
    }

    private boolean isEmptyCondition(AiSearchCondition condition) {
        return condition.getTypeName() == null
                && condition.getKeyword() == null
                && condition.getArea() == null
                && condition.getMinPrice() == null
                && condition.getMaxPrice() == null
                && condition.getMinScore() == null;
    }

    // ignorePriceAndScore=true 时只保留品类/关键词/商圈，用于条件过严时放宽
    private void applyCondition(QueryChainWrapper<Shop> wrapper, AiSearchCondition condition, boolean ignorePriceAndScore) {
        Long typeId = matchTypeId(condition.getTypeName());
        if (typeId != null) {
            wrapper.eq("type_id", typeId);
        }
        if (condition.getKeyword() != null) {
            wrapper.like("name", condition.getKeyword());
        }
        if (condition.getArea() != null) {
            wrapper.like("area", condition.getArea());
        }
        if (!ignorePriceAndScore) {
            if (condition.getMinPrice() != null) {
                wrapper.ge("avg_price", condition.getMinPrice());
            }
            if (condition.getMaxPrice() != null) {
                wrapper.le("avg_price", condition.getMaxPrice());
            }
            if (condition.getMinScore() != null) {
                wrapper.ge("score", (int) Math.round(condition.getMinScore() * 10));
            }
        }
    }

    private String buildSummary(AiSearchCondition condition) {
        List<String> parts = new ArrayList<>();
        if (condition.getTypeName() != null) {
            parts.add("品类：" + condition.getTypeName());
        }
        if (condition.getKeyword() != null) {
            parts.add("关键词：" + condition.getKeyword());
        }
        if (condition.getArea() != null) {
            parts.add("商圈：" + condition.getArea());
        }
        if (condition.getMinPrice() != null || condition.getMaxPrice() != null) {
            String min = condition.getMinPrice() == null ? "0" : condition.getMinPrice().toString();
            String max = condition.getMaxPrice() == null ? "不限" : condition.getMaxPrice().toString();
            parts.add("人均 " + min + "-" + max + " 元");
        }
        if (condition.getMinScore() != null) {
            parts.add("评分 ≥" + condition.getMinScore());
        }
        return "AI 已按 " + String.join(" · ", parts) + " 筛选";
    }

    private AiSearchCondition getCachedCondition(String key) {
        try {
            String json = stringRedisTemplate.opsForValue().get(key);
            return StrUtil.isBlank(json) ? null : JSONUtil.toBean(json, AiSearchCondition.class);
        } catch (Exception e) {
            log.warn("读取 AI 解析缓存失败: {}", e.getMessage());
            return null;
        }
    }

    private void cacheCondition(String key, AiSearchCondition condition) {
        try {
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(condition),
                    aiProperties.getCacheTtlMinutes(), TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("写入 AI 解析缓存失败: {}", e.getMessage());
        }
    }

    // 按前端传入的排序方式拼接 ORDER BY：sold-销量降序，priceAsc-价格升序，priceDesc-价格降序，score-评分降序
    private void applySort(QueryChainWrapper<Shop> wrapper, String sortBy) {
        if ("sold".equals(sortBy)) {
            wrapper.orderByDesc("sold");
        } else if ("priceAsc".equals(sortBy)) {
            wrapper.orderByAsc("avg_price");
        } else if ("priceDesc".equals(sortBy)) {
            wrapper.orderByDesc("avg_price");
        } else if ("score".equals(sortBy)) {
            wrapper.orderByDesc("score");
        }
    }

    // GEO 数据预热：把每个类型下的店铺坐标写入 redis（shop:geo:类型id），否则按距离查询查不到
    public void saveShop2RedisForGeo(){
        List<Shop> shops = list();
        //按类型分组，把同一类型的店铺坐标存到同一个 key
        shops.stream().collect(Collectors.groupingBy(Shop::getTypeId))
                .forEach((typeId, list) -> {
                    String key = SHOP_GEO_KEY + typeId;
                    list.forEach(shop -> stringRedisTemplate.opsForGeo()
                            .add(key, new Point(shop.getX(), shop.getY()), shop.getId().toString()));
                });
    }
}
