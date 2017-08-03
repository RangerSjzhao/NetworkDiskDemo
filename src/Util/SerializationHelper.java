package Util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * 序列化工具 用于字节流和特定对象、特定基本数据类型的相互转化
 */
public class SerializationHelper {
	public static byte[] objectToBytes(Object o) {
		byte[] bytes = null;
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ObjectOutputStream oos = new ObjectOutputStream(baos);
			oos.writeObject(o);
			bytes = baos.toByteArray();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return bytes;
	}
	
	public static Object bytesToObject(byte[] bytes){
		Object o =null;
		try{
			ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
			ObjectInputStream ois = new ObjectInputStream(bais);
			o = ois.readObject();
		}catch(Exception e){
			e.printStackTrace();
		}
		return o;
	}
	
	public static byte[] dataTypeToBytesFactory(Object o){
		byte[] bytes = null;
		String typeName = o.getClass().getSimpleName();
		try{
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			DataOutputStream dos = new DataOutputStream(baos);
//			switch(typeName){
//			case "Integer":
//				int i = o;
//				dos.writeInt(i);
//				break;
//			case "Long":
//				long l = (long) o;
//				dos.writeLong(l);
//				break;
//			}
			
			if(typeName.equals("Integer")){
				int i = (Integer) o;
				dos.writeInt(i);
			}else{
				long l = (Long) o;
				dos.writeLong(l);
			}
			
			bytes = baos.toByteArray();
		}catch(IOException e){
			e.printStackTrace();
		}
		return bytes;
	}
	
	public static byte[] joint(byte[]...byteArrays){
		int length = 0;
		for(int i=0;i<byteArrays.length;i++){
			length += byteArrays[i].length;
		}
		byte[] bytes = new byte[length];
		int lastIndex =0;
		for(int i=0;i<byteArrays.length;i++){
			System.arraycopy(byteArrays[i], 0, bytes, lastIndex, byteArrays[i].length);
		}
		return bytes;
	}
	
	@Deprecated
	public static byte[] getBytesOfInt(int i){
		byte[] bytes = null;
		try{
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			DataOutputStream dos = new DataOutputStream(baos);
			dos.writeInt(i);
			bytes = baos.toByteArray();
		}catch(IOException e){
			e.printStackTrace();
		}
		return bytes;
	}
	
	@Deprecated
	public static byte[] getBytesOfLong(long l){
		byte[] bytes = null;
		try{
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			DataOutputStream dos = new DataOutputStream(baos);
			dos.writeLong(l);
			bytes = baos.toByteArray();
		}catch(IOException e){
			e.printStackTrace();
		}
		return bytes;
	}
	
	
}



























