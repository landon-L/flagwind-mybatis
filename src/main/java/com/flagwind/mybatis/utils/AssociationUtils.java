package com.flagwind.mybatis.utils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;

import com.flagwind.mybatis.meta.EntityField;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.mapping.ResultFlag;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;

/***
 * 关联性字段工具类
 * 
 * @author svili
 *
 */
public class AssociationUtils {
    public static Class<?> getTargetType(Field field) {
        if (field.isAnnotationPresent(OneToOne.class)) {
            OneToOne one = field.getAnnotation(OneToOne.class);
            if (!one.targetEntity().equals(void.class)) {
                return one.targetEntity();
            }
        }
        return void.class;
    }

    public static String getMappedName(Field field) {
        if (field.isAnnotationPresent(OneToOne.class)) {
            OneToOne one = field.getAnnotation(OneToOne.class);
            if (!one.mappedBy().trim().equals("")) {
                return one.mappedBy();
            }
        }
        return null;
    }

    public static boolean isAssociationField(EntityField field) {
        return field.isAnnotationPresent(OneToOne.class) ||
                field.isAnnotationPresent(OneToMany.class) ||
                field.isAnnotationPresent(ManyToOne.class) ||
                field.isAnnotationPresent(ManyToMany.class);
    }
}
