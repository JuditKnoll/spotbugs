package ghIssues;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class Issue3529 {
    private static final String DATABASE_URL = "jdbc:mysql://localhost:3306/mydatabase";
    private static final String DATABASE_USER = "myuser";
    private boolean passwordDecider = false;

    public Connection getTernaryConn() throws SQLException {
        Connection conn = DriverManager.getConnection(DATABASE_URL, DATABASE_USER, passwordDecider ? "password" : "");
        return conn;
    }

    public Connection getEmptyPassConn() throws SQLException {
        Connection conn = DriverManager.getConnection(DATABASE_URL, DATABASE_USER, "");
        return conn;
    }

    public Connection getConstantPassConn() throws SQLException {
        Connection conn = DriverManager.getConnection(DATABASE_URL, DATABASE_USER, "password");
        return conn;
    }

    public Connection getConstantVarConn() throws SQLException {
        String pwd = "password";
        Connection conn = DriverManager.getConnection(DATABASE_URL, DATABASE_USER, pwd);
        return conn;
    }

    public Connection getTernaryVarConn() throws SQLException {
        String pwd = passwordDecider ? "password" : "";
        Connection conn = DriverManager.getConnection(DATABASE_URL, DATABASE_USER, pwd);
        return conn;
    }
}
