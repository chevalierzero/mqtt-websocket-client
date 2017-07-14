package org.chevalier.mqtt.websocket.util;

import java.io.UnsupportedEncodingException;

/**
 * 
 * @author 张诚
 * @date 2016年3月26日
 *
 */
public class UTF8Byte {

	public static byte[] getByte(String message) throws UnsupportedEncodingException{
		
		byte[] data = new byte[message.length() + 2];
		int index = 0;
		
		data[index++] = (byte) (message.length() / 256);
		data[index++] = (byte) (message.length() % 256);
		byte[] msg = message.getBytes("UTF-8");
		
		for(int i = 0; i < msg.length; i++){
			data[index++] = msg[i];
		}
		
		return data;
		
	}
	
	public static byte[] mergeArray(byte fixHeader, byte[][] datas){
		
		int capacity = 0;
		byte[] data = null;
		int index = 0;
		
		for(int i = 0; i < datas.length; i++){
			
			capacity += datas[i].length;
		}
		
		data = new byte[capacity > 127 ? capacity + 3 : capacity + 2];
		data[index++] = fixHeader;
		
		if(capacity > 127){ //剩余长度
			data[index++] = (byte)(capacity % 128 + 128);
			data[index++] = (byte)(capacity / 128);//当剩余长度大于127，则进位
		}else{
			data[index++] = (byte)capacity;
		}
		
		for(int i = 0, size = datas.length; i < size; i++){
			
			for(int j = 0; j < datas[i].length; j++){
				data[index++] = datas[i][j];
			}
			
		}
		
		return data;
	}
	
	public static byte[] merge(byte fixHeader, byte[]... datas){
		
		return mergeArray(fixHeader, datas.clone());
		
	}
	
}
