package Util;

import java.io.FileInputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * 数据库工具类
 * 包含两个静态方法分别用于初始化和关闭数据库
 */
public class DBUtil {
	private static String driver;
	private static String url;
	private static String user;
	private static String password;
	
	public static Connection conn;
	/*
	 * 加载数据库驱动并创建数据库链接
	 */
	public static void init() throws Exception{
		Properties prop = new Properties();
		//加载配置文件,配置文件包含了创建数据库所需信息
		prop.load(new FileInputStream("mysql.ini"));
		driver = prop.getProperty("driver");
		url = prop.getProperty("url");
		user = prop.getProperty("user");
		password = prop.getProperty("password");
		Class.forName(driver);
		conn = DriverManager.getConnection(url, user, password);
	}
	
	/*
	 * 关闭数据库链接
	 */
	public static void close(){
		try{
			conn.close();
		}catch(SQLException e){
			e.printStackTrace();
		}
	}
}
