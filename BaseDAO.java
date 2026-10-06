package dao;

import database.DBConnection;
import model.Entity;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * All the JDBC plumbing lives here once. A concrete DAO only declares its
 * table, columns and how to convert between a ResultSet and its model object.
 */
public abstract class BaseDAO<T extends Entity> {

    protected final String   table;
    protected final String   pk;
    protected final String[] columns;      // pk MUST be the first column

    protected BaseDAO(String table, String[] columns) {
        this.table   = table;
        this.columns = columns;
        this.pk      = columns[0];
    }

    /** ResultSet -> model object. */
    protected abstract T map(ResultSet rs) throws SQLException;

    /** Model object -> values, in exactly the same order as {@link #columns}. */
    protected abstract Object[] params(T t);

    // ------------------------------------------------------------------ read

    public List<T> findAll() throws SQLException {
        return where("", new Object[0]);
    }

    /** Case-insensitive "contains" search across every column. */
    public List<T> search(String keyword) throws SQLException {
        if (keyword == null || keyword.trim().isEmpty()) return findAll();
        StringBuilder sb = new StringBuilder(" WHERE ");
        Object[] args = new Object[columns.length];
        for (int i = 0; i < columns.length; i++) {
            if (i > 0) sb.append(" OR ");
            sb.append("LOWER(IFNULL(").append(columns[i]).append(",'')) LIKE ?");
            args[i] = "%" + keyword.trim().toLowerCase() + "%";
        }
        return where(sb.toString(), args);
    }

    /** Free-form WHERE clause, still using a PreparedStatement. */
    public List<T> where(String clause, Object... args) throws SQLException {
        String sql = "SELECT " + join(columns) + " FROM " + table + clause + " ORDER BY " + pk;
        List<T> out = new ArrayList<T>();
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = DBConnection.get().prepareStatement(sql);
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            rs = ps.executeQuery();
            while (rs.next()) out.add(map(rs));
        } finally {
            close(rs, ps);
        }
        return out;
    }

    public T findById(String id) throws SQLException {
        List<T> list = where(" WHERE " + pk + " = ?", id);
        return list.isEmpty() ? null : list.get(0);
    }

    public boolean exists(String id) throws SQLException {
        return findById(id) != null;
    }

    public int count() {
        return (int) DBConnection.scalar("SELECT COUNT(*) FROM " + table);
    }

    // ----------------------------------------------------------------- write

    public void insert(T t) throws SQLException {
        StringBuilder q = new StringBuilder();
        for (int i = 0; i < columns.length; i++) q.append(i == 0 ? "?" : ",?");
        String sql = "INSERT INTO " + table + " (" + join(columns) + ") VALUES (" + q + ")";
        PreparedStatement ps = null;
        try {
            ps = DBConnection.get().prepareStatement(sql);
            bind(ps, params(t), 1);
            ps.executeUpdate();
        } finally {
            close(null, ps);
        }
    }

    public void update(T t) throws SQLException {
        StringBuilder set = new StringBuilder();
        for (int i = 1; i < columns.length; i++) {
            if (i > 1) set.append(", ");
            set.append(columns[i]).append(" = ?");
        }
        String sql = "UPDATE " + table + " SET " + set + " WHERE " + pk + " = ?";
        Object[] p = params(t);
        PreparedStatement ps = null;
        try {
            ps = DBConnection.get().prepareStatement(sql);
            int idx = 1;
            for (int i = 1; i < p.length; i++) ps.setObject(idx++, p[i]);
            ps.setObject(idx, p[0]);                  // the pk goes last, into WHERE
            ps.executeUpdate();
        } finally {
            close(null, ps);
        }
    }

    public void delete(String id) throws SQLException {
        PreparedStatement ps = null;
        try {
            ps = DBConnection.get().prepareStatement("DELETE FROM " + table + " WHERE " + pk + " = ?");
            ps.setString(1, id);
            ps.executeUpdate();
        } finally {
            close(null, ps);
        }
    }

    /** Suggests the next id such as P005 / D012 based on rows already stored. */
    public String nextId(String prefix, int digits) {
        int n = (int) DBConnection.scalar("SELECT COUNT(*) FROM " + table) + 1;
        for (int guard = 0; guard < 500; guard++) {
            String candidate = prefix + String.format("%0" + digits + "d", n);
            if (DBConnection.scalar("SELECT COUNT(*) FROM " + table + " WHERE " + pk + " = '" + candidate + "'") == 0)
                return candidate;
            n++;
        }
        return prefix + System.currentTimeMillis() % 10000;
    }

    // ---------------------------------------------------------------- helper

    protected static String join(String[] a) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < a.length; i++) sb.append(i == 0 ? "" : ", ").append(a[i]);
        return sb.toString();
    }

    protected static void bind(PreparedStatement ps, Object[] values, int from) throws SQLException {
        for (int i = 0; i < values.length; i++) ps.setObject(from + i, values[i]);
    }

    protected static void close(ResultSet rs, Statement st) {
        try { if (rs != null) rs.close(); } catch (SQLException ignored) { }
        try { if (st != null) st.close(); } catch (SQLException ignored) { }
    }
}
