package com.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.UUID;

import org.chevalier.mqtt.websocket.MQTT;
import org.chevalier.mqtt.websocket.MQTTCallback;
import org.chevalier.mqtt.websocket.MQTTListener;
import org.chevalier.mqtt.websocket.entity.QoS;
import org.chevalier.mqtt.websocket.entity.Topic;

public class Test3{
	
	public static void main(String[] args) throws IOException, InterruptedException {

		for(int i = 0; i < 1; i++){
			new Thread(new Runnable() {
				
				@Override
				public void run() {
					
					String clientId = "guest" + UUID.randomUUID();
			    	String userName = "ntguest";
			    	String password = "xiaoneng123";
			    	MQTT mqtt = new MQTT("ws://xxx.xxx.xxx.xxx:xx/mqtt");
			    	mqtt.setClientId(clientId);
			    	mqtt.setUserName(userName);
			    	mqtt.setPassword(password);
			    	mqtt.setWillTopic("S/WILL/" + clientId);
			    	mqtt.setWillMessage("sa_you_na_la");
			    	mqtt.setWillQoS(QoS.AT_MOST_ONCE);
			    	mqtt.setKeepAlive(50);
			    	mqtt.getMqttConnection().addListener(new MQC(mqtt));
			    	mqtt.getMqttConnection().connect(new MQTTCallback() {
						
						@Override
						public void onSuccess() {
							// TODO Auto-generated method stub
						}
						
						@Override
						public void onFailure(Throwable msg) {
							// TODO Auto-generated method stub
							
						}
					});
				}

				class MQC implements MQTTListener{
					
					MQTT mqtt ;
					
					public MQC(MQTT mqtt){
						this.mqtt = mqtt;
					}
				
					@Override
					public void onConnected() {
						
						System.out.println("connecting MQTT Server is success : " + mqtt.getClientId());
						Topic[] topics = new Topic[]{
								new Topic("C/" + mqtt.getClientId(), QoS.AT_MOST_ONCE)
						};
						
						mqtt.getMqttConnection().subscribe(topics, new MQTTCallback() {
								
								@Override
								public void onSuccess() {
									System.out.println("onSuccess : " + mqtt.getClientId());
								}
								
								@Override
								public void onFailure(Throwable msg) {
									System.out.println(msg.getMessage());
									
								}
							}
						);
						mqtt.getMqttConnection().unsubscribe(new String[]{"a"}, new MQTTCallback() {
							
							@Override
							public void onSuccess() {
								System.out.println("onSuccess : " + mqtt.getClientId());
							}
							
							@Override
							public void onFailure(Throwable msg) {
								System.out.println(msg.getMessage());
								
							}
						}
								);
						mqtt.getMqttConnection().publish("C/" + mqtt.getClientId(), 
								"abc啊啊", QoS.EXACTLY_ONCE, false, new MQTTCallback() {
							
							@Override
							public void onSuccess() {
				
//								mqtt.getMqttConnection().disconnect();
								
							}
							
							@Override
							public void onFailure(Throwable msg) {
								// TODO Auto-generated method stub
								
							}
						});
				
					}
				
					@Override
					public void onClose(int arg0, String arg1, boolean arg2) {
						// TODO Auto-generated method stub
						System.out.println("onClose : " + mqtt.getClientId());
						mqtt.getMqttConnection().reconnect(new MQTTCallback() {
							
							@Override
							public void onSuccess() {
								// TODO Auto-generated method stub
								
							}
							
							@Override
							public void onFailure(Throwable msg) {
								// TODO Auto-generated method stub
								
							}
						});
						
					}
				
					@Override
					public void onPublish(String topic, String message) {
						System.out.println(topic);
						System.out.println(message);
					}
				
					@Override
					public void onFailure(Throwable arg0) {
						System.out.println(arg0);
						
					}

					@Override
					public void onDisconnected() {
						// TODO Auto-generated method stub
						
					}
					
				}
			}).start();
		};
    	
	}
	
}
