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

import java.util.Date;
import java.util.Map;

import com.google.common.collect.Maps;
import com.hazelcast.core.ILock;
import com.hazelcast.core.IMap;

import net.anyflow.lannister.session.Session;
import net.anyflow.lannister.topic.Topic;

public class Statistics {

	public static Statistics SELF;

	static {
		SELF = new Statistics();
	}

	public interface SysValue {
		String value();
	}

	private class RawSysValue implements SysValue {
		private Criterion criterion;

		private RawSysValue(Criterion criterion) {
			this.criterion = criterion;
		}

		@Override
		public String value() {
			return criterions.get(criterion).toString();
		}
	}

	public enum Criterion {
		BROKER_START_TIME,
		BYTE_RECEIVED,
		BYTE_SENT,
		CLIENTS_CONNECTED,
		CLIENTS_DISCONNECTED,
		CLIENTS_MAXIMUM,
		CLIENTS_TOTAL,
		MESSAGES_RECEIVED,
		MESSAGES_SENT,
		MESSAGES_PUBLISH_DROPPED,
		MESSAGES_PUBLISH_RECEIVED,
		MESSAGES_PUBLISH_SENT;
	}

	private Map<String, SysValue> data; // key : topic name
	private IMap<Criterion, Long> criterions;

	private Statistics() {
		this.data = Maps.newHashMap();
		this.criterions = Hazelcast.SELF.generator().getMap("statistics");

		initializeCriterions();
		initializeData();
	}

	public Map<String, SysValue> data() {
		return data;
	}

	public void add(Criterion criterion, long size) {
		ILock lock = Hazelcast.SELF.generator().getLock(criterion.toString());

		lock.lock();
		try {
			Long val = criterions.get(criterion);
			val += size;

			criterions.set(criterion, val);
		}
		finally {
			lock.unlock();
		}
	}

	private void initializeCriterions() {
		if (criterions.get(Criterion.BROKER_START_TIME) == null) {
			criterions.set(Criterion.BROKER_START_TIME, (new Date()).getTime());
		}

		initialize(Criterion.BYTE_RECEIVED);
		initialize(Criterion.BYTE_SENT);
		initialize(Criterion.CLIENTS_CONNECTED);
		initialize(Criterion.CLIENTS_DISCONNECTED);
		initialize(Criterion.CLIENTS_MAXIMUM);
		initialize(Criterion.CLIENTS_TOTAL);
		initialize(Criterion.MESSAGES_RECEIVED);
		initialize(Criterion.MESSAGES_SENT);
		initialize(Criterion.MESSAGES_PUBLISH_DROPPED);
		initialize(Criterion.MESSAGES_PUBLISH_RECEIVED);
		initialize(Criterion.MESSAGES_PUBLISH_SENT);
	}

	private void initialize(Criterion criterion) {
		if (criterions.get(criterion) != null) { return; }

		criterions.put(criterion, 0l);
	}

	private void initializeData() {
		data.put("$SYS/broker/load/bytes/received", new RawSysValue(Criterion.BYTE_RECEIVED));
		data.put("$SYS/broker/load/bytes/sent", new RawSysValue(Criterion.BYTE_SENT));
		data.put("$SYS/broker/clients/connected", new RawSysValue(Criterion.CLIENTS_CONNECTED));
		data.put("$SYS/broker/clients/disconnected", new RawSysValue(Criterion.CLIENTS_DISCONNECTED));
		data.put("$SYS/broker/clients/maximum", new RawSysValue(Criterion.CLIENTS_MAXIMUM));
		data.put("$SYS/broker/clients/total", new RawSysValue(Criterion.CLIENTS_TOTAL));
		data.put("$SYS/broker/messages/received", new RawSysValue(Criterion.MESSAGES_RECEIVED));
		data.put("$SYS/broker/messages/sent", new RawSysValue(Criterion.MESSAGES_SENT));
		data.put("$SYS/broker/messages/publish/dropped", new RawSysValue(Criterion.MESSAGES_PUBLISH_DROPPED));
		data.put("$SYS/broker/messages/publish/received", new RawSysValue(Criterion.MESSAGES_PUBLISH_RECEIVED));
		data.put("$SYS/broker/messages/publish/sent", new RawSysValue(Criterion.MESSAGES_PUBLISH_SENT));

		data.put("$SYS/broker/messages/retained/count", new SysValue() {
			@Override
			public String value() {
				return Long
						.toString(Topic.NEXUS.map().values().stream().filter(t -> t.retainedMessage() != null).count());
			}
		});

		data.put("$SYS/broker/subscriptions/count", new SysValue() {
			@Override
			public String value() {
				return Long.toString(Session.NEXUS.map().values().stream().map(s -> s.topicSubscriptions())
						.flatMap(s -> s.values().stream()).count());
			}
		});

		data.put("$SYS/broker/time", new SysValue() {
			@Override
			public String value() {
				return (new Date()).toString();
			}
		});

		data.put("$SYS/broker/uptime", new SysValue() {
			@Override
			public String value() {
				return Double.toString((double) ((new Date()).getTime() - criterions.get(Criterion.BROKER_START_TIME))
						/ (double) 1000);
			}
		});

		data.put("$SYS/broker/version", new SysValue() {
			@Override
			public String value() {
				return Settings.SELF.getProperty("project.version");
			}
		});
	}
}