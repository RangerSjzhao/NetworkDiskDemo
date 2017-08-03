package Service;

/**
 * 服务端和客户端的通信标记
 * 每个标记的长度为2个字符
 * 如果一个字符能表达清楚通信的目的，另一个字符就是占位符
 */
public interface NetDiskProtocol {
	int PROTOCOL_LEN =2;
	String PLACEHOLDER = "0";
	String SPLIT_SIGN = "1";
	String SIGNUP = "2";
	String LOGIN = "3";
	String UPLOAD = "4";
	String DOWNLOAD = "5";
	String SUCCESS ="6";
	String UNKNOWN_ERROR = "7";
	String USER_EXIST = "8";
	String FILE_NOT_FOUND = "9";
	String USER_NOT_FOUND = "A";
	String WRONG_PASSWORD = "B";
	String UPLOAD_IN_A_SECOND = "C";
}
