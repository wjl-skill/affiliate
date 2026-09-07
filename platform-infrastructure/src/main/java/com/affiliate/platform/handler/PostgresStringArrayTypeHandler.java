package com.affiliate.platform.handler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * PostgreSQL 字符串数组类型处理器 (PostgreSQL Text[] Array TypeHandler)
 * <p>
 * 支持 MyBatis-Plus 与 PostgreSQL 的 {@code text[]} / {@code varchar[]} 原生数组字段在 {@code List<String>} 之间的无缝转换。
 */
@MappedTypes({List.class})
@MappedJdbcTypes({JdbcType.ARRAY, JdbcType.OTHER})
public class PostgresStringArrayTypeHandler extends BaseTypeHandler<List<String>> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<String> parameter, JdbcType jdbcType) throws SQLException {
        Connection conn = ps.getConnection();
        Array array = conn.createArrayOf("text", parameter.toArray());
        ps.setArray(i, array);
    }

    @Override
    public List<String> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        Array array = rs.getArray(columnName);
        return toList(array);
    }

    @Override
    public List<String> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        Array array = rs.getArray(columnIndex);
        return toList(array);
    }

    @Override
    public List<String> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        Array array = cs.getArray(columnIndex);
        return toList(array);
    }

    private List<String> toList(Array array) throws SQLException {
        if (array == null) {
            return new ArrayList<>();
        }
        Object arrObj = array.getArray();
        if (arrObj instanceof String[] strArr) {
            return new ArrayList<>(Arrays.asList(strArr));
        } else if (arrObj instanceof Object[] objArr) {
            List<String> list = new ArrayList<>(objArr.length);
            for (Object obj : objArr) {
                list.add(obj != null ? obj.toString() : null);
            }
            return list;
        }
        return new ArrayList<>();
    }
}
