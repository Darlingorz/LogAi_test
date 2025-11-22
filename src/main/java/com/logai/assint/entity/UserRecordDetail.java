package com.logai.assint.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("user_record_detail")
public class UserRecordDetail {
    @TableId("id")
    private Long id;

    @TableField("record_id")
    private Long recordId;

    @TableField("attribute_id")
    private Long attributeId;

    @TableField("string_value")
    private String stringValue;

    @TableField("number_value")
    private Double numberValue;

    @TableField("number_unit")
    private String numberUnit;

    @TableField("date_value")
    private LocalDateTime dateValue;

    @TableField("boolean_value")
    private Boolean booleanValue;

    @TableField("json_value")
    private String jsonValue;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField("group_id")
    private String groupId;

    // 非数据库字段
    @TableField(exist = false)
    private Attribute attribute;

    public Object getValue() {
        if (attribute != null && attribute.getDataType() != null) {
            return switch (attribute.getDataType()) {
                case STRING -> stringValue;
                case NUMBER -> numberValue;
                case DATE, DATETIME -> dateValue;
                case BOOLEAN -> booleanValue;
                case JSON -> jsonValue;
                default -> stringValue;
            };
        }
        return stringValue;
    }

    public void setValue(Object value) {
        if (value == null) {
            return;
        }

        if (attribute != null && attribute.getDataType() != null) {
            switch (attribute.getDataType()) {
                case NUMBER:
                    this.numberValue = value instanceof Number ? ((Number) value).doubleValue() : Double.parseDouble(value.toString());
                    break;
                case DATE:
                case DATETIME:
                    this.dateValue = value instanceof LocalDateTime ? (LocalDateTime) value : LocalDateTime.parse(value.toString());
                    break;
                case BOOLEAN:
                    this.booleanValue = value instanceof Boolean ? (Boolean) value : Boolean.parseBoolean(value.toString());
                    break;
                case JSON:
                    this.jsonValue = value.toString();
                    break;
                case STRING:
                default:
                    this.stringValue = value.toString();
            }
        } else {
            this.stringValue = value.toString();
        }
    }
}