package org.chandler25.ai.demo.respository.mapper;

import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.chandler25.ai.demo.respository.entity.LabelTreeNode;

import java.util.List;

/**
 * 类功能描述
 *
 * @author 钱丁君-chandler 2025/11/20 17:43
 * @version 1.0.0
 * @since 21
 */
public interface LabelTreeNodeMapper extends BaseMapper<LabelTreeNode> {
    List<LabelTreeNode> selectSubLabels(@Param("parentId") Long id);
}
