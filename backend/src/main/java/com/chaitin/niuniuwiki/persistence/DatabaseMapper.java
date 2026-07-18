package com.chaitin.niuniuwiki.persistence;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.UpdateProvider;

/**
 * 承载 NiuniuWiki 动态参数化 SQL 的 MyBatis 映射。
 *
 * @author 程序员牛肉
 * @since 2026-07-09
 */
@Mapper
public interface DatabaseMapper {

    @SelectProvider(type = ParameterizedSqlProvider.class, method = "provide")
    List<Map<String, Object>> select(
            @Param("statement") String statement,
            @Param("arguments") List<Object> arguments
    );

    @UpdateProvider(type = ParameterizedSqlProvider.class, method = "provide")
    int mutate(
            @Param("statement") String statement,
            @Param("arguments") List<Object> arguments
    );
}
