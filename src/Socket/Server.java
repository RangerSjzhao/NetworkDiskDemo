package Socket;

import static Service.NetDiskProtocol.DOWNLOAD;
import static Service.NetDiskProtocol.FILE_NOT_FOUND;
import static Service.NetDiskProtocol.LOGIN;
import static Service.NetDiskProtocol.PLACEHOLDER;
import static Service.NetDiskProtocol.SIGNUP;
import static Service.NetDiskProtocol.SUCCESS;
import static Service.NetDiskProtocol.UNKNOWN_ERROR;
import static Service.NetDiskProtocol.UPLOAD;
import static Service.NetDiskProtocol.UPLOAD_IN_A_SECOND;
import static Service.NetDiskProtocol.USER_EXIST;
import static Service.NetDiskProtocol.USER_NOT_FOUND;
import static Service.NetDiskProtocol.WRONG_PASSWORD;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import Entity.AFile;
import Entity.User;

import static Util.DBUtil.*;
import static Service.NetDiskProtocol.*;

import Service.Translator;
import Util.DBUtil;
import Util.FileUtil;
//做了一些改动

public class Server {
	// Selector（选择器）是JavaNIO中能够检测一到多个NIO通道，并能够知晓通道是否为诸如读写事件做好准备的组。
	// 这样，一个单独的线程可以管理多个channel，从而管理多个网络连接。
	private Selector selector = null;
	private ServerSocketChannel acceptor = null;

	// 传输信息的端口6666，传输文件的端口6667
	private static final int PORT1 = 6666;
	private static final int PORT2 = 6667;

	private static final String HOST = "127.0.0.1";

	// 拼接传输主要信息和文件的Socket地址
	private InetSocketAddress infoAddress = new InetSocketAddress(HOST, PORT1);
	private InetSocketAddress fileAddress = new InetSocketAddress(HOST, PORT2);
	// 服务器存储文件的路径
	private static String fileLocation = "/Users/sunjiazhao/Downloads/ServerFileRepository";

	/**
	 * 打开一个选择器和一个接收通道 为接收通道设置通信地址，并将接收通道登记到选择器上
	 * 
	 * @throws IOException
	 */
	public Server() throws IOException {
		selector = Selector.open();
		acceptor = ServerSocketChannel.open();
		// acceptor = SelectorProvider.provider();
		ServerSocket socket = acceptor.socket();
		socket.bind(infoAddress);
		acceptor.configureBlocking(false);
		acceptor.register(selector, SelectionKey.OP_ACCEPT);

		FileUtil.mkdir(fileLocation);
		fileLocation += "/";
		System.out.println("服务器已启动");
	}

	public void work() throws Exception {
		new AcceptFileChannelThread().start();
		/*
		 * 接收注册和登陆
		 */
		while (selector.select() > 0) {
			for (SelectionKey key : selector.selectedKeys()) {
				// 将正在处理的SelectionKey从待处理的集合中除去
				selector.selectedKeys().remove(key);
				if (key.isAcceptable()) accept(key);
				if (key.isReadable()) {
					SocketChannel mainChannel = (SocketChannel) key.channel();
					String protocol = Translator.readProtocol(key);
					if (!key.isValid()) {
						// 如果key失效，跳过本次循环
						continue;
					}
					//待删
					System.out.println("协议字段为：" + protocol);
					
					// 若没有读到协议字符则说明出现了未知错误
					if (protocol == null) {
						Translator.write(mainChannel, UNKNOWN_ERROR
								+ PLACEHOLDER);
						continue;
					}
					// 客户端发送注册请求
					if (protocol.equals(SIGNUP + PLACEHOLDER)) {
						User user = Translator.readUser(key);
						String username = user.getUsername();
						User temp = getUser(username);
						//待删
						System.out.println("注册用户名：" + username);
						if (temp == null) {
							String password = user.getPassword();
							//待删
							System.out.println("注册用户密码：" + password);
							
							if (addUser(username, password)) {
								Translator.write(mainChannel, SIGNUP
										+ SUCCESS);
							} else {
								Translator.write(mainChannel, UNKNOWN_ERROR
										+ PLACEHOLDER);
							}
						} else {
							Translator.write(mainChannel, USER_EXIST
									+ PLACEHOLDER);
						}
					} else if (protocol.equals(LOGIN + PLACEHOLDER)) {
						User user = Translator.readUser(key);
						String username = user.getUsername();
						User temp = getUser(username);
						if (temp != null) {
							String password = user.getPassword();
							if (checkPassword(username, password)) {// 用户名和密码匹配
								Translator.write(mainChannel, LOGIN + SUCCESS);
							} else {
								Translator.write(mainChannel, WRONG_PASSWORD
										+ PLACEHOLDER);
							}
						} else {
							Translator.write(mainChannel, USER_NOT_FOUND
									+ PLACEHOLDER);
						}
					}else{
						//待删
						System.out.println("没有进入注册和登陆部分");
					}
				}
			}
		}
	}

	/*
	 * 打开传输文件的线程 每次都会启动一个LoadThread传输文件
	 */
	private class AcceptFileChannelThread extends Thread {
		private Selector selector = null;
		private ServerSocketChannel acceptor = null;

		public AcceptFileChannelThread() throws IOException {
			selector = Selector.open();
			acceptor = ServerSocketChannel.open();
			ServerSocket socket = acceptor.socket();
			socket.bind(fileAddress);
			acceptor.configureBlocking(false);
			acceptor.register(selector, SelectionKey.OP_ACCEPT);

		}

		public void run() {
			try {
				while (selector.select() > 0) {
					for (SelectionKey key : selector.selectedKeys()) {
						selector.selectedKeys().remove(key);
						if (key.isAcceptable()) {
							SocketChannel newChannel = acceptor.accept();
							newChannel.configureBlocking(true);
							key.interestOps(SelectionKey.OP_ACCEPT);
							new LoadThread(newChannel).start();// 打开一个可以upload和download的线程
						}
					}
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	private class LoadThread extends Thread {
		private SocketChannel loadChannel = null;

		public LoadThread(SocketChannel loadChannel) {
			this.loadChannel = loadChannel;
		}

		public void run() {
			String protocol = "";
			try {
				protocol = Translator.readProtocol(loadChannel);
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			
//			switch (protocol) {
//			case UPLOAD + PLACEHOLDER:
//				receive();
//				break;
//			case DOWNLOAD + PLACEHOLDER:
//				deliver();
//				break;
//			default:
//				System.err.println("Unable to read the protocol");
//			}
			
			if(protocol.equals(UPLOAD + PLACEHOLDER)){
				try {
					receive();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}else if(protocol.equals(DOWNLOAD + PLACEHOLDER)){
				try {
					deliver();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}else{
				System.err.println("Unable to read the protocol");
			}
		}

		// 接收客户端传送的文件
		public void receive() throws IOException, InterruptedException {
			int uid = Translator.readInt(loadChannel);
			// file只有id和format域是有效的
			AFile file = Translator.readAFile(loadChannel);
			/*
			 * 在数据库中查找file的信息 如果存在，根据file的id和format得到file的完整版信息
			 */
			AFile temp = getAFile(file.getName(), file.getFormat());
			// 如果服务器不存在该文件
			if (temp == null) {
				// 如果服务器中没有相应的文件，则向客户端发送文件未找到的信息，客户端会向服务器传送文件
				Translator.write(loadChannel, FILE_NOT_FOUND + PLACEHOLDER);
				/*
				 * 创建一个空文件，并用一个文件通道打开该文件
				 */
				String path = fileLocation + file;
				FileUtil.createNewFile(path);
				@SuppressWarnings("resource")
				FileChannel outChannel = new FileOutputStream(path)
						.getChannel();
				/*
				 * 读取通道中的字节缓存，然后将其写入到新创建的文件的通道中 把每秒写入的字节数量写入通道
				 */
				ByteBuffer buf = ByteBuffer.allocate(1024);
				long hasReadOnce = 0L;
				long readPerSecond = 0L;
				long start = System.currentTimeMillis();
				while ((hasReadOnce = loadChannel.read(buf)) != -1) {
					buf.flip();
					outChannel.write(buf);
					buf.clear();

					readPerSecond += hasReadOnce;
					long end = System.currentTimeMillis();
					// 记录时间，每隔一秒就将读到的字节数写入到loadChannel中
					if (end - start > 1000) {
						start = end;
						Translator.write(loadChannel, readPerSecond);
						readPerSecond = 0L;
					}
				}
				if (readPerSecond != 0L) {
					Translator.write(loadChannel, readPerSecond);
				}
				outChannel.close();
				/*
				 * 将新创建的文件的信息在数据库中更新
				 */
				int fid = addAFile(file.getName(), file.getFormat(), path);
				if (addRelation(uid, fid)) {
					// 将更新后的用户信息反馈给用户
					Translator.write(loadChannel, UPLOAD + SUCCESS);
					User user = getUser(uid);
					Translator.write(loadChannel, user);
				} else {
					Translator.write(loadChannel, UNKNOWN_ERROR);
				}
			} else {
				/*
				 * 如果数据库中存在该文件，则秒传
				 */
				file = temp;
				temp = null;
				if (addRelation(uid, file.getId())) {
					/*
					 * 将更新后的用户信息反馈给用户
					 */
					Translator.write(loadChannel, UPLOAD_IN_A_SECOND
							+ PLACEHOLDER);
					User user = getUser(uid);
					Translator.write(loadChannel, user);
				} else {
					Translator.write(loadChannel, UNKNOWN_ERROR);
				}
			}
			Socket socket = null;
			if (loadChannel.isConnected()) {
				socket = loadChannel.socket();
			}
			System.out.println("来自客户端" + socket.getRemoteSocketAddress()
					+ "的文件传送完成");
		}

		// 将文件传送给客户端
		private void deliver() throws IOException {
			AFile file = Translator.readAFile(loadChannel);
			// 从数据库中获得完整信息
			file = getAFile(file.getName(), file.getFormat());
			@SuppressWarnings("resource")
			FileChannel inChannel =new FileInputStream(file.getPath())
					.getChannel();
			Translator.write(loadChannel, inChannel.size());
			MappedByteBuffer buf = inChannel.map(MapMode.READ_ONLY, 0,
					inChannel.size());
			while (buf.hasRemaining()) {
				loadChannel.write(buf);
			}
			inChannel.close();
		}

	}

	// 接收新的SocketChannel并将其注册到selector上
	private void accept(SelectionKey sk) throws Exception {
		SocketChannel sc = acceptor.accept();
		sc.configureBlocking(false);
		sc.register(selector, SelectionKey.OP_READ);
		// 准备下次接收
		sk.interestOps(SelectionKey.OP_ACCEPT);
		Socket socket = sc.socket();
		System.out.println("链接到一个新的客户端" + socket.getRemoteSocketAddress());
	}

	// 根据用户名在数据库中查询用户，存在返回User对象，不存在返回null
	private User getUser(String username) {
		String sql = "SELECT * FROM user WHERE name ='" + username + "'";
		try {
			PreparedStatement pstmt = conn.prepareStatement(sql);
			ResultSet userRS = pstmt.executeQuery();
			if (userRS.next()) {
				int id = userRS.getInt("id");
				String password = userRS.getString("password");
				User user = new User(id, username, password);

				// 获取该用户所有文件
				String getFileSQL = "SELECT name,format FROM file INNER JOIN relation on fid = id AND uid = "
						+ id;
				pstmt = conn.prepareStatement(getFileSQL);
				ResultSet fileRS = pstmt.executeQuery();
				while (fileRS.next()) {
					String filename = fileRS.getString("name");
					String format = fileRS.getString("format");
					AFile file = new AFile(filename, format);
					user.addFile(file);
				}
				return user;
			} else {
				return null;
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return null;
	}

	private User getUser(int id) {
		String sql = "SELECT * FROM user WHERE id =" + id;
		try {
			PreparedStatement pstmt = conn.prepareStatement(sql);
			ResultSet userRS = pstmt.executeQuery();
			if (userRS.next()) {// 存在该id所对应的用户信息
				String username = userRS.getString("name");
				String password = userRS.getString("password");
				User user = new User(id, username, password);

				// 获取用户的所有文件
				String getFileSQL = "SELECT name,format FROM file INNER JOIN relation ON fid = id AND uid"
						+ id;
				pstmt = conn.prepareStatement(getFileSQL);
				ResultSet fileRS = pstmt.executeQuery();
				while (fileRS.next()) {
					String filename = fileRS.getString("name");
					String format = fileRS.getString("format");
					AFile file = new AFile(filename, format);
					user.addFile(file);
				}
			} else {
				return null;
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return null;
	}

	// 在数据库中检查用户名和密码是否匹配，若匹配则返回true，否则返回false
	private boolean checkPassword(String username, String password) {
		String sql = "SELECT * FROM user WHERE name = " + username
				+ "AND password =" + password;
		try {
			PreparedStatement pstmt = conn.prepareStatement(sql);
			ResultSet rs = pstmt.executeQuery();
			if (rs.next()) {
				return true;
			} else {
				return false;
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return false;
	}

	private boolean addUser(String name, String password) {
		String sql = "INSERT INTO user VALUES(null,?,?)";
		try {
			PreparedStatement pstmt = conn.prepareStatement(sql);
			pstmt.setString(1, name);
			pstmt.setString(2, password);
			if (pstmt.executeUpdate() == 1) {
				return true;
			} else {
				return false;
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return false;
	}

	// 根据文件名和文件格式得到文件对象并返回
	private AFile getAFile(String name, String format) {
		String sql = "SELECT * FROM file WHERE name = '" + name
				+ "' AND format ='" + format + "';";
		try {
			PreparedStatement pstmt = conn.prepareStatement(sql);
			ResultSet rs = pstmt.executeQuery();
			if (rs.next()) {
				int id = rs.getInt(1);
				String path = rs.getString(4);
				return new AFile(id, name, format, path);
			} else {
				return null;
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return null;
	}

	// 在数据库中添加用户和文件的对应关系
	private boolean addRelation(int uid, int fid) {
		String sql = "INSERT IGNORE INTO relation VALUES(?,?)";
		try {
			PreparedStatement pstmt = conn.prepareStatement(sql);
			pstmt.setInt(1, uid);
			pstmt.setInt(2, fid);
			if (pstmt.executeUpdate() == 1) {
				return true;
			} else {
				return false;
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return false;
	}

	//在数据库中添加新的文件，成功返回新文件的id，否则返回-1
	private	int addAFile(String name, String format, String path){
		String sql = "INSERT INTO file VALUES(NULL,?,?,?)";
		try{
			PreparedStatement pstmt = conn.prepareStatement(sql);
			pstmt.setString(1, name);
			pstmt.setString(2, format);
			pstmt.setString(3, path);
			if(pstmt.executeUpdate() == 1){
				sql = "SELECT LAST_INSERT_ID();";
				pstmt = conn.prepareStatement(sql);
				ResultSet rs = pstmt.executeQuery();
				if(rs.next()){
					int lastInsertID = rs.getInt(1);
					return lastInsertID;
				}
				
			}else{
				return -1;
			}
		}catch(SQLException e){
			e.printStackTrace();
		}
		return -1;
	}
	/**
	 * @param args
	 * @throws Exception 
	 */
	public static void main(String[] args) throws Exception {
		// TODO Auto-generated method stub
		Server server = new Server();
		DBUtil.init();
		server.work();
		DBUtil.close();
	}

}
