package net.anyflow.lannister.session;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import io.netty.handler.codec.mqtt.MqttQoS;
import net.anyflow.lannister.TestSuite;
import net.anyflow.lannister.topic.TopicSubscription;

public class SessionTest {

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		TestSuite.setUp();
	}

	@Test
	public void testMatches() throws Exception {
		String testTopic = "testTopic/test";
		Session session = new Session("1", "1", 1, 50, true, null);

		TopicSubscription ts0 = new TopicSubscription("testTopic/#", MqttQoS.AT_MOST_ONCE);
		TopicSubscription ts1 = new TopicSubscription("testTopic/+", MqttQoS.AT_LEAST_ONCE);
		TopicSubscription ts2 = new TopicSubscription(testTopic, MqttQoS.EXACTLY_ONCE);

		session.putTopicSubscription(ts0);
		session.putTopicSubscription(ts1);
		session.putTopicSubscription(ts2);

		Assert.assertEquals(3, session.getTopicSubscriptions().size());

		TopicSubscription target = session.matches(testTopic);

		Assert.assertTrue(target.topicFilter().equals(testTopic));
	}
}
