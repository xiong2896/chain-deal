package chaindeal.service.impl;

import chaindeal.entity.BlogComments;
import chaindeal.mapper.BlogCommentsMapper;
import chaindeal.service.IBlogCommentsService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author ChainDeal
 * @since 2021-12-22
 */
@Service
public class BlogCommentsServiceImpl extends ServiceImpl<BlogCommentsMapper, BlogComments> implements IBlogCommentsService {
    // 评论服务实现：课程未实现评论业务，仅提供 MyBatis-Plus 通用 CRUD
}
