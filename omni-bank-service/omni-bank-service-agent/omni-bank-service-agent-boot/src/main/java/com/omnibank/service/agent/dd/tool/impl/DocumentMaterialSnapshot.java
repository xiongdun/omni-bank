package com.omnibank.service.agent.dd.tool.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Mock 材料清单条目：字段与影像平台材料元信息对齐，有效期用于材料时效检查
 */
public record DocumentMaterialSnapshot(String fileName, String fileExtension, int pageCount,
                                       LocalDateTime uploadedAt, String fileHash, String textExcerpt,
                                       LocalDate validUntil) {
}
