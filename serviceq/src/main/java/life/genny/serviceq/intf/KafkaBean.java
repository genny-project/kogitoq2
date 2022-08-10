package life.genny.serviceq.intf;

import org.jboss.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.json.JsonObject;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;

import io.smallrye.reactive.messaging.kafka.OutgoingKafkaRecordMetadata;
import org.eclipse.microprofile.reactive.messaging.Message;

import life.genny.qwandaq.data.BridgeSwitch;
import life.genny.qwandaq.exception.runtime.DebugException;
import life.genny.qwandaq.exception.runtime.NullParameterException;
import life.genny.qwandaq.intf.KafkaInterface;
import life.genny.qwandaq.models.UserToken;
import life.genny.qwandaq.exception.runtime.DebugException;
import life.genny.qwandaq.exception.runtime.NullParameterException;
import life.genny.serviceq.live.data.InternalProducer;

@ApplicationScoped
public class KafkaBean implements KafkaInterface {

	@Inject 
	InternalProducer producer;

	@Inject 
	UserToken userToken;

	static final Logger log = Logger.getLogger(KafkaBean.class);

	static Jsonb jsonb = JsonbBuilder.create();

	/**
	* Write a string payload to a kafka channel.
	*
	* @param channel
	* @param payload
	 */
	public void write(String channel, String payload) { 

		if (channel == null)
			throw new NullParameterException("channel");
		if (payload == null)
			throw new NullParameterException("payload");

		// find GennyToken from payload contents
		JsonObject json = jsonb.fromJson(payload, JsonObject.class);
		if (json == null)
			throw new DebugException("Outgoing message could not be deserialized to json");
		if (!json.containsKey("token"))
			throw new DebugException("Outgoing message must have a token");

		// create metadata for correct bridge if outgoing
		OutgoingKafkaRecordMetadata<String> metadata = OutgoingKafkaRecordMetadata.<String>builder()
					.build();

		if ("webcmds".equals(channel) || "webdata".equals(channel)) {

			String bridgeId = BridgeSwitch.get(userToken);

			if (bridgeId == null) {
				log.warn("No Bridge ID found for " + userToken.getUserCode() + " : " + userToken.getJTI());
				bridgeId = BridgeSwitch.findActiveBridgeId(userToken);
			}

			if (bridgeId != null) {
				log.debug("Sending to " + bridgeId);

				metadata = OutgoingKafkaRecordMetadata.<String>builder()
					.withTopic(bridgeId + "-" + channel)
					.build();
			} else {
				log.error("No alternative Bridge ID found!");
			}
		}

		// channel switch
		switch (channel) {
			case "events":
				producer.getToEvents().send(payload);
				break;
			case "valid_events":
				producer.getToValidEvents().send(payload);
				break;
			case "genny_events":
				producer.getToGennyEvents().send(payload);
				break;
			case "genny_data":
				producer.getToGennyData().send(payload);
				break;
			case "search_events":
				producer.getToSearchEvents().send(payload);
				break;
			case "data":
				producer.getToData().send(payload);
				break;
			case "valid_data":
				producer.getToValidData().send(payload);
				break;
			case "search_data":
				producer.getToSearchData().send(payload);
				break;
			case "messages":
				producer.getToMessages().send(payload);
				break;
			case "schedule":
				producer.getToSchedule().send(payload);
				break;
			case "blacklist":
				producer.getToBlacklist().send(payload);
				break;
			case "webcmds":
				producer.getToWebCmds().send(Message.of(payload).addMetadata(metadata));
				break;
			case "webdata":
				producer.getToWebData().send(Message.of(payload).addMetadata(metadata));
				break;
			default:
				log.error("Producer unable to write to channel " + channel);
				break;
		}
	}
}
