package com.flagwind.mybatis.paginator.dialects.base;



import com.flagwind.mybatis.paginator.dialects.Dialect;
import org.apache.ibatis.mapping.MappedStatement;

import com.flagwind.mybatis.paginator.PageBounds;

public class H2Dialect extends Dialect {

    public H2Dialect(MappedStatement mappedStatement, Object parameterObject, PageBounds pageBounds) {
        super(mappedStatement, parameterObject, pageBounds);
    }

    protected String getLimitString(String sql, String offsetName,int offset, String limitName, int limit) {
		return new StringBuffer(sql.length() + 40).
			append(sql).
			append((offset > 0) ? " limit "+String.valueOf(limit)+" offset "+String.valueOf(offset) : " limit "+String.valueOf(limit)).
			toString();
	}


}