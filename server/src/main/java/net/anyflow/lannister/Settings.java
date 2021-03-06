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

package net.anyflow.lannister;

import java.io.File;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;

import io.netty.handler.ssl.util.SelfSignedCertificate;
import net.anyflow.lannister.cluster.Mode;

public class Settings extends java.util.Properties {

	private static final long serialVersionUID = -3232325711649130464L;

	protected static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Settings.class);

	private final static String CONFIG_NAME = "lannister.properties";

	public final static Settings INSTANCE = new Settings();

	private Map<String, String> webResourceExtensionToMimes;
	private transient SelfSignedCertificate ssc;
	private java.util.Properties gitProperties;

	@SuppressWarnings("unchecked")
	private Settings() {
		gitProperties = new java.util.Properties();

		try {
			load(Application.class.getClassLoader().getResourceAsStream(CONFIG_NAME));
			gitProperties.load(Application.class.getClassLoader().getResourceAsStream("git.properties"));

			ssc = new SelfSignedCertificate();
		}
		catch (IOException e) {
			logger.error("Settings instantiation failed.", e);
		}
		catch (CertificateException e) {
			logger.error(e.getMessage(), e);
		}

		try {
			webResourceExtensionToMimes = new ObjectMapper().readValue(getProperty("webserver.MIME"), Map.class);
		}
		catch (IOException e) {
			logger.error(e.getMessage(), e);
		}

	}

	public int getInt(String key, Integer defaultValue) {
		String valueString = this.getProperty(key.trim());

		if (valueString == null) { return defaultValue; }

		try {
			return Integer.parseInt(valueString.trim());
		}
		catch (NumberFormatException e) {
			return defaultValue;
		}
	}

	public boolean getBoolean(String key, boolean defaultValue) {
		String valueString = this.getProperty(key.trim());

		if (valueString == null) { return defaultValue; }

		return "true".equalsIgnoreCase(valueString.trim());
	}

	public Map<String, String> webResourceExtensionToMimes() {
		return webResourceExtensionToMimes;
	}

	public static Integer tryParse(String text) {
		try {
			return Integer.parseInt(text);
		}
		catch (NumberFormatException e) {
			return null;
		}
	}

	public Integer mqttPort() {
		return tryParse(getProperty("mqttserver.tcp.port", null));
	}

	public Integer mqttsPort() {
		return tryParse(getProperty("mqttserver.tcp.ssl.port", null));
	}

	public Integer websocketPort() {
		return tryParse(getProperty("mqttserver.websocket.port", null));
	}

	public Integer websocketSslPort() {
		return tryParse(getProperty("mqttserver.websocket.ssl.port", null));
	}

	public Integer httpPort() {
		return tryParse(getProperty("webserver.http.port", null));
	}

	public Integer httpsPort() {
		return tryParse(getProperty("webserver.https.port", null));
	}

	public File certChainFile() {
		String certFilePath = getProperty("lannister.ssl.certChainFilePath", null);

		return "self".equalsIgnoreCase(certFilePath) ? ssc.certificate() : new File(certFilePath);
	}

	public File privateKeyFile() {
		String privateKeyFilePath = getProperty("lannister.ssl.privateKeyFilePath", null);

		return "self".equalsIgnoreCase(privateKeyFilePath) ? ssc.privateKey() : new File(privateKeyFilePath);
	}

	public List<String> bannedTopicFilters() {
		List<String> ret = Lists.newArrayList();

		String tokens = getProperty("mqttserver.subscribe.banned_topicfilters", "");
		if (Strings.isNullOrEmpty(tokens)) { return ret; }

		return Lists.newArrayList(tokens.split(","));
	}

	/**
	 * @return context root path
	 */
	public String httpContextRoot() {
		String ret = getProperty("webserver.contextRoot", "/");

		if (ret.equalsIgnoreCase("") || ret.charAt(ret.length() - 1) != '/') {
			ret += "/";
		}

		return ret;
	}

	public String nettyTransportMode() {
		if (!getBoolean("netty.nativeTransportMode", true)) { return Literals.NETTY_NIO; }

		return System.getProperty("os.name").startsWith("Linux") ? Literals.NETTY_EPOLL : Literals.NETTY_NIO;
	}

	public Mode clusteringMode() {
		return Mode.from(getProperty("clustering.mode", "hazelcast"));
	}

	public String version() {
		return this.gitProperties.getProperty("git.build.version");
	}

	public String commitId() {
		return this.gitProperties.getProperty("git.commit.id");
	}

	public String commitIdDescribe() {
		return this.gitProperties.getProperty("git.commit.id.describe");
	}

	public String commitMessage() {
		return this.gitProperties.getProperty("git.commit.message.short");
	}

	public String buildTime() {
		return this.gitProperties.getProperty("git.build.time");
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof Settings; // JUST TO REMOVE FINDBUGS ERROR
	}

	@Override
	public int hashCode() {
		return version().hashCode(); // JUST TO REMOVE FINDBUGS ERROR
	}
}
