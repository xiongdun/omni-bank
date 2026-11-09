package com.omnibank.service.agent.dd.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 尽调任务状态：状态流转统一由 TaskStateMachine 管理，Controller 不得直接改状态
 */
@Getter
@AllArgsConstructor
public enum TaskStatus {

    DRAFT("草稿", 0),
    INITIALIZING("初始化", 10),
    SCENARIO_IDENTIFYING("业务场景识别", 20),
    PLANNING("生成尽调计划", 30),
    DATA_COLLECTING("数据采集", 40),
    ANALYZING("Agent 分析", 60),
    WAITING_FOR_SUPPLEMENT("等待补充材料", 60),
    REANALYZING("增量重分析", 70),
    RISK_ASSESSING("综合风险评估", 80),
    REPORT_GENERATING("生成报告", 90),
    WAITING_FOR_REVIEW("等待人工复核", 95),
    RETURNED("退回", 80),
    COMPLETED("人工复核完成", 100),
    FAILED("任务失败", 0),
    CANCELLED("任务取消", 0);

    private final String desc;

    /** 进度百分比（仅用于状态查询展示，不代表真实执行耗时） */
    private final int progress;

    /** 终态：不可再流转 */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }
}
