package org.chevalier.mqtt.websocket;

import java.util.Queue;

import org.chevalier.mqtt.websocket.entity.QoS;

public class MQTT {

	private String url;
	private String clientId;
	private String userName;
	private String password;
	private int keepAlive = 10;
	private int willQoS = QoS.AT_MOST_ONCE;
	private String willTopic;
	private String willMessage;
	private boolean cleanSession = true;
	private Queue<MQTTCallback> publishQueue;
	private MQTTConnection mqttConnection;

	public MQTT(String url){
		this.url = url;
		mqttConnection = new MQTTConnection(this);
	}
	
	public String getUrl() {
		return url;
	}

	public String getClientId() {
		return clientId;
	}

	public void setClientId(String clientId) {
		this.clientId = clientId;
	}

	public String getUserName() {
		return userName;
	}

	public void setUserName(String userName) {
		this.userName = userName;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}
	
	public int getKeepAlive() {
		return keepAlive;
	}

	public void setKeepAlive(int keepAlive) {
		this.keepAlive = keepAlive;
	}

	public int getWillQoS() {
		return willQoS;
	}

	public void setWillQoS(int willQoS) {
		this.willQoS = willQoS;
	}
	
	public String getWillTopic() {
		return willTopic;
	}

	public void setWillTopic(String willTopic) {
		this.willTopic = willTopic;
	}

	public String getWillMessage() {
		return willMessage;
	}

	public void setWillMessage(String willMessage) {
		this.willMessage = willMessage;
	}

	public boolean isCleanSession() {
		return cleanSession;
	}

	public void setCleanSession(boolean cleanSession) {
		this.cleanSession = cleanSession;
	}
	
	public Queue<MQTTCallback> getPublishQueue() {
		return publishQueue;
	}

	public void setPublishQueue(Queue<MQTTCallback> publishQueue) {
		this.publishQueue = publishQueue;
	}

	public MQTTConnection getMqttConnection() {
		return mqttConnection;
	}
	
}
