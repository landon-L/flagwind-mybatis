package com.flagwind.mybatis.provider.base;

import com.flagwind.mybatis.common.MapperResolver;
import com.flagwind.mybatis.meta.EntityColumn;
import com.flagwind.mybatis.exceptions.MapperException;
import com.flagwind.mybatis.helpers.EntityHelper;
import com.flagwind.mybatis.provider.MapperTemplate;
import com.flagwind.mybatis.helpers.SelectKeyHelper;
import com.flagwind.mybatis.helpers.SqlHelper;
import com.flagwind.mybatis.utils.StringUtil;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.ibatis.mapping.MappedStatement;

import java.util.Set;

public class BaseInsertProvider extends MapperTemplate {

    public BaseInsertProvider(Class<?> mapperClass, MapperResolver mapperResolver) {
        super(mapperClass, mapperResolver);
    }

    private MutablePair<String, Boolean> getSequenceKeyMapping(Set<EntityColumn> columnList, Class<?> entityClass,
            MappedStatement ms) {
        Boolean hasIdentityKey = false;
        StringBuilder sql = new StringBuilder();
        for (EntityColumn column : columnList) {
            if (!column.isInsertable()) {
                continue;
            }
            if (StringUtil.isNotEmpty(column.getSequenceName())) {
            } else if (column.isIdentity()) {
                //这种情况下,如果原先的字段有值,需要先缓存起来,否则就一定会使用自动增长
                //这是一个bind节点
                sql.append(SqlHelper.getBindCache(column));
                //如果是Identity列，就需要插入selectKey
                //如果已经存在Identity列，抛出异常
                if (hasIdentityKey) {
                    //jdbc类型只需要添加一次
                    if (column.getGenerator() != null && column.getGenerator().equals("JDBC")) {
                        continue;
                    }
                    throw new MapperException(
                            ms.getId() + "对应的实体类" + entityClass.getCanonicalName() + "中包含多个MySql的自动增长列,最多只能有一个!");
                }
                //插入selectKey
                SelectKeyHelper.newSelectKeyMappedStatement(ms, column, entityClass, isBEFORE(), getIDENTITY(column));
                hasIdentityKey = true;
            } else if (column.isUuid()) {
                //uuid的情况，直接插入bind节点
                sql.append(SqlHelper.getBindValue(column, getUUID()));
            }
        }
        return MutablePair.of(sql.toString(), hasIdentityKey);
    }

    /**
     * 插入全部,这段代码比较复杂，这里举个例子
     * CountryU生成的insert方法结构如下：
     * <pre>
            &lt;bind name="countryname_bind" value='@java.util.UUID@randomUUID().toString().replace("-", "")'/&gt;
            INSERT INTO country_u(id,countryname,countrycode) VALUES
            &lt;trim prefix="(" suffix=")" suffixOverrides=","&gt;
            &lt;if test="id != null"&gt;#{id,javaType=java.lang.Integer},&lt;/if&gt;
            &lt;if test="id == null"&gt;#{id,javaType=java.lang.Integer},&lt;/if&gt;
            &lt;if test="countryname != null"&gt;#{countryname,javaType=java.lang.String},&lt;/if&gt;
            &lt;if test="countryname == null"&gt;#{countryname_bind,javaType=java.lang.String},&lt;/if&gt;
            &lt;if test="countrycode != null"&gt;#{countrycode,javaType=java.lang.String},&lt;/if&gt;
            &lt;if test="countrycode == null"&gt;#{countrycode,javaType=java.lang.String},&lt;/if&gt;
            &lt;/trim&gt;
     </pre>
     *
     * @param ms
     * @return
     */
    public String insert(MappedStatement ms) {
        Class<?> entityClass = getEntityClass(ms);
        StringBuilder sql = new StringBuilder();
        //获取全部列
        Set<EntityColumn> columnList = EntityHelper.getColumns(entityClass);

        //先处理cache或bind节点
        MutablePair<String, Boolean> pair = getSequenceKeyMapping(columnList, entityClass, ms);

        //Identity列只能有一个
        // Boolean hasIdentityKey = pair.right;
        if (StringUtil.isNotEmpty(pair.left)) {
            sql.append(pair.left);
        }

        sql.append(SqlHelper.insertIntoTable(entityClass, tableName(entityClass)));
        sql.append(SqlHelper.insertColumns(entityClass, false, false, false));
        sql.append("<trim prefix=\"VALUES(\" suffix=\")\" suffixOverrides=\",\">");
        for (EntityColumn column : columnList) {
            if (!column.isInsertable()) {
                continue;
            }
            //优先使用传入的属性值,当原属性property!=null时，用原属性
            //自增的情况下,如果默认有值,就会备份到property_cache中,所以这里需要先判断备份的值是否存在
            if (column.isIdentity()) {
                sql.append(SqlHelper.getIfCacheNotNull(column, column.getColumnHolder(null, "_cache", ",")));
            } else {
                //其他情况值仍然存在原property中
                sql.append(SqlHelper.getIfNotNull(column, column.getColumnHolder(null, null, ","), isNotEmpty()));
            }
            //当属性为null时，如果存在主键策略，会自动获取值，如果不存在，则使用null
            //序列的情况
            if (StringUtil.isNotEmpty(column.getSequenceName())) {
                sql.append(SqlHelper.getIfIsNull(column, getSeqNextVal(column) + " ,", false));
            } else if (column.isIdentity()) {
                sql.append(SqlHelper.getIfCacheIsNull(column, column.getColumnHolder() + ","));
            } else if (column.isUuid()) {
                sql.append(SqlHelper.getIfIsNull(column, column.getColumnHolder(null, "_bind", ","), isNotEmpty()));
            } else {
                //当null的时候，如果不指定jdbcType，oracle可能会报异常，指定VARCHAR不影响其他
                sql.append(SqlHelper.getIfIsNull(column, column.getColumnHolder(null, null, ","), isNotEmpty()));
            }
        }
        sql.append("</trim>");
        return sql.toString();
    }

    /**
     * 插入不为null的字段,这段代码比较复杂，这里举个例子
     * CountryU生成的insertSelective方法结构如下：
     * <pre>
            &lt;bind name="countryname_bind" value='@java.util.UUID@randomUUID().toString().replace("-", "")'/&gt;
            INSERT INTO country_u
            &lt;trim prefix="(" suffix=")" suffixOverrides=","&gt;
            &lt;if test="id != null"&gt;id,&lt;/if&gt;
            countryname,
            &lt;if test="countrycode != null"&gt;countrycode,&lt;/if&gt;
            &lt;/trim&gt;
            VALUES
            &lt;trim prefix="(" suffix=")" suffixOverrides=","&gt;
            &lt;if test="id != null"&gt;#{id,javaType=java.lang.Integer},&lt;/if&gt;
            &lt;if test="countryname != null"&gt;#{countryname,javaType=java.lang.String},&lt;/if&gt;
            &lt;if test="countryname == null"&gt;#{countryname_bind,javaType=java.lang.String},&lt;/if&gt;
            &lt;if test="countrycode != null"&gt;#{countrycode,javaType=java.lang.String},&lt;/if&gt;
            &lt;/trim&gt;
     </pre>
     * 这段代码可以注意对countryname的处理
     *
     * @param ms
     * @return
     */
    public String insertSelective(MappedStatement ms) {
        Class<?> entityClass = getEntityClass(ms);
        StringBuilder sql = new StringBuilder();
        //获取全部列
        Set<EntityColumn> columnList = EntityHelper.getColumns(entityClass);
        //先处理cache或bind节点
        MutablePair<String, Boolean> pair = getSequenceKeyMapping(columnList, entityClass, ms);

        //Identity列只能有一个
        // Boolean hasIdentityKey = pair.right;
        if (StringUtil.isNotEmpty(pair.left)) {
            sql.append(pair.left);
        }

        sql.append(SqlHelper.insertIntoTable(entityClass, tableName(entityClass)));
        sql.append("<trim prefix=\"(\" suffix=\")\" suffixOverrides=\",\">");
        for (EntityColumn column : columnList) {
            if (!column.isInsertable()) {
                continue;
            }
            if (StringUtil.isNotEmpty(column.getSequenceName()) || column.isIdentity() || column.isUuid()) {
                sql.append(column.getColumn() + ",");
            } else {
                sql.append(SqlHelper.getIfNotNull(column, column.getColumn() + ",", isNotEmpty()));
            }
        }
        sql.append("</trim>");
        sql.append("<trim prefix=\"VALUES(\" suffix=\")\" suffixOverrides=\",\">");
        for (EntityColumn column : columnList) {
            if (!column.isInsertable()) {
                continue;
            }
            //优先使用传入的属性值,当原属性property!=null时，用原属性
            //自增的情况下,如果默认有值,就会备份到property_cache中,所以这里需要先判断备份的值是否存在
            if (column.isIdentity()) {
                sql.append(SqlHelper.getIfCacheNotNull(column, column.getColumnHolder(null, "_cache", ",")));
            } else {
                //其他情况值仍然存在原property中
                sql.append(SqlHelper.getIfNotNull(column, column.getColumnHolder(null, null, ","), isNotEmpty()));
            }
            //当属性为null时，如果存在主键策略，会自动获取值，如果不存在，则使用null
            //序列的情况
            if (StringUtil.isNotEmpty(column.getSequenceName())) {
                sql.append(SqlHelper.getIfIsNull(column, getSeqNextVal(column) + " ,", isNotEmpty()));
            } else if (column.isIdentity()) {
                sql.append(SqlHelper.getIfCacheIsNull(column, column.getColumnHolder() + ","));
            } else if (column.isUuid()) {
                sql.append(SqlHelper.getIfIsNull(column, column.getColumnHolder(null, "_bind", ","), isNotEmpty()));
            }
        }
        sql.append("</trim>");
        return sql.toString();
    }

    /**
     * 批量插入
     *
     * @param ms
     */
    public String insertList(MappedStatement ms) {
        if (StringUtils.equalsIgnoreCase(this.getDialect(), "mysql")) {
            return insertListFromMySql(ms);
        } else {
            return insertListFromOracle(ms);
        }
    }

    public String insertListFromOracle(MappedStatement ms) {
        final Class<?> entityClass = getEntityClass(ms);
        //开始拼sql
        StringBuilder sql = new StringBuilder();
        sql.append(SqlHelper.insertIntoTable(entityClass, tableName(entityClass)));
        sql.append(SqlHelper.insertColumns(entityClass, false, false, false));
        sql.append("  ");
        sql.append("<foreach collection=\"_list\" item=\"record\" separator=\"UNION ALL\" >");
        sql.append(" select ");
        //获取全部列
        Set<EntityColumn> columnList = EntityHelper.getColumns(entityClass);
        int i = 0;
        for (EntityColumn column : columnList) {
            if (column.isInsertable()) {
                sql.append((i != 0 ? "," : "") + column.getColumnHolder("record"));
                i++;
            }
        }
        sql.append(" from dual ");
        sql.append("</foreach>");
        return sql.toString();
    }

    public String insertListFromMySql(MappedStatement ms) {
        final Class<?> entityClass = getEntityClass(ms);
        //开始拼sql
        StringBuilder sql = new StringBuilder();
        sql.append(SqlHelper.insertIntoTable(entityClass, tableName(entityClass)));
        sql.append(SqlHelper.insertColumns(entityClass, false, false, false));
        sql.append("  ");
        sql.append(" values ");
        sql.append("<foreach collection=\"_list\" item=\"record\" separator=\",\" >");
        sql.append("<trim prefix=\"(\" suffix=\")\" suffixOverrides=\",\">");

  
        //获取全部列
        Set<EntityColumn> columnList = EntityHelper.getColumns(entityClass);
        //当某个列有主键策略时，不需要考虑他的属性是否为空，因为如果为空，一定会根据主键策略给他生成一个值
        int i = 0;
        for (EntityColumn column : columnList) {

            if (column.isInsertable()) {
                sql.append((i != 0 ? "," : "") + column.getColumnHolder("record"));
                i++;
            }
        }
        sql.append("</trim>");
        sql.append("</foreach>");
        return sql.toString();
    }

    /**
     * 插入，主键id，自增
     *
     * @param ms
     */
    public String insertUseGeneratedKeys(MappedStatement ms) {
        final Class<?> entityClass = getEntityClass(ms);
        StringBuilder sql = new StringBuilder();
        sql.append(SqlHelper.insertIntoTable(entityClass, tableName(entityClass)));
        sql.append(SqlHelper.insertColumns(entityClass, true, false, false));
        sql.append(SqlHelper.insertValuesColumns(entityClass, true, false, false));
        return sql.toString();
    }
}