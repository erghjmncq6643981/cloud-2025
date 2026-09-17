package com.chandler.freeswitch.client.example.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 呼叫中心智能排队分发调度算法单元测试
 * 覆盖：最长空闲坐席优先 (LONGEST_IDLE)、熟客记忆优先 (LAST_AGENT)、平滑排水节点过滤 (DRAINING)
 */
@DisplayName("RoutingQueueAlgorithm 智能排队调度算法测试")
public class RoutingQueueAlgorithmTest {

    record AgentCandidate(String name, String extension, long idleSeconds, boolean isDraining, int todayCallCount) {}

    /**
     * 算法 1: 最长空闲坐席优先 (LONGEST_IDLE)
     */
    private Optional<AgentCandidate> selectLongestIdleAgent(List<AgentCandidate> agents) {
        return agents.stream()
                .filter(a -> !a.isDraining())
                .max(Comparator.comparingLong(AgentCandidate::idleSeconds));
    }

    /**
     * 算法 2: 熟客记忆优先 (LAST_AGENT)，若历史坐席忙碌或排水，则回退到最长空闲
     */
    private Optional<AgentCandidate> selectLastAgentOrFallback(List<AgentCandidate> agents, String lastAgentExt) {
        // 先查历史坐席是否空闲就绪
        Optional<AgentCandidate> matched = agents.stream()
                .filter(a -> a.extension().equals(lastAgentExt) && !a.isDraining())
                .findFirst();
        if (matched.isPresent()) {
            return matched;
        }
        // 回退到最长空闲
        return selectLongestIdleAgent(agents);
    }

    @Test
    @DisplayName("测试最长空闲坐席优先选择 (LONGEST_IDLE)")
    void testSelectLongestIdleAgent() {
        List<AgentCandidate> candidates = List.of(
                new AgentCandidate("舒欣", "1007", 320, false, 8),
                new AgentCandidate("森林", "1008", 1200, false, 5), // 最长空闲 1200秒
                new AgentCandidate("亚峰", "1009", 150, false, 11)
        );

        Optional<AgentCandidate> selected = selectLongestIdleAgent(candidates);
        assertTrue(selected.isPresent());
        assertEquals("森林", selected.get().name());
        assertEquals("1008", selected.get().extension());
    }

    @Test
    @DisplayName("测试平滑排水模式 (DRAINING) 坐席自动被排除分发")
    void testDrainingAgentExclusion() {
        List<AgentCandidate> candidates = List.of(
                new AgentCandidate("陈松", "800213", 2500, true, 4), // 空闲最久但正处于 DRAINING 模式
                new AgentCandidate("舒欣", "1007", 600, false, 9)
        );

        Optional<AgentCandidate> selected = selectLongestIdleAgent(candidates);
        assertTrue(selected.isPresent());
        assertEquals("舒欣", selected.get().name(), "处于排水模式的坐席不应被分发新呼叫");
    }

    @Test
    @DisplayName("测试熟客记忆坐席优先 (LAST_AGENT) 命中与未命中回退")
    void testLastAgentRoutingAndFallback() {
        List<AgentCandidate> candidates = List.of(
                new AgentCandidate("舒欣", "1007", 300, false, 6),
                new AgentCandidate("钱丁君", "1001", 100, false, 12),
                new AgentCandidate("张闯", "1010", 900, false, 3)
        );

        // 1. 命中历史坐席 钱丁君 (1001)
        Optional<AgentCandidate> hit = selectLastAgentOrFallback(candidates, "1001");
        assertTrue(hit.isPresent());
        assertEquals("钱丁君", hit.get().name());

        // 2. 历史坐席不在候选池中 (如忙碌/离线)，回退至最长空闲坐席 张闯 (1010)
        Optional<AgentCandidate> fallback = selectLastAgentOrFallback(candidates, "9999");
        assertTrue(fallback.isPresent());
        assertEquals("张闯", fallback.get().name());
    }
}
