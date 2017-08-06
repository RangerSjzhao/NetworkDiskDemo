package Entity;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.Serializable;

@SuppressWarnings("serial")
public class AFile implements Serializable {
	//文件在数据库中的id
	private int id;
	private String name;
	private String format;
	//文件存储路径（服务器和客户端都有各自的文件存储路径）
	private String path;
	
	//客户端上传文件时，用于获得文件名和后缀，此时id和path无效
	//输入文件路径，得到相应的文件名和后缀
	public AFile(String path) throws FileNotFoundException{
		File file = new File(path);
		if(!file.exists()){
			throw new FileNotFoundException();
		}
		String[] temp = split(file.getName());
		this.name = temp[0];
		this.format = temp[1];
		
		//id和path为无效域
		id = -1;
		path = null;
	}
	
	//供服务器端使用，该对象拥有完整的文件属性
	public AFile(int id, String name, String format, String path){
		this.id = id;
		this.path = path;
		this.name = name;
		this.format = format;
	}
	
	//用户对象下的文件对象
	public AFile(String name, String format){
		this.name = name;
		this.format = format;
	}
	
	//获取文件名和后缀
	public static String[] split(String fullName){
		//. * | _ + 都是特殊字符需要转义(\)
		String[] temp = fullName.split("\\.");
		String[] result = new String[2];
		result[0] = "";
		result[1] = "";
		if(temp.length == 2){
			result[0] = temp[0];
			result[1] = temp[1];
		}else if(temp.length >2){
			int length = temp.length;
			result[0] = temp[0];
			for(int i=1;i< length -1;i++){
				result[0] += "." + temp[i];
			}
			result[1] = temp[length - 1];
		}else{
			result[0] =temp[0];
		}
		return result;
	}

	public int getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getFormat() {
		return format;
	}

	public String getPath() {
		return path;
	}

	@Override
	public String toString() {
		if(format.equals("")){
			return name;
		}else{
			return name + "." + format;
		}
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		AFile other = (AFile) obj;
		if (format == null) {
			if (other.format != null)
				return false;
		} else if (!format.equals(other.format))
			return false;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		return true;
	}
	
	
	
}


























