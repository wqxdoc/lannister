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

package net.anyflow.lannister.topic;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.Sets;
import com.hazelcast.core.ITopic;

import net.anyflow.lannister.cluster.ClusterDataFactory;
import net.anyflow.lannister.cluster.Map;
import net.anyflow.lannister.message.InboundMessageStatus;
import net.anyflow.lannister.message.Message;
import net.anyflow.lannister.message.OutboundMessageStatus;
import net.anyflow.lannister.session.Sessions;

public class Topics {
	@SuppressWarnings("unused")
	private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Topics.class);

	private final Map<String, Topic> topics;
	private final ITopic<Notification> notifier;

	protected Topics(Sessions sessions) {
		this.topics = ClusterDataFactory.INSTANCE.createMap("topics");
		this.notifier = ClusterDataFactory.INSTANCE.createTopic("publishNotifier");
		this.notifier.addMessageListener(sessions);
	}

	public Set<String> keySet() {
		return Sets.newHashSet(topics.keySet());
	}

	public ITopic<Notification> notifier() {
		return notifier;
	}

	public Topic get(String name) {
		return topics.get(name);
	}

	public enum ClientType {
		SUBSCRIBER,
		PUBLISHER
	}

	public Topic get(String clientId, int messageId, ClientType clientType) {
		switch (clientType) {
		case SUBSCRIBER:
			return getFromSubscriber(clientId, messageId);

		case PUBLISHER:
			return getFromPublisher(clientId, messageId);

		default:
			throw new IllegalArgumentException();
		}
	}

	private Topic getFromPublisher(String publisherId, int messageId) {
		InboundMessageStatus status = InboundMessageStatus.NEXUS.getBy(messageId, publisherId);
		if (status == null) { return null; }

		return Topic.NEXUS.get(status.topicName());
	}

	private Topic getFromSubscriber(String subscriberId, int messageId) {
		OutboundMessageStatus status = OutboundMessageStatus.NEXUS.getBy(messageId, subscriberId);
		if (status == null) { return null; }

		return Topic.NEXUS.get(status.topicName());
	}

	protected void persist(Topic topic) {
		assert topic != null;

		topics.put(topic.name(), topic);
	}

	public void insert(Topic topic) {
		assert topic != null;

		TopicSubscriber.NEXUS.updateByTopicName(topic.name());

		// TODO should be added in case of no subscriber & no retained Message?
		persist(topic);
	}

	public Topic remove(Topic topic) {
		assert topic != null;

		return topics.remove(topic.name());
	}

	public Topic prepare(Message message) {
		Topic topic = get(message.topicName());
		if (topic == null) {
			topic = new Topic(message.topicName());
			insert(topic);
		}

		return topic;
	}

	public List<Topic> matches(String topicFilter) {
		return topics.keySet().stream().filter(topicName -> TopicMatcher.match(topicFilter, topicName))
				.map(topicName -> topics.get(topicName)).collect(Collectors.toList());
	}

	public List<Topic> matches(Collection<String> topicFilters) {
		return topics.keySet().stream()
				.filter(topicName -> topicFilters.stream().filter(tf -> TopicMatcher.match(tf, topicName)).count() > 0)
				.map(topicName -> topics.get(topicName)).collect(Collectors.toList());
	}
}