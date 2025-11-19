package org.chandler25.ai.demo.service;

import com.google.common.collect.Lists;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.chandler25.ai.demo.common.*;
import org.chandler25.ai.demo.domain.dto.AiContextDTO;
import org.chandler25.ai.demo.domain.dto.AiTemplateDTO;
import org.chandler25.ai.demo.domain.bo.UserQuestion;
import org.chandler25.ai.demo.respository.entity.AiGeneratedDataHistory;
import org.chandler25.ai.demo.respository.entity.AiTemplate;
import org.chandler25.ai.demo.respository.entity.User;
import org.chandler25.ai.demo.respository.mapper.AiGeneratedDataHistoryMapper;
import org.chandler25.ai.demo.respository.mapper.AiTemplateMapper;
import org.chandler25.ai.demo.util.KeyUtil;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.chandler25.ai.demo.respository.entity.table.AiGeneratedDataHistoryTableDef.AI_GENERATED_DATA_HISTORY;
import static org.chandler25.ai.demo.respository.entity.table.AiTemplateTableDef.AI_TEMPLATE;
import static org.chandler25.ai.demo.respository.entity.table.UserTableDef.USER;

/**
 * 类功能描述
 *
 * @author 钱丁君-chandler 2025/11/16 14:54
 * @version 1.0.0
 * @since 21
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiService {

    private final ChatClient chatClient;

    private final AiTemplateMapper aiTemplateMapper;

    private final AiGeneratedDataHistoryMapper historyMapper;

    @Transactional(rollbackFor = Exception.class)
    public UserQuestion chat(AiContextDTO aiContext, Boolean isSync) {
        UserQuestion userQuestion = new UserQuestion();
        AiGeneratedDataHistory dataHistory = new AiGeneratedDataHistory();
        User loginUser = UserLoginCheckHelper.getUserSession();
        if (Objects.nonNull(loginUser)) {
            aiContext.setUserId(loginUser.getId());
            dataHistory.setLastUpdateBy(loginUser.getLoginName());
            dataHistory.setCreateBy(loginUser.getLoginName());
        }
        if (Objects.isNull(aiContext.getFieldType())) {
            aiContext.setFieldType(AiFieldType.COMMON);
        }
        userQuestion.setQuestion(aiContext.getQuestion());
        dataHistory.setUserId(loginUser.getId());
        dataHistory.setQuestionStatus(AiQuestionStatus.NEW);
        dataHistory.setModelType(AiModelType.DEEPSEEK);
        dataHistory.setCreateBy(loginUser.getCreateBy());
        dataHistory.setLastUpdateBy(loginUser.getCreateBy());

        String questionId = KeyUtil.getQuestionKey();
        dataHistory.setQuestionId(questionId);
        userQuestion.setQuestionId(questionId);
        dataHistory.setData(userQuestion);

        addChatHistory(dataHistory);
        if (Boolean.FALSE.equals(isSync)) {
            CompletableFuture.runAsync(() -> {
                String answer = getAnswer(aiContext);
                userQuestion.setAnswer(answer);
                dataHistory.setQuestionStatus(AiQuestionStatus.ANSWERED);
                addChatHistory(dataHistory);
            });
            return userQuestion;
        }
        String answer = getAnswer(aiContext);
        userQuestion.setAnswer(answer);
        dataHistory.setQuestionStatus(AiQuestionStatus.ANSWERED);
        addChatHistory(dataHistory);
        return userQuestion;
    }

    public String getAnswer(AiContextDTO aiContext) {
        String answer = "";
        if (AiFieldType.COMMON.equals(aiContext.getFieldType())) {
            answer = chatClient.prompt()
                    .user(aiContext.getQuestion())
                    .call()
                    .content();
        } else {
            Optional<AiTemplate> aiTemplate = getTemplate(aiContext.getFieldType());
            if (aiTemplate.isPresent()) {
                answer = chatClient.prompt()
                        .user(u -> u
                                .text(aiTemplate.get().getTemplate())
                                .param("question", aiContext.getQuestion()))
                        .call()
                        .content();
            } else {
                log.warn("{} 缺少提示词模板！", aiContext.getFieldType().name());
                throw new RuntimeException("缺少提示词模板!");
            }
        }
        return answer;
    }

    public List<AiGeneratedDataHistory> chatHistories() {
        User loginUser = UserLoginCheckHelper.getUserSession();
        if (Objects.isNull(loginUser)) {
            return Lists.newArrayList();
        }
        QueryWrapper query = QueryWrapper.create()
                .select(AI_GENERATED_DATA_HISTORY.ALL_COLUMNS)
                .from(AI_GENERATED_DATA_HISTORY)
                .where(AI_GENERATED_DATA_HISTORY.USER_ID.eq(loginUser.getId()))
                .orderBy(AI_GENERATED_DATA_HISTORY.ID, false)
                .limit(5);
        return historyMapper.selectListByQuery(query);
    }

    public Optional<AiTemplate> getTemplate(AiFieldType fieldType) {
        List<AiTemplate> aiTemplates = queryAiTemplates(fieldType);
        if (CollectionUtils.isEmpty(aiTemplates)) {
            return Optional.empty();
        }
        return aiTemplates.stream().filter(t -> TemplateStatus.ENABLED.equals(t.getStatus()))
                .findFirst();
    }

    public List<AiTemplate> queryAiTemplates(AiFieldType fieldType) {
        QueryWrapper query = QueryWrapper.create()
                .select(AI_TEMPLATE.ALL_COLUMNS)
                .from(AI_TEMPLATE);
        if (Objects.nonNull(fieldType)) {
            query.where(AI_TEMPLATE.FIELD_TYPE.eq(fieldType.name()));
        }
        return aiTemplateMapper.selectListByQuery(query);
    }

    public void addOrUpdate(AiTemplateDTO aiTemplateDTO) {
        String loginUserName = "";
        User loginUser = UserLoginCheckHelper.getUserSession();
        if (Objects.isNull(loginUser)) {
            loginUserName = loginUser.getLoginName();
        }
        AiTemplate aiTemplate = new AiTemplate();
        BeanUtils.copyProperties(aiTemplateDTO,aiTemplate);
        aiTemplate.setLastUpdateBy(loginUserName);
        if (Objects.isNull(aiTemplate.getId())) {
            aiTemplate.setCreateBy(loginUserName);
            aiTemplateMapper.insert(aiTemplate);
        } else {
            aiTemplateMapper.update(aiTemplate);
        }
    }

    public void addChatHistory(AiGeneratedDataHistory dataHistory) {
        if(Objects.isNull(dataHistory.getQuestionStatus())){
            dataHistory.setQuestionStatus(AiQuestionStatus.NEW);
        }
        AiGeneratedDataHistory old = queryHistoryByKey(dataHistory.getQuestionId());
        if (Objects.isNull(old)) {
            historyMapper.insert(dataHistory);
        } else {
            dataHistory.setId(old.getId());
            dataHistory.setQuestionId(old.getQuestionId());
            dataHistory.setCreateTime(old.getCreateTime());
            dataHistory.setCreateBy(old.getCreateBy());
            historyMapper.update(dataHistory);
        }
    }

    public AiGeneratedDataHistory queryHistoryByKey(String questionId) {
        QueryWrapper query = QueryWrapper.create()
                .select(AI_GENERATED_DATA_HISTORY.ALL_COLUMNS)
                .from(AI_GENERATED_DATA_HISTORY)
                .where(AI_GENERATED_DATA_HISTORY.QUESTION_ID.eq(questionId))
                .limit(1);
        return historyMapper.selectOneByQuery(query);
    }
}