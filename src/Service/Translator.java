package Service;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;

import Entity.AFile;
import Entity.User;
import Util.SerializationHelper;

/**
 * 翻译类 用于服务器和客户端读写信息
 */
public class Translator {
	// 负责编码和解码的字符集对象
	private static Charset coder = Charset.forName("GBK");

	public static String readProtocol(SelectionKey key) {
		SocketChannel channel = (SocketChannel) key.channel();
		try {
			key.interestOps(SelectionKey.OP_READ);
			return readProtocol(channel);
		} catch (IOException e) {
			/**
			 * 如果出现异常 可能是客户端出现问题（可能已经关闭） 所以取消该SelectionKey的注册
			 */
			System.out.println("Cancel a key when readProtocol");
			cancel(key);
		}
		return null;
	}

	public static String readProtocol(SocketChannel channel) throws IOException {
		// 一个协议字符是1个字节，所以一组协议占用2个字节
		ByteBuffer buf = ByteBuffer.allocate(2);
		String protocol = "";
		channel.read(buf);
		buf.flip();
		protocol += coder.decode(buf);
		return protocol;
	}

	public static User readUser(SelectionKey key) {
		SocketChannel channel = (SocketChannel) key.channel();
		try {
			key.interestOps(SelectionKey.OP_READ);
			return readUser(channel);
		} catch (IOException e) {
			System.out.println("Cancel a key when readUser");
			cancel(key);
		}
		return null;
	}

	public static User readUser(SocketChannel channel) throws IOException {
		/**
		 * 先读取对象序列的长度 然后为ByteBuffer分配相应长度的空间 最后读取对象序列
		 */
		int length = readInt(channel);
		ByteBuffer userBuf = ByteBuffer.allocate(length);
		channel.read(userBuf);
		userBuf.flip();
		byte[] userBytes = userBuf.array();
		User user = (User) SerializationHelper.bytesToObject(userBytes);
		return user;
	}

	public static int readInt(SelectionKey key) {
		SocketChannel channel = (SocketChannel) key.channel();
		try {
			key.interestOps(SelectionKey.OP_READ);
			return readInt(channel);
		} catch (IOException e) {
			System.out.println("Cancel a key when readInt");
			cancel(key);
		}
		return -1;
	}

	public static int readInt(SocketChannel channel) throws IOException {
		// int型占4个字节
		ByteBuffer buf = ByteBuffer.allocate(4);
		channel.read(buf);
		buf.flip();
		int result = buf.getInt();
		return result;
	}

	public static AFile readAFile(SelectionKey key) {
		SocketChannel channel = (SocketChannel) key.channel();
		try {
			key.interestOps(SelectionKey.OP_READ);
			return readAFile(channel);
		} catch (IOException e) {
			System.out.println("Cancle a key when readAFile");
		}
		return null;
	}

	public static AFile readAFile(SocketChannel channel) throws IOException {
		int length = readInt(channel);
		ByteBuffer filebuf = ByteBuffer.allocate(length);
		channel.read(filebuf);
		filebuf.flip();
		byte[] fileBytes = filebuf.array();
		AFile file = (AFile) SerializationHelper.bytesToObject(fileBytes);
		return file;
	}

	public static long readLong(SelectionKey key) {
		SocketChannel channel = (SocketChannel) key.channel();
		try {
			key.interestOps(SelectionKey.OP_READ);
			return readLong(channel);
		} catch (IOException e) {
			System.out.println("Cancel a key when readLong");
			cancel(key);
		}
		return -1;
	}

	public static long readLong(SocketChannel channel) throws IOException {
		// long型占用8字节
		ByteBuffer buf = ByteBuffer.allocate(8);
		channel.read(buf);
		buf.flip();
		long result = buf.getLong();
		return result;
	}

	/**
	 * 简单的工厂模式，把一个对象或者基本数据类型写入到通道中
	 * 
	 * @param channel
	 *            写入的通道
	 * @param o
	 *            对象或者基本数据类型
	 */
	public static void write(SocketChannel channel, Object o) {
		String typeName = o.getClass().getSimpleName();
		byte[] bytes = null;
		// switch(typeName){
		// case "Long":
		// case "Integer":
		// bytes = SerializationHelper.dataTypeToBytesFactory(o);
		// break;
		// case "User":
		// case "AFile":
		// byte[] objectBytes = SerializationHelper.objectToBytes(o);
		// byte[] lengthBytes =
		// SerializationHelper.dataTypeToBytesFactory(objectBytes.length);
		// bytes = SerializationHelper.joint(lengthBytes,objectBytes);
		// break;
		// case "byte[]":
		// bytes = (byte[]) o;
		// break;
		// case "String":
		// String message = (String) o;
		// bytes = message.getBytes(); //使用默认编码
		// break;
		// default:
		// System.out.println("未找到对象的类名");
		// return;
		// }
		if (typeName.equals("Long") || typeName.equals("Integer")) {
			bytes = SerializationHelper.dataTypeToBytesFactory(o);
		} else if (typeName.equals("User") || typeName.equals("AFile")) {
			byte[] objectBytes = SerializationHelper.objectToBytes(o);
			byte[] lengthBytes = SerializationHelper
					.dataTypeToBytesFactory(objectBytes.length);
			bytes = SerializationHelper.joint(lengthBytes, objectBytes);
		} else if (typeName.equals("byte[]")) {
			bytes = (byte[]) o;
		} else if (typeName.equals("String")) {
			String message = (String) o;
			bytes = message.getBytes(); // 使用默认编码
		} else {
			System.out.println("未找到对象的类名");
			return;
		}

		try {
			// wrap方法 将数组转化为缓冲区
			channel.write(ByteBuffer.wrap(bytes));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// 传输用户对象和文件对象时，需要将字节信息拼接后一并发送，如果分开发送会导致客户端和服务器端无法同步并抛出异常
	public static void write(SocketChannel channel, String protocol, User user) throws UnsupportedEncodingException {
		byte[] protocolBytes = protocol.getBytes();
		//待删
		System.out.println("协议字段的字节数为" + protocolBytes.length);
		byte[] userBytes = SerializationHelper.objectToBytes(user);
		byte[] lengthBytes = SerializationHelper
				.dataTypeToBytesFactory(userBytes.length);
		byte[] bytes = SerializationHelper.joint(protocolBytes, lengthBytes,
				userBytes);
		write(channel, bytes);
	}

	public static void write(SocketChannel channel, String protocol,
			int userId, AFile file) {
		byte[] protocolBytes = protocol.getBytes();
		byte[] userIdBytes = SerializationHelper.dataTypeToBytesFactory(userId);
		byte[] fileBytes = SerializationHelper.objectToBytes(file);
		byte[] lengthBytes = SerializationHelper
				.dataTypeToBytesFactory(fileBytes.length);
		byte[] bytes = SerializationHelper.joint(protocolBytes, userIdBytes,
				lengthBytes, fileBytes);
		write(channel, bytes);
	}

	private static void cancel(SelectionKey key) {
		key.cancel();
		if (key.channel() != null) {
			try {
				key.channel().close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}
