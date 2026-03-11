package org.chandler25.ai.demo.service;

import com.google.common.collect.Lists;
import com.mybatisflex.core.query.QueryCondition;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.chandler25.ai.demo.common.UserLoginCheckHelper;
import org.chandler25.ai.demo.domain.bo.LabelNode;
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
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.chandler25.ai.demo.respository.entity.table.LabelNoteRelateTableDef.LABEL_NOTE_RELATE;
import static org.chandler25.ai.demo.respository.entity.table.LabelTreeNodeTableDef.LABEL_TREE_NODE;
import static org.chandler25.ai.demo.respository.entity.table.NoteTableDef.NOTE;
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

    public List<LabelNode> queryAllLabels() {
        User loginUser = UserLoginCheckHelper.getUserSession();
        if (Objects.isNull(loginUser)) {
            throw new RuntimeException("请登录");
        }
        QueryWrapper query = QueryWrapper.create()
                .select(LABEL_TREE_NODE.ALL_COLUMNS)
                .from(LABEL_TREE_NODE)
                .where(LABEL_TREE_NODE.CREATE_BY.eq(loginUser.getLoginName()))
                .and(LABEL_TREE_NODE.LABEL_PARENT.eq(0))
                .orderBy(LABEL_TREE_NODE.ID, true);
        List<LabelTreeNode> rootLabels = nodeMapper.selectListByQuery(query);
        List<LabelNode> labels = rootLabels.stream().map(rootL -> {
            List<LabelTreeNode> children = nodeMapper.selectSubLabels(rootL.getId());
            return toTree(rootL, children);
        }).toList();
        return labels;
    }

    private LabelNode toTree(LabelTreeNode node, List<LabelTreeNode> children) {
        LabelNode parent = new LabelNode();
        BeanUtils.copyProperties(node, parent);
        List<LabelTreeNode> subNodes = children.stream()
                .filter(c -> c.getLabelParent().equals(parent.getId()))
                .toList();
        if (CollectionUtils.isEmpty(subNodes)) {
            return parent;
        }
        subNodes.forEach(sNode -> toTree(sNode, children));
        List<LabelNode> subLabels = subNodes.stream().map(c -> {
                    LabelNode sub = new LabelNode();
                    BeanUtils.copyProperties(c, sub);
                    return sub;
                })
                .toList();
        parent.setNodes(subLabels);
        return parent;
    }

    public LabelNode querySubLabel(Long labelId){
        User loginUser = UserLoginCheckHelper.getUserSession();
        if (Objects.isNull(loginUser)) {
            throw new RuntimeException("请登录");
        }
        QueryWrapper query = QueryWrapper.create()
                .select(LABEL_TREE_NODE.ALL_COLUMNS)
                .from(LABEL_TREE_NODE)
                .where(LABEL_TREE_NODE.CREATE_BY.eq(loginUser.getLoginName()))
                .and(LABEL_TREE_NODE.LABEL_PARENT.eq(labelId))
                .orderBy(LABEL_TREE_NODE.ID, true);
        List<LabelTreeNode> subLabels = nodeMapper.selectListByQuery(query);
        LabelTreeNode node=new LabelTreeNode();
        node.setId(labelId);
        return toTree(node, subLabels);
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
        relate.setCreateBy(loginUser.getLastUpdateBy());
        QueryWrapper query = QueryWrapper.create()
                .select(LABEL_NOTE_RELATE.ALL_COLUMNS)
                .from(LABEL_NOTE_RELATE)
                .where(LABEL_NOTE_RELATE.NOTE_ID.eq(noteId))
                .and(LABEL_NOTE_RELATE.LABEL_ID.eq(labelId))
                .limit(1);
        LabelNoteRelate old= relateMapper.selectOneByQuery(query);
        if(Objects.isNull(old)){
            relateMapper.insert(relate);
        }
    }

    /**
     * @param
     * @return {@Description} 传入labelId，查询标签下所有笔记
     * {@Author} chandler
     * {@create} 2025/11/21 14:03
     */
    public List<Note> queryNotes() {
        User loginUser = UserLoginCheckHelper.getUserSession();
        if (Objects.isNull(loginUser)) {
            throw new RuntimeException("请登录");
        }
        QueryWrapper query = QueryWrapper.create()
                .select(NOTE.ALL_COLUMNS)
                .from(NOTE)
                .where(NOTE.CREATE_BY.eq(loginUser.getLoginName()))
                .orderBy(NOTE.LAST_UPDATE_TIME,false);
        return noteMapper.selectListByQuery(query);
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

    public void updateNote(NoteDTO note) {
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

    public void delNote(Long noteId){
        noteMapper.deleteById(noteId);
    }
}