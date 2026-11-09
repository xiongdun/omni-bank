package com.omnibank.service.agent.dd.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.omnibank.common.core.exception.BizException;
import com.omnibank.service.agent.dd.enums.TaskStatus;
import org.junit.jupiter.api.Test;

/**
 * 状态机流转规则单元测试
 */
class TaskStateMachineTest {

    private final TaskStateMachine stateMachine = new TaskStateMachine();

    @Test
    void 草稿可以提交和取消() {
        assertThatCode(() -> stateMachine.check(TaskStatus.DRAFT, TaskStatus.INITIALIZING))
                .doesNotThrowAnyException();
        assertThatCode(() -> stateMachine.check(TaskStatus.DRAFT, TaskStatus.CANCELLED))
                .doesNotThrowAnyException();
    }

    @Test
    void 草稿不能直接跳到分析() {
        assertThatThrownBy(() -> stateMachine.check(TaskStatus.DRAFT, TaskStatus.ANALYZING))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不允许");
    }

    @Test
    void 终态不可再流转() {
        for (TaskStatus terminal : new TaskStatus[]{TaskStatus.COMPLETED, TaskStatus.FAILED, TaskStatus.CANCELLED}) {
            assertThatThrownBy(() -> stateMachine.check(terminal, TaskStatus.INITIALIZING))
                    .isInstanceOf(BizException.class);
        }
    }

    @Test
    void 初始化链路可以到达P1终点() {
        assertThatCode(() -> {
            stateMachine.check(TaskStatus.DRAFT, TaskStatus.INITIALIZING);
            stateMachine.check(TaskStatus.INITIALIZING, TaskStatus.SCENARIO_IDENTIFYING);
            stateMachine.check(TaskStatus.SCENARIO_IDENTIFYING, TaskStatus.PLANNING);
        }).doesNotThrowAnyException();
        assertThat(TaskStatus.PLANNING.isTerminal()).isFalse();
        assertThat(TaskStatus.COMPLETED.getProgress()).isEqualTo(100);
    }
}
