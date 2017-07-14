package org.chevalier.mqtt.websocket;

public interface MQTTListener {

	public void onConnected();
	
	public void onDisconnected();
	
	public void onClose(int arg0, String arg1, boolean arg2);
	
	public void onPublish(String topic, String message);
	
	public void onFailure(Throwable arg0);
	
}
