package chaindeal.dto;

import lombok.Data;

/**
 * 大模型解析出的找店条件，所有字段都可为空（空表示不限）
 */
@Data
public class AiSearchCondition {

    /** 品类名称，如：美食、KTV */
    private String typeName;

    /** 店铺或菜系关键词 */
    private String keyword;

    /** 人均价格下限 */
    private Long minPrice;

    /** 人均价格上限 */
    private Long maxPrice;

    /** 商圈，如：运河上街 */
    private String area;

    /** 最低评分，0~5，一位小数 */
    private Double minScore;

    /** 排序：sold-销量降序，priceAsc-价格升序，priceDesc-价格降序，score-评分降序 */
    private String sortBy;
}
