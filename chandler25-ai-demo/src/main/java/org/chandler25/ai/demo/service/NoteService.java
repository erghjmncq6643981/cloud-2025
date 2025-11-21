package org.chandler25.ai.demo.service;

import com.mybatisflex.core.query.QueryCondition;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.chandler25.ai.demo.common.UserLoginCheckHelper;
import org.chandler25.ai.demo.domain.bo.NoteData;
import org.chandler25.ai.demo.domain.dto.LabelDTO;
import org.chandler25.ai.demo.domain.dto.NoteDTO;
import org.chandler25.ai.demo.respository.entity.LabelNoteRelate;
import org.chandler25.ai.demo.respository.entity.LabelTreeNode;
import org.chandler25.ai.demo.respository.entity.Note;
import org.chandler25.ai.demo.respository.entity.User;
import org.chandler25.ai.demo.respository.mapper.LabelNoteRelateMapper;
import org.chandler25.ai.demo.respository.mapper.LabelTreeNodeMapper;
import org.chandler25.ai.demo.respository.mapper.NoteMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

import static org.chandler25.ai.demo.respository.entity.table.LabelTreeNodeTableDef.LABEL_TREE_NODE;
import static org.chandler25.ai.demo.respository.entity.table.UserTableDef.USER;

/**
 * 笔记管理
 *
 * @author 钱丁君-chandler 2025/11/20 17:39
 * @version 1.0.0
 * @since 21
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NoteService {
    private final NoteMapper noteMapper;
    private final LabelTreeNodeMapper nodeMapper;
    private final LabelNoteRelateMapper relateMapper;

    @Transactional
    public void addOrUpdateLabel(LabelDTO label) {
        User loginUser = UserLoginCheckHelper.getUserSession();
        if (Objects.isNull(loginUser)) {
            throw new RuntimeException("请登录");
        }
        LabelTreeNode labelNode = new LabelTreeNode();
        BeanUtils.copyProperties(label, labelNode);
        labelNode.setLastUpdateBy(loginUser.getLoginName());
        labelNode.setUserId(loginUser.getId());
        if (Objects.isNull(label.getId())) {
            labelNode.setCreateBy(loginUser.getLoginName());
            nodeMapper.insert(labelNode);
        } else {
            nodeMapper.update(labelNode);
        }
    }

    public void delLabel(Long labelId) {
        nodeMapper.deleteById(labelId);
    }

    public void queryAllLabels() {
        User loginUser = UserLoginCheckHelper.getUserSession();
        if (Objects.isNull(loginUser)) {
            throw new RuntimeException("请登录");
        }
        QueryWrapper query = QueryWrapper.create()
                .select(LABEL_TREE_NODE.ALL_COLUMNS)
                .from(LABEL_TREE_NODE)
                .where(LABEL_TREE_NODE.CREATE_BY.eq(loginUser.getLoginName()));
    }

    public void relate(Long labelId,Long noteId){
        User loginUser = UserLoginCheckHelper.getUserSession();
        if (Objects.isNull(loginUser)) {
            throw new RuntimeException("请登录");
        }
        LabelNoteRelate relate=new LabelNoteRelate();
        relate.setNoteId(noteId);
        relate.setLabelId(labelId);
        relate.setLastUpdateBy(loginUser.getLastUpdateBy());
//        QueryCondition whereConditions=QueryCondition.create();
//        whereConditions.
//        relateMapper.selectOneByCondition()
    }

    /**
     * @param
     * @return {@Description} 传入labelId，查询标签下所有笔记
     * {@Author} chandler
     * {@create} 2025/11/21 14:03
     */
    public void queryNotes() {

    }

    public Note queryNoteById(Long noteId) {
        return noteMapper.selectOneById(noteId);
    }

    public void addNote(NoteDTO note) {
        User loginUser = UserLoginCheckHelper.getUserSession();
        if (Objects.isNull(loginUser)) {
            throw new RuntimeException("请登录");
        }
        Note n = new Note();
        n.setLastUpdateBy(loginUser.getLoginName());
        n.setCreateBy(loginUser.getLoginName());
        n.setUserId(loginUser.getId());
        NoteData data = new NoteData();
        data.setContent(note.getContent());
        noteMapper.insert(n);
    }

    public void update(NoteDTO note) {
        User loginUser = UserLoginCheckHelper.getUserSession();
        if (Objects.isNull(loginUser)) {
            throw new RuntimeException("请登录");
        }
        Note n = noteMapper.selectOneById(note.getId());
        if (Objects.isNull(n)) {
            throw new RuntimeException("没有找到对应的笔记");
        }
        n.setLastUpdateBy(loginUser.getLoginName());
        n.setUserId(loginUser.getId());
        NoteData data = new NoteData();
        data.setContent(note.getContent());
        noteMapper.update(n);
    }


}