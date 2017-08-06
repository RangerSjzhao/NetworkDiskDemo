package Socket;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Scanner;

import Entity.AFile;
import Entity.User;

import static Service.NetDiskProtocol.*;
import Service.Translator;
import Util.FileUtil;

public class Client {
	private Selector selector = null;
	private SocketChannel mainChannel = null;

	private static final int PORT1 = 6666;
	private static final int PORT2 = 6667;
	private static final String HOST = "127.0.0.1";

	private InetSocketAddress communicationAddress = new InetSocketAddress(
			HOST, PORT1);
	private InetSocketAddress fileAddress = new InetSocketAddress(HOST, PORT2);

	private int command;
	private Scanner console = new Scanner(System.in);

	private boolean isLogin = false;

	// 当前客户端在线用户
	private User currentUser = null;

	// 客户端文件存储地址
	private static String fileLocation = "/Users/sunjiazhao/Downloads/client_repository";

	// 打开选择器和主通道，进行相应的设置并将通道绑定到选择器上
	public Client() throws IOException {
		selector = Selector.open();
		mainChannel = SocketChannel.open(communicationAddress);
		mainChannel.configureBlocking(false);
		mainChannel.register(selector, SelectionKey.OP_READ);

		FileUtil.mkdir(fileLocation);
		fileLocation += "/";
	}

	private void readCommand() {
		while (true) {
			try {
				command = console.nextInt();
				console.nextLine();
				return;
			} catch (Exception e) {
				System.out.println("请输入数字！");
				console.next();
			}
		}
	}

	// 第一层操作，包括注册，登陆，退出
	private void FirstLayer() throws Exception {
		new ClientThread().start();
		while (true) {
			FirstLayerMenu();
			readCommand();
			switch (command) {
			case 1:
				login();
				break;
			case 2:
				signUp();
				break;
			case 3:
				System.out.println("再见!");
				return;
			}
		}
	}

	private void signUp() throws InterruptedException, UnsupportedEncodingException {
		System.out.println("请输出用户名：");
		String username = console.next();
		System.out.println("请输入密码：");
		String password = console.next();
		User newUser = new User(username, password);
		String protocol = SIGNUP + PLACEHOLDER;
		Translator.write(mainChannel, protocol, newUser);
		Thread.sleep(1000);
	}

	private void login() throws InterruptedException, UnsupportedEncodingException {
		System.out.println("请输入用户名：");
		String username = console.next();
		System.out.println("请输入密码：");
		String password = console.next();
		User tempUser = new User(username, password);
		String protocol = LOGIN + PLACEHOLDER;
		Translator.write(mainChannel, protocol, tempUser);
		Thread.sleep(1000);
		if (isLogin) {
			SecondLayer();
		} else {
			currentUser = null;
		}
	}

	// 第二层操作，包括上传、下载和登出操作，还可以看到当前用户的文件
	private void SecondLayer() {
		while (true) {
			SecondLayerMenu();
			readCommand();
			switch (command) {
			case 1:
				try {
					upload();
				} catch (Exception e) {
					System.out.println("上传时抛出异常");
					//e.printStackTrace();
				}
				break;
			case 2:
				try {
					download();
				} catch (Exception e) {
					System.out.println("下载时抛出异常");
					//e.printStackTrace();
				}
				break;
			case 3:
				isLogin = false;
				currentUser = null;
				return;
			}
		}
	}

	private void upload() throws Exception {
		/*
		 * 不断输入文件路径，直到得到可用路径为止
		 */
		AFile file;
		String path;
		while (true) {
			try {
				path = console.nextLine();
				file = new AFile(path);
				break;
			} catch (FileNotFoundException e) {
				System.out.println("找不到该文件，请重新输入");
			}
		}
		//打开一个新的上传通道并设为阻塞
		SocketChannel uploadChannel = SocketChannel.open(fileAddress);
		uploadChannel.configureBlocking(true);
		
		//把上传协议，用户id和文件对象（不是文件实体）通过上传通道传输给服务器
		String protocol = UPLOAD + PLACEHOLDER;
		int id = currentUser.getId();
		Translator.write(uploadChannel,protocol,id,file);
		/*
		 * 读取服务器的响应
		 * 若服务器不存在要上传的文件，则上传
		 * 若存在则秒传
		 * 否则出现未知错误
		 */
		protocol = Translator.readProtocol(uploadChannel);
		if(protocol == null) throw new Exception("未读取到协议");
		if(protocol.equals(FILE_NOT_FOUND + PLACEHOLDER)){
			@SuppressWarnings("resource")
			FileChannel inChannel = new FileInputStream(path).getChannel();
			MappedByteBuffer buf = inChannel.map(MapMode.READ_ONLY, 0, inChannel.size());
			
			//启动显示进度的线程
			GetCompleteRateThread rate = new GetCompleteRateThread(uploadChannel,inChannel.size());
			rate.start();
		    
			while(buf.hasRemaining()){
				uploadChannel.write(buf);
			}
			//关闭输出，表明文件输入完毕
			uploadChannel.shutdownOutput();
			
			synchronized(uploadChannel){
				if(!rate.complete){
					uploadChannel.wait();
				}
			}
			protocol = Translator.readProtocol(uploadChannel);
			//读取服务器响应，检查是否上传成功
			if(protocol.equals(UPLOAD + SUCCESS)){
				System.out.println("上传成功！");
			}else if(protocol.equals(UNKNOWN_ERROR)){
				throw new Exception("未知错误!");
			}
		}else if(protocol.equals(UPLOAD_IN_A_SECOND + PLACEHOLDER)){
			System.out.println("秒传!");
		}else throw new Exception("未知错误");
		currentUser = Translator.readUser(uploadChannel);
		uploadChannel.close();
		Thread.sleep(3000);
		
	}

	private void download() throws IOException,InterruptedException{
		/*
		 * 生成要下载文件对象
		 * 如果文件不存在于用户的文件列表中则抛出异常
		 */
		AFile file = null;
		while(true){
			try{
				do{
					System.out.println("请输入您要下载的文件名：");
					String temp = console.next();
					String[] fullName = AFile.split(temp);
					file = new AFile(fullName[0],fullName[1]);
				}while(!currentUser.hasFile(file));
				break;
			}catch(FileNotFoundException e){
				System.out.println(e.getMessage());
			}
		}
		//客户端保存下载文件的路径
		String path = fileLocation + file;
		
		/*
		 * 在文件仓库下创建同名空文件
		 * 并获取该文件的文件通道
		 */
		FileUtil.createNewFile(path);
		//@SuppressWarnings("resource")
		FileChannel outChannel = new FileOutputStream(path).getChannel();
		
		/*
		 * 打开一个SocketChannel
		 * 设置为阻塞状态
		 * 传入协议和文件对象
		 */
		SocketChannel downloadChannel = SocketChannel.open(fileAddress);
		downloadChannel.configureBlocking(true);
		String protocol = DOWNLOAD + PLACEHOLDER;
		Translator.write(downloadChannel,protocol);
		Translator.write(downloadChannel,file);
		//获取下载文件大小
		Long size = Translator.readLong(downloadChannel);
		ByteBuffer buf = ByteBuffer.allocate(1024);
		//每次从buf中下载的字节数
		int hasRead =0;
		//每秒从buf中下载的字节数
		Long readPerSecond = 0L;
		//当前已经下载的字节数
		Long currentSize =0L;
		
		Long start = System.currentTimeMillis();
		while((hasRead = downloadChannel.read(buf)) != -1 ){
			buf.flip();
			outChannel.write(buf);
			buf.clear();
			readPerSecond += hasRead;
			Long end = System.currentTimeMillis();
			if((end - start)>1000){
				start = end;
				currentSize += readPerSecond;
				readPerSecond = 0L;
				double rate = (double) currentSize / size;
				System.out.println("已完成" + String.format("%.1f", rate * 100)+"%");
			}
		}//如果readPerSecond中还存在遗留字节
		if(readPerSecond != 0L){
			currentSize += readPerSecond;
			double rate = (double) currentSize / size;
			System.out.println("已完成" + String.format("%.1f", rate * 100) + "%");
		}
		System.out.println("下载完成！");
		Thread.sleep(3000);
	}
	
	/*
	 * 上传时获得上传进度的线程
	 */
	private class GetCompleteRateThread extends Thread{
		private SocketChannel loadChannel = null;
		private Long fileSize =0L;
		private Long currentSize = 0L;
		public boolean complete = false;
		
		public GetCompleteRateThread(SocketChannel loadChannel, Long fileSize) {
			this.loadChannel = loadChannel;
			this.fileSize = fileSize;
		}
		
		public void run(){
			while(getCompleteRate(currentSize,fileSize)<1){
				try{
					currentSize += Translator.readLong(loadChannel);
				}catch(IOException e){
					e.printStackTrace();
				}
			}
			
			synchronized (loadChannel) {
				complete = true;
				loadChannel.notify();
			}
		}
		
		private double getCompleteRate(Long currentSize, Long fileSize){
			double rate = (double)currentSize/fileSize;
			System.out.println("已完成" + String.format("%.1f", rate * 100) + "%");
			return rate;
		}		
	}
	
	/**
	 * 客户端线程类
	 * 负责读取服务端的部分响应
	 */
	private class ClientThread extends Thread{
		public void run(){
			try{
				while(selector.select()>0){
					for(SelectionKey key: selector.selectedKeys()){
						selector.selectedKeys().remove(key);
						if(key.isReadable()){
							String content = Translator.readProtocol(key);
							if(content.equals(UNKNOWN_ERROR + PLACEHOLDER)){
								System.out.println("未知错误");
							}else if(content.equals(SIGNUP + SUCCESS)){
								System.out.println("注册成功");
							}else if(content.equals(USER_EXIST + PLACEHOLDER)){
								System.out.println("用户名已经存在");
							}else if(content.equals(LOGIN + SUCCESS)){
								System.out.println("登陆成功");
								/*
								 * 登陆成功后，服务器会将该用户的用户信息从数据库中提取出来
								 * 并包装成用户对象发送给客户端
								 */
								isLogin = true;
								//将当前在线用户设为此对象
								currentUser = Translator.readUser(key);
							}else if(content.equals(USER_NOT_FOUND + PLACEHOLDER )){
								System.out.println("找不到用户");
								isLogin = false;
							}else if(content.equals(WRONG_PASSWORD + PLACEHOLDER)){
								System.out.println("密码错误");
								isLogin = false;
							}
						}
					}
				}
			}catch(IOException e){
				e.printStackTrace();
			}
		}
	}
	
	
	
	/**
	 * @param args
	 * @throws Exception 
	 */
	public static void main(String[] args) throws Exception {
		// TODO Auto-generated method stub
		Client client = new Client();
		client.FirstLayer();
	}
	
	private void FirstLayerMenu(){
		System.out.println("1.登陆");
		System.out.println("2.注册");
		System.out.println("3.退出");
	}
	
	private void SecondLayerMenu(){
		System.out.println("欢迎您："+currentUser.getUsername());
		System.out.println("1.上传");
		System.out.println("2.下载");
		System.out.println("3.登出");
		System.out.println("----------文件列表-----------");
		currentUser.listFiles();		
	}

}
