package org.chevalier.mqtt.websocket;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.NotYetConnectedException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.chevalier.mqtt.websocket.entity.QoS;
import org.chevalier.mqtt.websocket.entity.Topic;
import org.chevalier.mqtt.websocket.util.UTF8Byte;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft;
import org.java_websocket.drafts.Draft_17;
import org.java_websocket.handshake.ServerHandshake;

/**
 * 
 * @author Chevalier
 * @date 2016年3月29日
 *
 */
public final class MQTTConnection{
	
	private MQTT mqtt;
	private IWebSocketClient iWebSocketClient;
	private MQTTListener mqttListener;
	private BlockingQueue<MQTTCallback> conQueue = new LinkedBlockingQueue<MQTTCallback>(1);
	private BlockingQueue<MQTTCallback> pingQueue = new LinkedBlockingQueue<MQTTCallback>(2);
	private Map<Byte, MQTTCallback> callBacks = new ConcurrentHashMap<Byte, MQTTCallback>();
	private Map<Integer, String[]> messages = new ConcurrentHashMap<Integer, String[]>();
	private boolean disconnected = true;
	private ScheduledExecutorService tasks = Executors.newScheduledThreadPool(1);
	//对于消息延迟的等待时间
	private long waitTime = 5000;
	
	//存放packetid,作用于要求消息唯一性的协议，保证其发送消息的唯一性
	private final static BlockingQueue<Byte> PACKET_QUEUE;
	private final static Map<String, String> MQTT_HEADER;
	
	static{
		PACKET_QUEUE = new LinkedBlockingQueue<Byte>(Byte.MAX_VALUE);
		for(byte i = 1; i < Byte.MAX_VALUE; i++){
			PACKET_QUEUE.add(i);
		}
		
		MQTT_HEADER = new HashMap<String, String>(1);
		MQTT_HEADER.put("Sec-WebSocket-Protocol", "mqtt");
	}
	
	//MQTT报文类型
	private static class Method{
		public final static byte CONNECT = 16;		// 1 << 4
		public final static byte CONNACK = 32; 		// 1 << 1 << 4
		public final static byte PUBLISH = 48; 		// (1 << 1) + 1 << 4
		public final static byte PUBACK = 64;		// (1 << 2) << 4  
		public final static byte PUBREC = 80;		// (1 << 2) + 1 << 4
		public final static byte PUBREL = 98;		// ((1 << 2) + (1 << 1) << 4) + (1 << 1) 
		public final static byte SUBSCRIBE = -126;	
		public final static byte SUBACK  = -112;
		public final static byte UNSUBSCRIBE = -94;
		public final static byte UNSUBACK  = -80;
		public final static byte PINGREQ  = -64;
		public final static byte PINGRESP  = -48;
		public final static byte DISCONNECT = -32; 
	}
	
	protected MQTTConnection(MQTT mqtt){

		iWebSocketClient = new IWebSocketClient(URI.create(mqtt.getUrl()), new Draft_17(), MQTT_HEADER);
		this.mqtt = mqtt;
	}
	
	public void addListener(MQTTListener mqttListener){
		this.mqttListener = mqttListener;
	}

	/**
	 * 与MQTT建立物理连接
	 * @param cb
	 */
	public void connect(MQTTCallback cb){
		
		try {
			
			if(disconnected == false){
				cb.onFailure(new Throwable("MQTT已连接"));
				return;
			}
			
			if(conQueue.offer(cb, waitTime, TimeUnit.MILLISECONDS)){
				if(iWebSocketClient.connectBlocking() == false){
					conQueue.poll();
					if(cb != null){
						cb.onFailure(new Throwable("连接失败"));
					}
				}
			}else{
				cb.onFailure(new Throwable("等待连接超时"));
			}
		} catch (InterruptedException e) {
			if(cb != null){
				disconnected = true;
				cb.onFailure(e);
			}
		}

	}
	
	/**
	 * 重新与MQTT建立物理连接
	 * @param cb 回调对象
	 */
	public void reconnect(MQTTCallback cb){
		
		if(tasks.isShutdown()){
			tasks = Executors.newScheduledThreadPool(1);
		}
		
		if(iWebSocketClient == null){
			iWebSocketClient = new IWebSocketClient(URI.create(mqtt.getUrl()), new Draft_17(), MQTT_HEADER);
		}
		connect(cb);

	}
	
	/**
	 * 向mqtt发送Connect协议请求,建立逻辑连接
	 * @throws NotYetConnectedException
	 * @throws UnsupportedEncodingException
	 */
	private void login(MQTTCallback cb){
		
		IMQTTCallback callback = new IMQTTCallback(cb);
		
		try {
			iWebSocketClient.send(UTF8Byte.merge(Method.CONNECT, 
	    			getVarHeader(Method.CONNECT), 
					UTF8Byte.getByte(mqtt.getClientId()),
					UTF8Byte.getByte(mqtt.getWillTopic()),
					UTF8Byte.getByte(mqtt.getWillMessage()),
					UTF8Byte.getByte(mqtt.getUserName()),
					UTF8Byte.getByte(mqtt.getPassword())
					));
			callback.onSuccess();
		} catch (InterruptedException | NotYetConnectedException | UnsupportedEncodingException e) {
			callback.onFailure(e);
		}
		
	}
	
	/**
	 * 向MQTT发送消息
	 * @param topic 主题 
	 * @param message 消息
	 * @param qos 服务质量
	 * @param retain 目前没用
	 * @param cb 回调对象
	 */
	public void publish(String topic, String message, int qos, boolean retain, MQTTCallback cb){
		
		IMQTTCallback callback = new IMQTTCallback(cb);
		
		if(disconnected){
			callback.onFailure(new Throwable("disconnected"));
		}
		
		byte fixHeader = Method.PUBLISH;
		
		try {
			switch (qos) {
				case QoS.EXACTLY_ONCE:
					fixHeader += 2;
				case QoS.AT_LEAST_ONCE:
					fixHeader += 2;
				byte packetId;
				packetId = PACKET_QUEUE.poll(waitTime, TimeUnit.MILLISECONDS);
				this.callBacks.put(packetId, new IMQTTCallback(cb));
				iWebSocketClient.send(
						UTF8Byte.merge(fixHeader, 
								UTF8Byte.getByte(topic),
								new byte[]{0, packetId},
								message.getBytes()
								)
						);
				break;
				case QoS.AT_MOST_ONCE:
					iWebSocketClient.send(
							UTF8Byte.merge(fixHeader, 
									UTF8Byte.getByte(topic),
									message.getBytes()
									)
							);
					callback.onSuccess();
					break;
				default:
					return;
			}
		} catch (InterruptedException | NotYetConnectedException | UnsupportedEncodingException e) {
			callback.onFailure(e);
		}
				
		
	}
	
	/**
	 * 发布确认，用于QOS等级1
	 * @param packet 报文标识符
	 */
	private void puback(byte[] packet){
		
		IMQTTCallback callback = new IMQTTCallback();
		
		if(disconnected){
			callback.onFailure(new Throwable("disconnected"));
		}

		iWebSocketClient.send(UTF8Byte.merge(Method.PUBACK, 
				packet));
		
	}
	
	/**
	 * 发布确认，用于QOS等级2
	 * @param packet 报文标识符
	 */
	private void pubrec(byte[] packet){
		
		IMQTTCallback callback = new IMQTTCallback();
		
		if(disconnected){
			callback.onFailure(new Throwable("disconnected"));
		}
		
		iWebSocketClient.send(UTF8Byte.merge(Method.PUBREC, 
				packet));
		
	}
	
	/**
	 * 发布释放，用于QOS等级2，用于确认接收到发布确认的消息
	 * @param packet
	 */
	private void pubrel(byte[] packet){
		
		IMQTTCallback callback = new IMQTTCallback();
		
		if(disconnected){
			callback.onFailure(new Throwable("disconnected"));
		}
		
		iWebSocketClient.send(UTF8Byte.merge(Method.PUBREL, 
				packet));
		
	}
	
	/**
	 * 订阅主题
	 * @param topics 需要订阅的主题集合
	 * @param cb 回调对象
	 */
	public void subscribe(Topic[] topics, MQTTCallback cb){

		IMQTTCallback callback = new IMQTTCallback(cb);
		
		if(topics == null || topics.length <= 0){
			callback.onFailure(new Throwable("the arrays must not be null or empty"));
		}
		
		if(disconnected){
			callback.onFailure(new Throwable("disconnected"));
		}
		
		byte[][] data = new byte[topics.length * 2 + 1][];
		int index = 0;
		
		try {
			data[index++] = getVarHeader(Method.SUBSCRIBE);
			
			for(int i = 0; i < topics.length; i++){

				data[index++] = UTF8Byte.getByte(topics[i].getName());
				data[index++] = new byte[]{(byte) topics[i].getQos()};
				
			}
			
			this.callBacks.put(data[0][1], callback);
			
	    	iWebSocketClient.send(UTF8Byte.mergeArray(
	    			Method.SUBSCRIBE, 
	    			data));
		} catch (InterruptedException | UnsupportedEncodingException e) {
			callback.onFailure(e);
		}
		
    	
	}
	
	/**
	 * 取消订阅
	 * @param topics 需要订阅的主题名集合
	 * @param cb 回调对象
	 */
	public void unsubscribe(String[] topics, MQTTCallback cb){

		IMQTTCallback callback = new IMQTTCallback(cb);
		
		if(topics == null || topics.length <= 0){
			callback.onFailure(new Throwable("the arrays must not be null or empty"));
		}
		
		if(disconnected){
			callback.onFailure(new Throwable("disconnected"));
		}
		
		byte[][] data = new byte[topics.length + 1][];
		int index = 0;
		
		try {
			data[index++] = getVarHeader(Method.UNSUBSCRIBE);
			
			for(int i = 0; i < topics.length; i++){
				data[index++] = UTF8Byte.getByte(topics[i]);
			}

			this.callBacks.put(data[0][1], callback);
			
	    	iWebSocketClient.send(UTF8Byte.mergeArray(
	    			Method.UNSUBSCRIBE, 
	    			data));
		} catch (InterruptedException | UnsupportedEncodingException e) {
			callback.onFailure(e);
		}
    	
	}
	
	/**
	 * 心跳请求
	 */
	private void pingreq(){
		
		final MQTTCallback callback = new MQTTCallback() {
			
			@Override
			public void onSuccess() {
			}
			
			@Override
			public void onFailure(Throwable msg) {
				System.err.println(msg.getMessage());
				disconnect();
			}
		};
		
		if(!disconnected){
			tasks.scheduleAtFixedRate(new Runnable() {
				
				@Override
				public void run() {
					try {
						if(pingQueue.offer(callback, waitTime, TimeUnit.MILLISECONDS)){
							iWebSocketClient.send(new byte[]{Method.PINGREQ, 0});
						}else{
							callback.onFailure(new Throwable("与服务器连接超时"));
						}
					} catch (InterruptedException e) {
						callback.onFailure(e);
					}
					
				}
			}, mqtt.getKeepAlive(), mqtt.getKeepAlive(), TimeUnit.SECONDS);
		}
		
		
	}
	
	/**
	 * 断开连接
	 */
	public void disconnect(){

    	disconnected = true;
    	//断开逻辑连接
    	iWebSocketClient.send(new byte[]{Method.DISCONNECT, 0});
    	if(tasks.isShutdown() == false){
    		tasks.shutdown();
    	}
    	
    	//延迟断开物理连接，等待所有消息流读取和发送完成
    	new Thread(new Runnable() {
			
			@Override
			public void run() {

				if(iWebSocketClient.isConnected == false){

					long time = 0;
					
					while(true){
						
						if(iWebSocketClient.getConnection().hasBufferedData() == false 
								|| time > 10000){
							try {
								Thread.sleep(100);
								time += 100;
								iWebSocketClient.close();
							} catch (InterruptedException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
							break;
						}
						
						try {
							Thread.sleep(100);
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						
					}
				}

		    	conQueue.clear();
		    	callBacks.clear();
		    	messages.clear();
		    	pingQueue.clear();
				iWebSocketClient = null;
			}
		}).start();
    	
	}
	
	/**
	 * 获取可变报头
	 * @param method
	 * @return
	 * @throws InterruptedException
	 */
	private byte[] getVarHeader(byte method) throws InterruptedException{
		
		byte[] varHeader = null;
		byte packetId = 0;
		
		switch(method){
			case Method.CONNECT:
				varHeader = new byte[]{0, 4, 77, 81, 84, 84, 4, 0, 0, 0};
				
				varHeader[7] += mqtt.getUserName() != null ? 128 : 0;
				varHeader[7] += mqtt.getPassword() != null ? 64 : 0;
				varHeader[7] += mqtt.isCleanSession() ? 2 : 0;
				
				switch(mqtt.getWillQoS()){
					case QoS.EXACTLY_ONCE:
						varHeader[7] += 8;
					case QoS.AT_LEAST_ONCE:
						varHeader[7] += 8;
					case QoS.AT_MOST_ONCE:
						varHeader[7] += 4;
						break;
					default:
						break;
				}
				
				varHeader[8] = (byte) (mqtt.getKeepAlive() / 256);
				varHeader[9] = (byte) (mqtt.getKeepAlive() % 256);
				
				break;
			case Method.SUBSCRIBE:
				packetId = PACKET_QUEUE.poll(waitTime, TimeUnit.MILLISECONDS);
				varHeader = new byte[]{0, packetId};
				break;
			case Method.PUBLISH:
				break;
			case Method.UNSUBSCRIBE:
				packetId = PACKET_QUEUE.poll(waitTime, TimeUnit.MILLISECONDS);
				varHeader = new byte[]{0, packetId};
				break;
		}
		
		return varHeader;
		
	}
	
	/**
	 * 对接收到的发布消息进行解析
	 * @param msg
	 * @return
	 */
	private String[] getMqttInfo(byte[] msg){
		
		String[] data = null;
		
		int capacity = msg[1]; //Get Remaining Length
		int index = 2;
		byte[] packet = null;
	
		if(capacity > 127){
			capacity = 127 + (255 - 127) * (msg[2] - 1) + (capacity - 127);
			index = 3;
		}
		
		if(capacity != msg.length - index){
			return data;
		}
		
		int topicLength = msg[index] * 256 + msg[index + 1];
		
		try {
			String topic = new String(
					Arrays.copyOfRange(msg, index + 2, topicLength + index + 2), "utf-8");
			
			int messageIndex = index + 2 + topicLength;
			
			if(msg[0] != MQTTConnection.Method.PUBLISH){
				packet = Arrays.copyOfRange(msg, messageIndex, messageIndex + 2);
				messageIndex += 2;
			}
			
			String message = new String(
					Arrays.copyOfRange(msg, messageIndex, msg.length), "utf-8");
			
			data = new String[]{topic, message};
			
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			if(data != null){
				switch (msg[0]) {
					case Method.PUBLISH + 2:
					case Method.PUBLISH + 2 + 8:
						puback(packet);
						break;
					case Method.PUBLISH + 4:
					case Method.PUBLISH + 4 + 8:
						synchronized (this) {
							int pk = packet[0] * 256 + packet[1];
							if(this.messages.containsKey(pk)){
								pubrec(packet);
								this.messages.put(pk, data);
							}
						}
						break;

				}
			}
		}
		
		return data;
	}
	
	/**
	 * websocket操控类
	 * @author Chevalier
	 * @date 2016年3月29日
	 *
	 */
	class IWebSocketClient extends WebSocketClient{

		public boolean isConnected = false;
		public MQTTConnection con = MQTTConnection.this;
		
		public IWebSocketClient(URI serverUri, Draft draft, Map<String, String> headers) {
			super(serverUri, draft, headers);
		}
		
		@Override
		public void onOpen(ServerHandshake arg0) {
			disconnected = true;
			con.login(con.conQueue.poll());
		}
		
		@Override
		public void onMessage(String arg0) {
		}
		
		@Override
		public void onMessage(ByteBuffer bytes) {
			
			if(bytes != null && bytes.hasArray()){
				
				byte[] msg = bytes.array();
				MQTTCallback cb = null;
				String[] data;
				
				switch (msg[0]) {
					case MQTTConnection.Method.CONNACK:
						if(Arrays.equals(msg, new byte[]{MQTTConnection.Method.CONNACK, 2, 0, 0})){
							con.disconnected = false;
							mqttListener.onConnected();
							con.pingreq();
						}
						break;
						
					case MQTTConnection.Method.PUBLISH:
					case MQTTConnection.Method.PUBLISH + 2:
					case MQTTConnection.Method.PUBLISH + 2 + 8:
						
						data = con.getMqttInfo(msg);
					
						if(data != null){
							mqttListener.onPublish(data[0], data[1]);
						}
						
					
					break;
					
					case MQTTConnection.Method.PUBLISH + 4:
					case MQTTConnection.Method.PUBLISH + 4 + 8:
						con.getMqttInfo(msg);
						break;
					case MQTTConnection.Method.PUBACK:
						
						cb = con.callBacks.remove(msg[3]);
						
						if(cb == null){
							return;
						}
						
						MQTTConnection.PACKET_QUEUE.add(msg[3]);
						
						if(Arrays.equals(msg, 
								new byte[]{MQTTConnection.Method.PUBACK, 2, 0, msg[3]})){
							cb.onSuccess();
						}else{
							cb.onFailure(new Throwable("MQTT 数据返回值非法"));
						}
						
						break;
						
					case MQTTConnection.Method.PUBREC:

						cb = con.callBacks.remove(msg[3]);
						
						if(cb == null){
							return;
						}

						MQTTConnection.PACKET_QUEUE.add(msg[3]);
						
						if(Arrays.equals(msg, 
								new byte[]{MQTTConnection.Method.PUBREC, 2, 0, msg[3]})){
							con.pubrel(new byte[]{0, msg[3]});
							cb.onSuccess();
						}else{
							cb.onFailure(new Throwable("MQTT 数据返回非法"));
						}
						
						break;
						
					case MQTTConnection.Method.PUBREL:
						
						int packet = msg[2] * 256 + msg[3];
						data = con.messages.remove(packet);
						
						if(data != null){
							mqttListener.onPublish(data[0], data[1]);
							return;
						}
						
						break;
						
					case MQTTConnection.Method.SUBACK://待优化
						
						cb = con.callBacks.remove(msg[3]);
						
						if(cb == null){
							return;
						}
						
						MQTTConnection.PACKET_QUEUE.add(msg[3]);
						
						StringBuffer sb = new StringBuffer();
						
						for(int i = 4; i < msg.length; i++){
							switch (msg[i]) {
								case QoS.EXACTLY_ONCE:
								case QoS.AT_LEAST_ONCE:
								case QoS.AT_MOST_ONCE:
									break;
								case -128:
									sb.append(i - 3 + ",");
									break;
								default:
									break;
							}
						}
						
						if(sb.length() == 0){
							cb.onSuccess();
						}else{
							sb.setLength(sb.length() - 1);
							sb.append("主题订阅失败");
							cb.onFailure(new Throwable(sb.toString()));
						}
						
						break;
						
					case MQTTConnection.Method.UNSUBACK:
						
						cb = con.callBacks.remove(msg[3]);
						
						if(cb == null){
							return;
						}
						
						MQTTConnection.PACKET_QUEUE.add(msg[3]);
						
						if(Arrays.equals(msg, new byte[]{MQTTConnection.Method.UNSUBACK, 2, 0, msg[3]})){
							cb.onSuccess();
						}else{
							cb.onFailure(null);
						}
						break;
						
					case MQTTConnection.Method.PINGRESP:
						
						cb = con.pingQueue.poll();
						
						if(cb == null){
							return;
						}
						
						if(Arrays.equals(msg, new byte[]{MQTTConnection.Method.PINGRESP, 0})){
							cb.onSuccess();
						}else{
							cb.onFailure(null);
						}
						break;
					default:
						break;
				}
			}

		}
		
		@Override
		public void onError(Exception arg0) {
			// TODO Auto-generated method stub
			mqttListener.onFailure(arg0);
		}
		
		@Override
		public void onClose(int arg0, String arg1, boolean arg2) {
			
			if(disconnected == false){ //如果websocket是非正常关闭
				disconnect();
			}
			
			mqttListener.onClose(arg0, arg1, arg2);
			mqttListener.onDisconnected();
		}
	}
	
	class IMQTTCallback implements MQTTCallback{

		private MQTTCallback cb;
		
		public IMQTTCallback() {}

		public IMQTTCallback(MQTTCallback cb) {
			this.cb = cb;
		}

		@Override
		public void onSuccess() {
			if(cb != null){
				cb.onSuccess();
			}
		}

		@Override
		public void onFailure(Throwable msg) {
			if(cb != null){
				cb.onFailure(msg);
			}
		}
		
	}
	
}
