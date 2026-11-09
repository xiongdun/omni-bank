package com.omnibank.common.web.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.omnibank.common.core.enums.BaseEnum;
import java.io.IOException;

/**
 * BaseEnum 反序列化支持：与序列化侧 @JsonValue(code) 对称，
 * 请求体中的数字 code（含数字字符串）自动反序列化回对应枚举常量，无匹配 code 时报 400 语义错误。
 * 注册为 Jackson Module 后，凡实现 {@link BaseEnum} 的枚举字段全局生效，业务枚举零配置。
 */
public class BaseEnumJacksonModule extends SimpleModule {

    public BaseEnumJacksonModule() {
        super("omni-bank-base-enum");
        addDeserializer(BaseEnum.class, new CodeDeserializer(null));
    }

    /**
     * 按 code 匹配枚举常量。通过 ContextualDeserializer 感知实际字段类型：
     * 目标类型是实现 BaseEnum 的枚举才按 code 匹配，其他枚举（非 BaseEnum）保持 Jackson 默认行为。
     */
    static class CodeDeserializer extends JsonDeserializer<BaseEnum> implements ContextualDeserializer {

        private final Class<?> enumType;

        CodeDeserializer(Class<?> enumType) {
            this.enumType = enumType;
        }

        @Override
        public BaseEnum deserialize(JsonParser parser, DeserializationContext ctxt) throws IOException {
            if (enumType == null || parser.currentToken() == JsonToken.VALUE_NULL) {
                return null;
            }
            Integer code = readCode(parser, ctxt);
            for (Object constant : enumType.getEnumConstants()) {
                if (((BaseEnum) constant).getCode().equals(code)) {
                    return (BaseEnum) constant;
                }
            }
            throw weirdCode(ctxt, code);
        }

        /** code 入参兼容数字与数字字符串，其余形式一律按非法值处理 */
        private Integer readCode(JsonParser parser, DeserializationContext ctxt) throws IOException {
            JsonToken token = parser.currentToken();
            if (token == JsonToken.VALUE_NUMBER_INT) {
                return parser.getIntValue();
            }
            try {
                return Integer.valueOf(parser.getText().trim());
            } catch (NumberFormatException e) {
                throw weirdCode(ctxt, parser.getText());
            }
        }

        private JsonMappingException weirdCode(DeserializationContext ctxt, Object value) throws JsonMappingException {
            return ctxt.weirdStringException(String.valueOf(value), enumType, "无匹配的 BaseEnum code");
        }

        @Override
        public JsonDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property) {
            JavaType type = ctxt.getContextualType();
            if (type == null) {
                return this;
            }
            Class<?> rawClass = type.getRawClass();
            if (rawClass.isEnum() && BaseEnum.class.isAssignableFrom(rawClass)) {
                return new CodeDeserializer(rawClass);
            }
            return this;
        }
    }
}
