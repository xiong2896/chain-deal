package chaindeal.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 大模型配置
 * 密钥放 application-local.yaml（已被 .gitignore 排除，不会提交仓库）
 */
@Data
@Component
@ConfigurationProperties(prefix = "chaindeal.ai")
public class AiProperties {

    /** OpenAI 兼容接口地址，DeepSeek 为 https://api.deepseek.com */
    private String baseUrl = "https://api.deepseek.com";

    /** 接口密钥 */
    private String apiKey;

    /** 模型名 */
    private String model = "deepseek-chat";

    /** 调用超时（毫秒），超时即降级关键词搜索 */
    private int timeoutMs = 2500;

    /** 解析结果缓存时长（分钟） */
    private long cacheTtlMinutes = 30;
}
