package chaindeal.dto;

import chaindeal.entity.Shop;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * AI 找店返回结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiSearchResult {

    /** 是否成功用 AI 解析了条件（含缓存命中） */
    private boolean aiUsed;

    /** 是否因模型超时/异常降级为关键词搜索 */
    private boolean degraded;

    /** 条件说明，用于前端展示 */
    private String summary;

    /** 店铺列表 */
    private List<Shop> shops;
}
