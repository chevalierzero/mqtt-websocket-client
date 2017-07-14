package org.chevalier.mqtt.websocket.entity;

/**
 * 
 * @author 张诚
 * @date 2016年3月26日
 *
 */
public class ErrorMessage {

	public int state;
	public String message;
	
	public ErrorMessage(int state, String message) {
		this.state = state;
		this.message = message;
	}
	
	public int getState() {
		return state;
	}
	
	public String getMessage() {
		return message;
	}
	
}
