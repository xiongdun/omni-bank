package com.omnibank.service.agent.dd.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnibank.common.core.exception.BizException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * JSON 字段读写单元测试：空值兜底、读写往返与非法 JSON 异常。
 */
class DdJsonSupportTest {

    private final DdJsonSupport jsonSupport = new DdJsonSupport(new ObjectMapper());

    @Test
    void 空值读取返回空集合或空Map() {
        assertThat(jsonSupport.readStringList(null)).isEmpty();
        assertThat(jsonSupport.readStringList("  ")).isEmpty();
        assertThat(jsonSupport.readMap("")).isEmpty();
        assertThat(jsonSupport.readStringListMap(null)).isEmpty();
        assertThat(jsonSupport.read(null, new TypeReference<Map<String, Object>>() {
        })).isNull();
    }

    @Test
    void 写入后可原样读回() {
        String listJson = jsonSupport.write(List.of("A", "B"));
        String mapJson = jsonSupport.write(Map.of("taskId", 1L));

        assertThat(jsonSupport.readStringList(listJson)).containsExactly("A", "B");
        assertThat(jsonSupport.readMap(mapJson)).containsEntry("taskId", 1);
    }

    @Test
    void 字符串集合Map读写往返() {
        String json = jsonSupport.write(Map.of("PLANNER", List.of("SUBJECT_VERIFICATION")));

        assertThat(jsonSupport.readStringListMap(json))
                .containsEntry("PLANNER", List.of("SUBJECT_VERIFICATION"));
    }

    @Test
    void 非法JSON抛业务异常() {
        assertThatThrownBy(() -> jsonSupport.readStringList("{not-json"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("JSON");
        assertThatThrownBy(() -> jsonSupport.readMap("[1,2]"))
                .isInstanceOf(BizException.class);
    }
}
