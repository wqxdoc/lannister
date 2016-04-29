package net.anyflow.lannister.topic;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.hazelcast.core.IMap;

import io.netty.handler.codec.mqtt.MqttQoS;
import net.anyflow.lannister.Jsonizable;
import net.anyflow.lannister.Repository;
import net.anyflow.lannister.message.Message;
import net.anyflow.lannister.message.MessageStatus;
import net.anyflow.lannister.message.ReceivedMessageStatus;
import net.anyflow.lannister.message.ReceiverTargetStatus;
import net.anyflow.lannister.session.Session;

public class Topic extends Jsonizable implements java.io.Serializable {

	@SuppressWarnings("unused")
	private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Topic.class);

	private static final long serialVersionUID = -3335949846595801533L;

	public static Topics NEXUS = new Topics();

	@JsonProperty
	private String name;
	@JsonProperty
	private Message retainedMessage;
	@JsonProperty
	private IMap<String, TopicSubscriber> subscribers; // clientIds
	@JsonProperty
	private IMap<String, Message> messages; // KEY:Message.key()
	@JsonProperty
	private IMap<String, ReceivedMessageStatus> messageStatus; // KEY:clientId_messageId

	public Topic(String name) {
		this.name = name;
		this.retainedMessage = null;

		this.subscribers = Repository.SELF.generator().getMap("TOPIC(" + name + ")_subscribers");
		this.messages = Repository.SELF.generator().getMap("TOPIC(" + name + ")_messages");
		this.messageStatus = Repository.SELF.generator().getMap("TOPIC(" + name + ")_receivedMessageStatuses");
	}

	public String name() {
		return name;
	}

	public Message retainedMessage() {
		return retainedMessage;
	}

	public void setRetainedMessage(Message message) {
		this.retainedMessage = message;
		NEXUS.put(this);
	}

	public ImmutableMap<String, TopicSubscriber> subscribers() {
		return ImmutableMap.copyOf(subscribers);
	}

	public IMap<String, Message> messages() {
		return messages;
	}

	public void removeReceivedMessageStatus(String clientId, int messageId) {
		messageStatus.remove(MessageStatus.key(clientId, messageId));
	}

	public void setReceivedMessageStatus(String clientId, int messageId, ReceiverTargetStatus targetStatus) {
		ReceivedMessageStatus status = messageStatus.get(MessageStatus.key(clientId, messageId));
		if (status == null) {
			status = new ReceivedMessageStatus(clientId, messageId);
		}
		status.targetStatus(targetStatus);

		messageStatus.put(status.key(), status);
	}

	public void addSubscriber(String clientId) {
		subscribers.put(clientId, new TopicSubscriber(clientId, name));
	}

	public void removeSubscriber(String clientId, boolean persist) {
		subscribers.remove(clientId);

		// TODO should be this topic remained in spite of no subscriber?

		if (persist) {
			NEXUS.put(this);
		}
	}

	public void publish(String clientId, Message message) {
		if (message.qos() != MqttQoS.AT_MOST_ONCE) {
			messages.put(message.key(), message);

			setReceivedMessageStatus(clientId, message.id(),
					message.qos() == MqttQoS.AT_LEAST_ONCE ? ReceiverTargetStatus.TO_ACK : ReceiverTargetStatus.TO_REC);
		}

		subscribers.keySet().stream().parallel().forEach(id -> {
			Session session = Session.NEXUS.lives().values().stream().filter(s -> id.equals(s.channelId())).findFirst()
					.orElse(null);

			if (session != null) {
				session.onPublish(this, message);
			}
			else {
				NEXUS.notifier().publish(new Notification(id, this, message));
			}
		});
	}

	public static boolean isValid(String topicName) {
		// TODO topic name validation
		return Strings.isNullOrEmpty(topicName) == false;
	}

	public static Topic put(Topic topic) {
		Session.NEXUS.topicAdded(topic);

		// TODO should be added in case of no subscriber & no retained Message?
		return NEXUS.put(topic);
	}
}