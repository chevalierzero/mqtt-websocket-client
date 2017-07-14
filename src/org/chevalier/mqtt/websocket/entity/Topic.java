package org.chevalier.mqtt.websocket.entity;

/**
 * 
 * @author Chevalier
 * @date 2016-03-26
 *
 */
public class Topic {

	private String name;
	private int qos;
	
	public Topic(String name, int qos) {
		this.name = name;
		this.qos = qos;
	}

	public String getName() {
		return name;
	}

	public int getQos() {
		return qos;
	}
	
}
