package com.affiliate.platform.handler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.Array;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** PostgreSQL {@code integer[]} 与 {@code List<Integer>} 的字段级类型处理器。 */
public class PostgresIntegerArrayTypeHandler extends BaseTypeHandler<List<Integer>> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<Integer> parameter, JdbcType jdbcType)
            throws SQLException {
        Connection connection = ps.getConnection();
        ps.setArray(i, connection.createArrayOf("integer", parameter.toArray()));
    }

    @Override
    public List<Integer> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return toList(rs.getArray(columnName));
    }

    @Override
    public List<Integer> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return toList(rs.getArray(columnIndex));
    }

    @Override
    public List<Integer> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return toList(cs.getArray(columnIndex));
    }

    private List<Integer> toList(Array array) throws SQLException {
        if (array == null) return new ArrayList<>();
        Object value = array.getArray();
        if (!(value instanceof Object[] values)) return new ArrayList<>();
        List<Integer> result = new ArrayList<>(values.length);
        for (Object item : values) {
            if (item == null) {
                result.add(null);
            } else if (item instanceof Number number) {
                result.add(number.intValue());
            } else {
                result.add(Integer.valueOf(item.toString()));
            }
        }
        return result;
    }
}
