/*
 * Copyright 2016 The Lannister Project
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.anyflow.lannister.packetreceiver;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import io.netty.channel.ChannelId;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.mqtt.MqttConnAckMessage;
import io.netty.handler.codec.mqtt.MqttConnectMessage;
import io.netty.handler.codec.mqtt.MqttConnectPayload;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttConnectVariableHeader;
import io.netty.handler.codec.mqtt.MqttFixedHeader;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttQoS;
import net.anyflow.lannister.Settings;
import net.anyflow.lannister.TestSuite;
import net.anyflow.lannister.TestUtil;
import net.anyflow.lannister.client.MqttClient;
import net.anyflow.lannister.message.ConnectOptions;
import net.anyflow.lannister.plugin.Authenticator;
import net.anyflow.lannister.plugin.Authorizer;
import net.anyflow.lannister.plugin.Plugin;
import net.anyflow.lannister.plugin.Plugins;
import net.anyflow.lannister.plugin.ServiceChecker;

//TODO invalid version(e.g. 2) mqtt connect, server seems process nothing
public class ConnectReceiverTest {
	@SuppressWarnings("unused")
	private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ConnectReceiverTest.class);

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		TestSuite.setUp();
	}

	@Test
	public void testNonCleanSessionWithoutClientId() throws Exception { // [MQTT-3.1.3-3]
		ConnectOptions options = new ConnectOptions();
		options.cleanSession(false);

		MqttClient client = new MqttClient("mqtt://localhost:" + Settings.INSTANCE.mqttPort());
		MqttConnectReturnCode ret = client.connectOptions(options).receiver(null).connect();

		Assert.assertEquals(MqttConnectReturnCode.CONNECTION_REFUSED_IDENTIFIER_REJECTED, ret);
	}

	@Test
	public void testDefaultChannelRead0() throws Exception {
		MqttConnAckMessage ret = executeNormalChannelRead0(TestUtil.newClientId(), true, null);

		Assert.assertEquals(ret.variableHeader().connectReturnCode(), MqttConnectReturnCode.CONNECTION_ACCEPTED);
	}

	@Test
	public void testCleanSessionWithoutClientIdReturnTrue() throws Exception {
		MqttConnAckMessage ret = executeNormalChannelRead0("", true, null);

		Assert.assertEquals(MqttConnectReturnCode.CONNECTION_ACCEPTED, ret.variableHeader().connectReturnCode());
	}

	@Test
	public void testCleanSessionWithoutClientIdReturnFalse() throws Exception {
		Settings.INSTANCE.setProperty("mqttserver.acceptEmptyClientId", "false");
		MqttConnAckMessage ret = executeNormalChannelRead0("", true, null);

		Assert.assertEquals(MqttConnectReturnCode.CONNECTION_REFUSED_IDENTIFIER_REJECTED,
				ret.variableHeader().connectReturnCode());
		Settings.INSTANCE.setProperty("mqttserver.acceptEmptyClientId", "true");
	}

	@Test
	public void testCONNECTION_REFUSED_SERVER_UNAVAILABLE() throws Exception {
		ServiceChecker prev = Plugins.INSTANCE.put(ServiceChecker.class, new ServiceChecker() {
			@Override
			public Plugin clone() {
				return this;
			}

			@Override
			public boolean isServiceAvailable() {
				return false;
			}
		});

		MqttConnAckMessage ret = executeNormalChannelRead0(TestUtil.newClientId(), true, null);

		Assert.assertEquals(ret.variableHeader().connectReturnCode(),
				MqttConnectReturnCode.CONNECTION_REFUSED_SERVER_UNAVAILABLE);

		Plugins.INSTANCE.put(ServiceChecker.class, prev);
	}

	@Test
	public void testCONNECTION_REFUSED_IDENTIFIER_REJECTED() throws Exception {
		Authenticator prev = Plugins.INSTANCE.put(Authenticator.class, new Authenticator() {
			@Override
			public Plugin clone() {
				return this;
			}

			@Override
			public boolean isValid(String clientId) {
				return false;
			}

			@Override
			public boolean isValid(String clientId, String userName, String password) {
				return true;
			}
		});

		MqttConnAckMessage ret = executeNormalChannelRead0(TestUtil.newClientId(), true, null);

		Assert.assertEquals(ret.variableHeader().connectReturnCode(),
				MqttConnectReturnCode.CONNECTION_REFUSED_IDENTIFIER_REJECTED);

		Plugins.INSTANCE.put(Authenticator.class, prev);
	}

	@Test
	public void testCONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD() throws Exception {
		Authenticator prev = Plugins.INSTANCE.put(Authenticator.class, new Authenticator() {
			@Override
			public Plugin clone() {
				return this;
			}

			@Override
			public boolean isValid(String clientId) {
				return true;
			}

			@Override
			public boolean isValid(String clientId, String userName, String password) {
				return false;
			}
		});

		MqttConnAckMessage ret = executeNormalChannelRead0(TestUtil.newClientId(), true, null);

		Assert.assertEquals(ret.variableHeader().connectReturnCode(),
				MqttConnectReturnCode.CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD);

		Plugins.INSTANCE.put(Authenticator.class, prev);
	}

	@Test
	public void testCONNECTION_REFUSED_NOT_AUTHORIZED() throws Exception {
		Authorizer prev = Plugins.INSTANCE.put(Authorizer.class, new Authorizer() {
			@Override
			public Plugin clone() {
				return this;
			}

			@Override
			public boolean isAuthorized(String clientId, String username) {
				return false;
			}
		});

		MqttConnAckMessage ret = executeNormalChannelRead0(TestUtil.newClientId(), true, null);

		Assert.assertEquals(ret.variableHeader().connectReturnCode(),
				MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED);

		Plugins.INSTANCE.put(Authorizer.class, prev);
	}

	@Test
	public void cleanSessionOnSameClientIdSession() throws Exception {
		String clientId = TestUtil.newClientId();

		executeNormalChannelRead0(clientId, true, null);

		MqttConnAckMessage ret = executeNormalChannelRead0(clientId, true, null);

		Assert.assertEquals(ret.variableHeader().connectReturnCode(), MqttConnectReturnCode.CONNECTION_ACCEPTED);
	}

	@Test
	public void nonCleanSession() throws Exception {
		String clientId = TestUtil.newClientId();

		MqttConnAckMessage ret = executeNormalChannelRead0(clientId, false, null);

		Assert.assertEquals(ret.variableHeader().connectReturnCode(), MqttConnectReturnCode.CONNECTION_ACCEPTED);
	}

	private MqttConnAckMessage executeNormalChannelRead0(String clientId, boolean cleanSession, ChannelId channelId)
			throws Exception {
		MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.CONNECT, false, MqttQoS.AT_MOST_ONCE, false,
				10);
		MqttConnectVariableHeader variableHeader = new MqttConnectVariableHeader("MQTT", 4, true, true, true, 0, true,
				cleanSession, 60);
		MqttConnectPayload payload = new MqttConnectPayload(clientId, "willtopic", "willmessage", "username",
				"password");

		MqttConnectMessage msg = new MqttConnectMessage(fixedHeader, variableHeader, payload);

		ChannelId cid = channelId == null ? TestUtil.newChannelId(clientId, false) : channelId;

		EmbeddedChannel channel = new EmbeddedChannel(cid, new ConnectReceiver());

		channel.writeInbound(msg);

		return channel.readOutbound();
	}
}