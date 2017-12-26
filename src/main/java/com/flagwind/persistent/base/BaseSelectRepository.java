package com.flagwind.persistent.base;

import com.flagwind.persistent.model.Paging;
import com.flagwind.persistent.model.Sorting;
import com.flagwind.mybatis.provider.base.BaseSelectProvider;
import com.flagwind.persistent.model.Clause;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.SelectProvider;

import java.io.Serializable;
import java.util.List;

public interface BaseSelectRepository<E, ID extends Serializable> {
    @SelectProvider(type = BaseSelectProvider.class, method = "dynamicSQL")
    E getById(@Param("_key") ID id);

    @SelectProvider(type = BaseSelectProvider.class, method = "dynamicSQL")
    long count(@Param("_clause") Clause clause);


    @SelectProvider(type = BaseSelectProvider.class, method = "dynamicSQL")
    List<E> find(@Param("_clause") Clause clause);

    @SelectProvider(type = BaseSelectProvider.class, method = "dynamicSQL")
    List<E> page(@Param("_clause") Clause clause, @Param("_paging") Paging page, @Param("_sorts") Sorting[] sorts);

    @SelectProvider(type = BaseSelectProvider.class, method = "dynamicSQL")
    List<E> take(@Param("_clause") Clause clause, @Param("_startIndex") int startIndex,
                 @Param("_endIndex") int endIndex);

    @SelectProvider(type = BaseSelectProvider.class, method = "dynamicSQL")
    List<E> getAll();
}
