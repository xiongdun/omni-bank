package com.omnibank.service.agent.dd.tool.impl;

import java.util.List;

/**
 * 材料清单工具输出：材料条目与清单证据ID
 */
public record DocumentMaterialsSnapshot(List<DocumentMaterialSnapshot> materials, Long evidenceId) {
}
