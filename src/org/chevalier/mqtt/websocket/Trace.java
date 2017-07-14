package org.chevalier.mqtt.websocket;

/**
 * 
 * @author 张诚
 * @date 2016年3月29日
 *
 */
public interface Trace{
	
	public void onSuccess();
	
	public void onFailure(Throwable msg);
	
}
